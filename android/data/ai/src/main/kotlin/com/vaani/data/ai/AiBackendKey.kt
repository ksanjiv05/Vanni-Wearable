package com.vaani.data.ai

import com.vaani.domain.ai.AiBackend
import dagger.MapKey

/**
 * Dagger map-key for contributing engine implementations into the router's
 * multibindings keyed by [AiBackend] (ADR-001). Backend modules annotate their
 * @Binds @IntoMap with this so `Map<AiBackend, AsrEngine>` / `<…, Enricher>`
 * assemble automatically.
 */
@MapKey
annotation class AiBackendKey(val value: AiBackend)
