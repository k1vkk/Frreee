package com.example.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NexoraApp
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
            val prefs = prefsRepo.preferencesFlow.first()
            if (prefs.apiBaseUrl.isNotBlank()) {
                testBackendHealth(prefs.apiBaseUrl)
            }
            if (prefs.authToken.isNotBlank()) {
                refreshProfile()
            }
        }
    }

    fun saveAndTestBackendUrl(rawUrl: String) {
        viewModelScope.launch {
            val clean = rawUrl.trim().trimEnd('/')
            if (!clean.startsWith("https://") || clean.contains("localhost") || clean.contains("127.0.0.1")) {
                _apiHealth.value = ApiHealthState.Unreachable("Production API URL must use https:// (no localhost)")
                return@launch
            }
            prefsRepo.setApiBaseUrl(clean)
            testBackendHealth(clean)
        }
    }

    private suspend fun testBackendHealth(url: String) {
        _apiHealth.value = ApiHealthState.Checking
        val service = apiClient.createService(url)
        if (service == null) {
            _apiHealth.value = ApiHealthState.Unreachable("Invalid URL")
            return
        }
        try {
            val res = withContext(Dispatchers.IO) { service.checkHealth() }
            if (res.isSuccessful && res.body()?.ok == true) {
                val body = res.body()!!
                val db = body.database ?: "postgresql"
                val online = body.onlineNodes ?: 0
                val slots = body.totalSlots ?: 0
                _apiHealth.value = ApiHealthState.Healthy("${db.uppercase()} • $online/$slots VPS Online")
            } else {
                _apiHealth.value = ApiHealthState.Unreachable("HTTP ${res.code()}")
            }
        } catch (e: Exception) {
            _apiHealth.value = ApiHealthState.Unreachable(e.localizedMessage ?: "Network error")
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            try {
                val prefs = prefsRepo.preferencesFlow.first()
                val service = apiClient.createService(prefs.apiBaseUrl)
                    ?: throw IllegalStateException("Configure Backend API URL in Settings first.")
                val deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                val res = withContext(Dispatchers.IO) {
                    service.login(LoginRequest(email.trim(), password, deviceLabel))
                }
                if (!res.isSuccessful || res.body() == null) {
                    throw IllegalStateException("Authentication failed (HTTP ${res.code()})")
                }
                val body = res.body()!!
                prefsRepo.setAuthSession(
                    token = body.token,
                    email = body.user.email,
                    displayName = body.user.displayName,
                    planTier = body.user.planTier
                )
                refreshProfile()
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
            try {
                val prefs = prefsRepo.preferencesFlow.first()
                val service = apiClient.createService(prefs.apiBaseUrl)
                    ?: throw IllegalStateException("Configure Backend API URL in Settings first.")
                val deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                val res = withContext(Dispatchers.IO) {
                    service.register(RegisterRequest(email.trim(), password, displayName.trim(), deviceLabel))
                }
                if (!res.isSuccessful || res.body() == null) {
                    throw IllegalStateException("Registration failed (HTTP ${res.code()})")
                }
                val body = res.body()!!
                prefsRepo.setAuthSession(
                    token = body.token,
                    email = body.user.email,
                    displayName = body.user.displayName,
                    planTier = body.user.planTier
                )
                refreshProfile()
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
                val prefs = prefsRepo.preferencesFlow.first()
                val service = apiClient.createService(prefs.apiBaseUrl)
                    ?: throw IllegalStateException("Configure HTTPS Backend API URL in Settings first.")
                val res = withContext(Dispatchers.IO) {
                    service.forgotPassword(ForgotPasswordRequest(email.trim()))
                }
                if (res.isSuccessful) {
                    _authNotice.value = res.body()?.message ?: "Verification code dispatched if account exists."
                } else {
                    val errBody = res.errorBody()?.string().orEmpty()
                    throw IllegalStateException(
                        if (errBody.contains("RESEND_API_KEY")) "Email provider not configured on backend. Ask Admin to reset password."
                        else "Reset request failed (HTTP ${res.code()})"
                    )
                }
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
                val prefs = prefsRepo.preferencesFlow.first()
                val service = apiClient.createService(prefs.apiBaseUrl)
                    ?: throw IllegalStateException("Configure HTTPS Backend API URL in Settings first.")
                val res = withContext(Dispatchers.IO) {
                    service.resetPassword(ResetPasswordRequest(email.trim(), code.trim(), newPassword))
                }
                if (res.isSuccessful) {
                    _authNotice.value = res.body()?.message ?: "Password updated. Sign in with your new password."
                    onSuccess()
                } else {
                    throw IllegalStateException("Invalid or expired verification code (HTTP ${res.code()})")
                }
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
