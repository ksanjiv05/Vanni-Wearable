package com.vaani.data.ai

import android.content.Context
import com.vaani.domain.ai.InstallStatus
import com.vaani.domain.ai.ModelCatalog
import com.vaani.domain.ai.ModelInfo
import com.vaani.domain.ai.ModelRole
import com.vaani.domain.ai.ModelInstallState
import com.vaani.domain.ai.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp-backed [ModelRepository] (ADR-001 §4). Downloads are resumable (HTTP
 * Range from the `.part` length), SHA-256-verified, then — for bundle models —
 * extracted (.tar.bz2) into the model dir. Cached in app-private
 * `filesDir/models/<id>/`. An unpinned model (blank sha256) is reported
 * UNAVAILABLE and never fetched (fail closed).
 *
 * Controls: [download] starts/resumes, [pause] stops but keeps the `.part`,
 * [cancel] stops and discards, [delete] removes installed files. State is a
 * StateFlow map so the picker UI shows live progress / pause / extraction.
 */
@Singleton
class OkHttpModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @ModelDownloads private val client: OkHttpClient,
    private val hfCredentials: com.vaani.domain.ai.HfCredentials,
) : ModelRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val paused = ConcurrentHashMap<String, Boolean>()

    private val states = MutableStateFlow(
        ModelCatalog.all.associate { it.id to initialState(it) },
    )

    override fun observeAll(): Flow<Map<String, ModelInstallState>> = states.asStateFlow()

    override fun observe(modelId: String): Flow<ModelInstallState> =
        states.map { it[modelId] ?: ModelInstallState(modelId, InstallStatus.NOT_INSTALLED) }

    override suspend fun download(modelId: String) {
        val model = ModelCatalog.byId(modelId) ?: return
        // Gated models (e.g. Gemma) have no pre-pinned SHA — they can't be fetched
        // to hash until the user provides a HF token for a license-accepted account.
        // Allow the download when a token exists; integrity is then size-checked.
        if (model.gated) {
            if (hfCredentials.token().isNullOrBlank()) {
                put(modelId, InstallStatus.UNAVAILABLE, detail = "Add a Hugging Face token & accept the license to download.")
                return
            }
        } else if (model.sha256.isBlank()) {
            put(modelId, InstallStatus.UNAVAILABLE, detail = "Coming soon — not yet available to download.")
            return
        }
        paused.remove(modelId)
        if (jobs[modelId]?.isActive == true) return
        // Keep the process alive while downloading so a large fetch survives the
        // user leaving the app. Stopped when the last active download finishes.
        ModelDownloadService.start(context, "Downloading ${model.displayName}…")
        jobs[modelId] = scope.launch {
            try {
                runDownload(model)
            } finally {
                jobs.remove(modelId)
                if (jobs.values.none { it.isActive }) ModelDownloadService.stop(context)
            }
        }
    }

    override fun pause(modelId: String) {
        paused[modelId] = true
        jobs.remove(modelId)?.cancel()
        if (jobs.values.none { it.isActive }) ModelDownloadService.stop(context)
        val model = ModelCatalog.byId(modelId) ?: return
        val pct = partFile(model).let { if (it.exists() && model.downloadBytes > 0) it.length().toFloat() / model.downloadBytes else 0f }
        put(modelId, InstallStatus.PAUSED, progress = pct.coerceIn(0f, 1f), detail = "Paused — tap to resume")
    }

    override fun cancel(modelId: String) {
        paused.remove(modelId)
        jobs.remove(modelId)?.cancel()
        if (jobs.values.none { it.isActive }) ModelDownloadService.stop(context)
        val model = ModelCatalog.byId(modelId) ?: return
        partFile(model).delete()
        put(modelId, if (isInstalled(model)) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED)
    }

    override suspend fun delete(modelId: String) {
        val model = ModelCatalog.byId(modelId) ?: return
        cancel(modelId)
        modelDir(model).deleteRecursively()
        put(modelId, InstallStatus.NOT_INSTALLED)
    }

    // --- download core -------------------------------------------------------

    private suspend fun runDownload(model: ModelInfo) {
        put(model.id, InstallStatus.DOWNLOADING, progress = 0f)
        val archive = partTargetFile(model)
        val part = partFile(model).apply { parentFile?.mkdirs() }
        var offset = if (part.exists()) part.length() else 0L

        // Gated (HF) models carry a bearer token from a license-accepted account.
        val hfToken = if (model.gated) hfCredentials.token() else null

        val request = Request.Builder()
            .url(model.url)
            .apply {
                if (offset > 0) addHeader("Range", "bytes=$offset-")
                if (!hfToken.isNullOrBlank()) addHeader("Authorization", "Bearer $hfToken")
            }
            .build()

        val downloaded = runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val detail = when (resp.code) {
                        401, 403 -> "Access denied (HTTP ${resp.code}) — check your Hugging Face token and that you've accepted this model's license."
                        else -> "Download failed (HTTP ${resp.code})."
                    }
                    put(model.id, InstallStatus.FAILED, detail = detail)
                    return
                }
                val body = resp.body ?: run {
                    put(model.id, InstallStatus.FAILED, detail = "Empty response."); return
                }
                // If we asked to resume (Range) the server MUST answer 206. A 200 means
                // it's sending the WHOLE file — seeking to `offset` would append it after
                // the partial bytes and corrupt the archive. Restart from 0 instead.
                if (offset > 0 && resp.code != 206) {
                    offset = 0L
                    runCatching { RandomAccessFile(part, "rw").use { it.setLength(0) } }
                }
                val total = if (model.downloadBytes > 0) model.downloadBytes else (offset + body.contentLength())
                body.byteStream().use { input ->
                    RandomAccessFile(part, "rw").use { out ->
                        out.seek(offset)
                        val buf = ByteArray(128 * 1024)
                        while (scope.isActive && paused[model.id] != true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            offset += n
                            if (total > 0) put(model.id, InstallStatus.DOWNLOADING, (offset.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            true
        }.getOrElse { t ->
            // Coroutine cancellation (pause/cancel) lands here — leave state as set by
            // pause()/cancel(); do NOT overwrite it with a spurious "Network error".
            if (t is kotlinx.coroutines.CancellationException || paused[model.id] == true) return
            put(model.id, InstallStatus.FAILED, detail = "Network error: ${t.message}")
            false
        }
        if (!downloaded || paused[model.id] == true) return

        put(model.id, InstallStatus.VERIFYING, progress = 1f)
        if (model.sha256.isNotBlank()) {
            if (!verify(part, model.sha256)) {
                part.delete()
                put(model.id, InstallStatus.FAILED, detail = "Integrity check failed; download discarded.")
                return
            }
        } else {
            // Gated model with no pre-pinned SHA: we can't hash-verify, so sanity-
            // check the size against the catalog estimate to reject truncated/HTML
            // error bodies masquerading as a model.
            val min = (model.downloadBytes * 0.5).toLong()
            if (model.downloadBytes > 0 && part.length() < min) {
                part.delete()
                put(model.id, InstallStatus.FAILED, detail = "Download looks incomplete; discarded. Try again.")
                return
            }
        }
        if (!part.renameTo(archive)) {
            put(model.id, InstallStatus.FAILED, detail = "Could not finalize the download.")
            return
        }

        if (model.extract) {
            put(model.id, InstallStatus.EXTRACTING, progress = 1f)
            val extracted = runCatching { extractTarBz2(archive, modelDir(model)) }.getOrElse {
                put(model.id, InstallStatus.FAILED, detail = "Extraction failed: ${it.message}"); return
            }
            archive.delete() // reclaim the ~116 MB bundle once unpacked
            if (!extracted) {
                put(model.id, InstallStatus.FAILED, detail = "Extraction produced no model files."); return
            }
        }
        put(model.id, InstallStatus.INSTALLED, progress = 1f)
    }

    /** Unpack a .tar.bz2 into [dest], flattening the single top-level dir the bundle uses. */
    private fun extractTarBz2(archive: File, dest: File): Boolean {
        dest.mkdirs()
        var wrote = false
        archive.inputStream().buffered().use { fin ->
            BZip2CompressorInputStream(fin).use { bz ->
                TarArchiveInputStream(bz).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            // Strip the leading "sherpa-onnx-whisper-tiny/" component.
                            val name = entry.name.substringAfter('/', entry.name)
                            if (name.isNotBlank() && !name.contains("test_wavs")) {
                                val outFile = File(dest, name).apply { parentFile?.mkdirs() }
                                outFile.outputStream().use { tar.copyTo(it) }
                                wrote = true
                            }
                        }
                        entry = tar.nextEntry
                    }
                }
            }
        }
        return wrote
    }

    // --- helpers -------------------------------------------------------------

    private fun modelDir(model: ModelInfo) = File(File(context.filesDir, "models"), model.id)

    /** Final file for a single-file model; ignored for bundles (extracted instead). */
    private fun partTargetFile(model: ModelInfo) = File(modelDir(model), model.fileName)
    private fun partFile(model: ModelInfo) = File(modelDir(model), model.fileName + ".part")

    /** Installed = extracted model files exist (bundle) or the single file exists. */
    private fun isInstalled(model: ModelInfo): Boolean {
        val dir = modelDir(model)
        return if (model.extract) {
            dir.isDirectory && (dir.listFiles()?.any { it.name.endsWith(".onnx") } == true)
        } else {
            partTargetFile(model).exists()
        }
    }

    private fun initialState(model: ModelInfo): ModelInstallState = when {
        isInstalled(model) -> ModelInstallState(model.id, InstallStatus.INSTALLED, 1f)
        // Gated models: show a Download button (NOT_INSTALLED). Tapping it checks
        // for a HF token and, if missing, prompts to add one + accept the license.
        model.gated -> ModelInstallState(model.id, InstallStatus.NOT_INSTALLED)
        model.sha256.isBlank() -> ModelInstallState(
            model.id, InstallStatus.UNAVAILABLE,
            detail = when {
                model.role == ModelRole.LLM -> "Coming soon — no on-device build published yet."
                model.role == ModelRole.EMBEDDING -> "Coming soon — enables offline semantic search."
                else -> "Coming soon."
            },
        )
        partFile(model).exists() -> ModelInstallState(
            model.id, InstallStatus.PAUSED,
            progress = if (model.downloadBytes > 0) partFile(model).length().toFloat() / model.downloadBytes else 0f,
            detail = "Paused — tap to resume",
        )
        else -> ModelInstallState(model.id, InstallStatus.NOT_INSTALLED)
    }

    private fun put(id: String, status: InstallStatus, progress: Float = 0f, detail: String? = null) {
        states.update { it + (id to ModelInstallState(id, status, progress, detail)) }
    }

    private fun verify(file: File, expected: String): Boolean {
        if (expected.isBlank() || !file.exists()) return false
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { s ->
            val buf = ByteArray(128 * 1024)
            while (true) { val n = s.read(buf); if (n <= 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }.equals(expected, ignoreCase = true)
    }
}
