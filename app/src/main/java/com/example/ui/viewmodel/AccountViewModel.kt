package com.example.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NexoraApp
import com.example.NexoraConfig
import com.example.data.local.LocalDemoFixtures
import com.example.data.local.UserPreferences
import com.example.data.remote.ForgotPasswordRequest
import com.example.data.remote.LoginRequest
import com.example.data.remote.RegisterRequest
import com.example.data.remote.ResetPasswordRequest
import com.example.data.remote.SessionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ApiHealthState {
    data object NotConfigured : ApiHealthState
    data object Checking : ApiHealthState
    data class Healthy(val info: String) : ApiHealthState
    data class Unreachable(val reason: String) : ApiHealthState
}

class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NexoraApp
    private val prefsRepo = app.prefsRepo
    private val apiClient = app.apiClient

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserPreferences()
    )

    private val _sessions = MutableStateFlow<List<SessionDto>>(emptyList())
    val sessions: StateFlow<List<SessionDto>> = _sessions.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authNotice = MutableStateFlow<String?>(null)
    val authNotice: StateFlow<String?> = _authNotice.asStateFlow()

    private val _apiHealth = MutableStateFlow<ApiHealthState>(ApiHealthState.NotConfigured)
    val apiHealth: StateFlow<ApiHealthState> = _apiHealth.asStateFlow()

    init {
        viewModelScope.launch {
            prefsRepo.ensureWireGuardKeyPair()
            testBackendHealth(NexoraConfig.NEXORA_API_BASE_URL)
            val prefs = prefsRepo.preferencesFlow.first()
            if (prefs.authToken.isNotBlank()) {
                refreshProfile()
                syncSubscription()
            }
        }
    }

    fun refreshBackendHealth() {
        viewModelScope.launch {
            testBackendHealth(NexoraConfig.NEXORA_API_BASE_URL)
        }
    }

    private suspend fun testBackendHealth(url: String) {
        _apiHealth.value = ApiHealthState.Checking
        val service = apiClient.createService(url)
        if (service != null) {
            try {
                val res = withContext(Dispatchers.IO) { service.checkHealth() }
                if (res.isSuccessful && res.body()?.ok == true) {
                    val body = res.body()!!
                    val db = body.database ?: "postgresql"
                    val online = body.onlineNodes ?: 0
                    val slots = body.totalSlots ?: 0
                    _apiHealth.value = ApiHealthState.Healthy("${db.uppercase()} • $online/$slots VPS Online")
                    return
                } else if (!NexoraConfig.IS_LOCAL_DEMO_MODE) {
                    _apiHealth.value = ApiHealthState.Unreachable("HTTP ${res.code()}")
                    return
                }
            } catch (e: Exception) {
                if (!NexoraConfig.IS_LOCAL_DEMO_MODE) {
                    _apiHealth.value = ApiHealthState.Unreachable(e.localizedMessage ?: "Network error")
                    return
                }
            }
        } else if (!NexoraConfig.IS_LOCAL_DEMO_MODE) {
            _apiHealth.value = ApiHealthState.Unreachable("Invalid URL")
            return
        }

        _apiHealth.value = ApiHealthState.Healthy("Local Demo Mode • 8/8 GCC Nodes")
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            val deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            try {
                val service = apiClient.createService(NexoraConfig.NEXORA_API_BASE_URL)
                if (service != null) {
                    try {
                        val res = withContext(Dispatchers.IO) {
                            service.login(LoginRequest(email.trim(), password, deviceLabel))
                        }
                        if (res.isSuccessful && res.body() != null) {
                            val body = res.body()!!
                            prefsRepo.setAuthSession(
                                token = body.token,
                                email = body.user.email,
                                displayName = body.user.displayName,
                                planTier = body.user.planTier
                            )
                            refreshProfile()
                            return@launch
                        } else if (!NexoraConfig.IS_LOCAL_DEMO_MODE) {
                            throw IllegalStateException("Authentication failed (HTTP ${res.code()})")
                        }
                    } catch (e: Exception) {
                        if (!NexoraConfig.IS_LOCAL_DEMO_MODE) throw e
                    }
                } else if (!NexoraConfig.IS_LOCAL_DEMO_MODE) {
                    throw IllegalStateException("Backend API endpoint unavailable.")
                }

                // Local Demo Mode fallback sign-in
                val cleanEmail = email.trim()
                val name = cleanEmail.substringBefore('@').replaceFirstChar { it.uppercase() }.ifBlank { "NEXORA User" }
                prefsRepo.setAuthSession(
                    token = "local-demo-jwt-token",
                    email = cleanEmail,
                    displayName = name,
                    planTier = "pro"
                )
                _sessions.value = LocalDemoFixtures.demoSessions(deviceLabel)
            } catch (e: Exception) {
                _authError.value = e.localizedMessage ?: "Sign in failed"
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun register(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            val deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            try {
                val service = apiClient.createService(NexoraConfig.NEXORA_API_BASE_URL)
                if (service != null) {
                    try {
                        val res = withContext(Dispatchers.IO) {
                            service.register(RegisterRequest(email.trim(), password, displayName.trim(), deviceLabel))
                        }
                        if (res.isSuccessful && res.body() != null) {
                            val body = res.body()!!
                            prefsRepo.setAuthSession(
                                token = body.token,
                                email = body.user.email,
                                displayName = body.user.displayName,
                                planTier = body.user.planTier
                            )
                            refreshProfile()
                            return@launch
                        } else if (!NexoraConfig.IS_LOCAL_DEMO_MODE) {
                            throw IllegalStateException("Registration failed (HTTP ${res.code()})")
                        }
                    } catch (e: Exception) {
                        if (!NexoraConfig.IS_LOCAL_DEMO_MODE) throw e
                    }
                } else if (!NexoraConfig.IS_LOCAL_DEMO_MODE) {
                    throw IllegalStateException("Backend API endpoint unavailable.")
                }

                val cleanEmail = email.trim()
                val name = displayName.trim().ifBlank { cleanEmail.substringBefore('@') }
                prefsRepo.setAuthSession(
                    token = "local-demo-jwt-token",
                    email = cleanEmail,
                    displayName = name,
                    planTier = "free"
                )
                _sessions.value = LocalDemoFixtures.demoSessions(deviceLabel)
            } catch (e: Exception) {
                _authError.value = e.localizedMessage ?: "Registration failed"
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            val prefs = prefsRepo.preferencesFlow.first()
            if (prefs.authToken.isBlank()) return@launch
            if (NexoraConfig.IS_LOCAL_DEMO_MODE && prefs.authToken == "local-demo-jwt-token") {
                val deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                _sessions.value = LocalDemoFixtures.demoSessions(deviceLabel)
                return@launch
            }
            val service = apiClient.createService(prefs.apiBaseUrl) ?: return@launch
            try {
                val res = withContext(Dispatchers.IO) {
                    service.getMe("Bearer ${prefs.authToken}")
                }
                if (res.isSuccessful && res.body() != null) {
                    val me = res.body()!!
                    prefsRepo.setAuthSession(
                        token = prefs.authToken,
                        email = me.user.email,
                        displayName = me.user.displayName,
                        planTier = me.user.planTier
                    )
                    _sessions.value = me.sessions
                } else if (res.code() == 401) {
                    prefsRepo.clearAuthSession()
                    _sessions.value = emptyList()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun revokeSession(sessionId: String) {
        viewModelScope.launch {
            val prefs = prefsRepo.preferencesFlow.first()
            if (NexoraConfig.IS_LOCAL_DEMO_MODE && prefs.authToken == "local-demo-jwt-token") {
                _sessions.value = _sessions.value.filterNot { it.id == sessionId }
                return@launch
            }
            val service = apiClient.createService(prefs.apiBaseUrl) ?: return@launch
            try {
                withContext(Dispatchers.IO) {
                    service.revokeSession("Bearer ${prefs.authToken}", sessionId)
                }
                refreshProfile()
            } catch (_: Exception) {
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            val prefs = prefsRepo.preferencesFlow.first()
            val service = apiClient.createService(prefs.apiBaseUrl)
            if (service != null && prefs.authToken.isNotBlank()) {
                try {
                    withContext(Dispatchers.IO) {
                        service.logout("Bearer ${prefs.authToken}")
                    }
                } catch (_: Exception) {
                }
            }
            prefsRepo.clearAuthSession()
            _sessions.value = emptyList()
        }
    }

    fun rotateWireGuardKey() {
        viewModelScope.launch {
            prefsRepo.rotateWireGuardKeyPair()
        }
    }

    fun setLanguageMode(mode: String) {
        viewModelScope.launch {
            prefsRepo.setLanguageMode(mode)
        }
    }

    fun saveWireGuardSettings(
        dns: String,
        mtu: Int,
        autoConnect: Boolean,
        killSwitch: Boolean,
        autoReconnect: Boolean,
        dnsLeakProtection: Boolean
    ) {
        viewModelScope.launch {
            prefsRepo.setWireGuardSettings(
                customDns = dns,
                mtu = mtu,
                autoConnect = autoConnect,
                killSwitch = killSwitch,
                autoReconnect = autoReconnect,
                dnsLeakProtection = dnsLeakProtection
            )
        }
    }

    fun toggleExcludedPackage(packageName: String) {
        viewModelScope.launch {
            val current = prefsRepo.preferencesFlow.first().excludedPackages.toMutableSet()
            if (current.contains(packageName)) {
                current.remove(packageName)
            } else {
                current.add(packageName)
            }
            prefsRepo.setExcludedPackages(current)
        }
    }

    fun requestPasswordReset(email: String) {
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            _authNotice.value = null
            try {
                val service = apiClient.createService(NexoraConfig.NEXORA_API_BASE_URL)
                if (service != null) {
                    try {
                        val res = withContext(Dispatchers.IO) {
                            service.forgotPassword(ForgotPasswordRequest(email.trim()))
                        }
                        if (res.isSuccessful) {
                            _authNotice.value = res.body()?.message ?: "Verification code dispatched if account exists."
                            return@launch
                        } else if (!NexoraConfig.IS_LOCAL_DEMO_MODE) {
                            val errBody = res.errorBody()?.string().orEmpty()
                            throw IllegalStateException(
                                if (errBody.contains("RESEND_API_KEY")) "Email provider not configured on backend. Ask Admin to reset password."
                                else "Reset request failed (HTTP ${res.code()})"
                            )
                        }
                    } catch (e: Exception) {
                        if (!NexoraConfig.IS_LOCAL_DEMO_MODE) throw e
                    }
                } else if (!NexoraConfig.IS_LOCAL_DEMO_MODE) {
                    throw IllegalStateException("Backend API endpoint unavailable.")
                }

                _authNotice.value = "Local Demo Mode: Enter any 6-digit code (e.g. 123456) to reset."
            } catch (e: Exception) {
                _authError.value = e.localizedMessage ?: "Reset request failed"
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun confirmPasswordReset(email: String, code: String, newPassword: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            _authNotice.value = null
            try {
                val service = apiClient.createService(NexoraConfig.NEXORA_API_BASE_URL)
                if (service != null) {
                    try {
                        val res = withContext(Dispatchers.IO) {
                            service.resetPassword(ResetPasswordRequest(email.trim(), code.trim(), newPassword))
                        }
                        if (res.isSuccessful) {
                            _authNotice.value = res.body()?.message ?: "Password updated. Sign in with your new password."
                            onSuccess()
                            return@launch
                        } else if (!NexoraConfig.IS_LOCAL_DEMO_MODE) {
                            throw IllegalStateException("Invalid or expired verification code (HTTP ${res.code()})")
                        }
                    } catch (e: Exception) {
                        if (!NexoraConfig.IS_LOCAL_DEMO_MODE) throw e
                    }
                } else if (!NexoraConfig.IS_LOCAL_DEMO_MODE) {
                    throw IllegalStateException("Backend API endpoint unavailable.")
                }

                _authNotice.value = "Local Demo Mode: Password updated. Sign in with your credentials."
                onSuccess()
            } catch (e: Exception) {
                _authError.value = e.localizedMessage ?: "Password reset failed"
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun syncSubscription() {
        viewModelScope.launch {
            val prefs = prefsRepo.preferencesFlow.first()
            if (prefs.authToken.isBlank()) return@launch
            val service = apiClient.createService(prefs.apiBaseUrl) ?: return@launch
            try {
                val res = withContext(Dispatchers.IO) {
                    service.getSubscription("Bearer ${prefs.authToken}")
                }
                if (res.isSuccessful && res.body() != null) {
                    prefsRepo.updatePlanTier(res.body()!!.planTier)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun clearAuthError() {
        _authError.value = null
        _authNotice.value = null
    }
}
