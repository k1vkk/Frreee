package com.example

import android.app.Application
import com.example.billing.BillingManager
import com.example.data.local.NexoraDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.NexoraApiClient
import com.example.network.NetworkDiagnostics
import com.example.vpn.VpnController

class NexoraApp : Application() {
    lateinit var prefsRepo: UserPreferencesRepository
        private set
    lateinit var database: NexoraDatabase
        private set
    lateinit var apiClient: NexoraApiClient
        private set
    lateinit var diagnostics: NetworkDiagnostics
        private set
    lateinit var vpnController: VpnController
        private set
    lateinit var billingManager: BillingManager
        private set

    override fun onCreate() {
        super.onCreate()
        prefsRepo = UserPreferencesRepository(this)
        database = NexoraDatabase.getInstance(this)
        apiClient = NexoraApiClient()
        diagnostics = NetworkDiagnostics(apiClient.okHttpClient)
        vpnController = VpnController(
            appContext = this,
            prefsRepo = prefsRepo,
            serverDao = database.serverProfileDao(),
            apiClient = apiClient,
            diagnostics = diagnostics
        )
        billingManager = BillingManager(
            context = this,
            prefsRepo = prefsRepo,
            apiClient = apiClient
        )
        billingManager.startConnection()
    }
}
