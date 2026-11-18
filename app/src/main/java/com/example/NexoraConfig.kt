package com.example

object NexoraConfig {
    /**
     * Local/Demo preview mode:
     * - true: Defaults to http://localhost:8080 and falls back seamlessly to local preview
     *   simulation/fixtures when no live backend server is reachable, preventing DNS/network errors.
     * - false: Strict production mode (HTTPS-only backend & verified WireGuard handshake required).
     */
    const val IS_LOCAL_DEMO_MODE = true

    /**
     * Default Backend API base URL.
     * For production deployment, set IS_LOCAL_DEMO_MODE = false and replace with your HTTPS domain,
     * e.g. "https://api.nexoravpn.com"
     */
    const val NEXORA_API_BASE_URL = "http://localhost:8080"

    fun isLocalHostUrl(url: String): Boolean {
        val clean = url.trim().lowercase()
        return clean.startsWith("http://localhost") ||
            clean.startsWith("http://127.0.0.1") ||
            clean.startsWith("http://10.0.2.2") ||
            clean.startsWith("https://localhost") ||
            clean.startsWith("https://127.0.0.1")
    }
}
