package com.vaani.data.device.wifi

import android.net.Network
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wi-Fi transport to the wearable's HTTP server (SoftAP at 192.168.4.1). All calls are routed
 * ONLY through the bound AP [Network] via its socketFactory/DNS — the rest of the app keeps its
 * normal network (we never bindProcessToNetwork). Blocking calls; callers wrap in Dispatchers.IO.
 */
@Singleton
class WifiHttpClient @Inject constructor() {
    var baseUrl: String = "http://192.168.4.1"

    // Rebuilt whenever the bound network changes so sockets open on the AP interface.
    @Volatile private var network: Network? = null
    @Volatile private var http: OkHttpClient = build(null)

    fun useNetwork(net: Network?) {
        network = net
        http = build(net)
    }

    private fun build(net: Network?): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
        if (net != null) {
            b.socketFactory(net.socketFactory)
            b.dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> = net.getAllByName(hostname).toList()
            })
        }
        return b.build()
    }

    fun info(): String = get("/api/info")

    fun listFiles(path: String): String = get("/api/files?path=${enc(path)}")

    fun readFile(path: String): ByteArray {
        val req = Request.Builder().url("$baseUrl/api/file?path=${enc(path)}").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
            return r.body?.bytes() ?: ByteArray(0)
        }
    }

    fun writeFile(path: String, bytes: ByteArray): String {
        // Streaming multipart upload to the firmware's /api/upload (no whole-body RAM buffer on device).
        val body = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart(
                "file", path.substringAfterLast('/'),
                bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()),
            )
            .build()
        val req = Request.Builder().url("$baseUrl/api/upload?path=${enc(path)}").post(body).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
            return r.body?.string() ?: ""
        }
    }

    fun deleteFile(path: String): String = get("/api/delete?path=${enc(path)}")

    private fun get(pathAndQuery: String): String {
        val req = Request.Builder().url("$baseUrl$pathAndQuery").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
            return r.body?.string() ?: ""
        }
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
