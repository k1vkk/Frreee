package com.example

import java.net.URI

object NexoraConfig {

    /**
     * Reads the embedded HTTPS backend API base URL from BuildConfig (.env / Secrets).
     * Never falls back to localhost or a fake placeholder domain.
     */
    val DEFAULT_API_BASE_URL: String
        get() = normalizeProductionHttpsUrl(BuildConfig.NEXORA_API_BASE_URL) ?: ""

    fun normalizeProductionHttpsUrl(raw: String?): String? {
        val clean = raw?.trim()?.trimEnd('/') ?: return null
        if (!clean.startsWith("https://")) return null
        val host = runCatching { URI(clean).host?.lowercase() }.getOrNull() ?: return null
        if (host.isBlank() ||
            host == "localhost" ||
            host == "127.0.0.1" ||
            host == "10.0.2.2" ||
            host.endsWith("yourdomain.com") ||
            host.contains("example.com")
        ) {
            return null
        }
        return clean
    }

    fun isValidProductionHttpsUrl(raw: String?): Boolean = normalizeProductionHttpsUrl(raw) != null
}
