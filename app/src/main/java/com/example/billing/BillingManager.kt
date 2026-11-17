package com.example.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.NexoraApiClient
import com.example.data.remote.VerifyPurchaseRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SubscriptionProductUi(
    val productId: String,
    val formattedPrice: String?,
    val productDetails: ProductDetails?
)

data class BillingUiState(
    val isPlayConnected: Boolean = false,
    val statusMessage: String? = null,
    val products: Map<String, SubscriptionProductUi> = emptyMap(),
    val isPurchasing: Boolean = false
)

class BillingManager(
    private val context: Context,
    private val prefsRepo: UserPreferencesRepository,
    private val apiClient: NexoraApiClient
) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_PRO_MONTHLY = "nexora_pro_monthly"
        const val PRODUCT_PRO_ANNUAL = "nexora_pro_annual"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    fun startConnection() {
        if (billingClient.isReady) {
            querySubscriptionProducts()
            restorePurchases()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.update { it.copy(isPlayConnected = true, statusMessage = null) }
                    querySubscriptionProducts()
                    restorePurchases()
                } else {
                    _state.update {
                        it.copy(
                            isPlayConnected = false,
                            statusMessage = result.debugMessage.ifBlank { "Play Billing unavailable (${result.responseCode})" }
                        )
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.update { it.copy(isPlayConnected = false) }
            }
        })
    }

    fun querySubscriptionProducts() {
        if (!billingClient.isReady) return
        val productList = listOf(PRODUCT_PRO_MONTHLY, PRODUCT_PRO_ANNUAL).map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val map = productDetailsList.associate { details ->
                    val price = details.subscriptionOfferDetails
                        ?.firstOrNull()
                        ?.pricingPhases
                        ?.pricingPhaseList
                        ?.firstOrNull()
                        ?.formattedPrice
                    details.productId to SubscriptionProductUi(
                        productId = details.productId,
                        formattedPrice = price,
                        productDetails = details
                    )
                }
                _state.update { it.copy(products = map) }
            }
        }
    }

    fun launchPurchase(activity: Activity, productId: String) {
        val product = _state.value.products[productId]?.productDetails
        val offerToken = product?.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (product == null || offerToken == null) {
            _state.update {
                it.copy(statusMessage = "Product $productId is not configured on Play Console yet.")
            }
            return
        }
        _state.update { it.copy(isPurchasing = true, statusMessage = null) }
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .setOfferToken(offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    fun restorePurchases() {
        if (!billingClient.isReady) {
            startConnection()
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                scope.launch {
                    handlePurchases(purchases)
                }
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        _state.update { it.copy(isPurchasing = false) }
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch { handlePurchases(purchases) }
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            _state.update {
                it.copy(statusMessage = result.debugMessage.ifBlank { "Purchase error ${result.responseCode}" })
            }
        }
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (!purchase.isAcknowledged) {
                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    withContext(Dispatchers.IO) {
                        billingClient.acknowledgePurchase(ackParams) { }
                    }
                }
                val productId = purchase.products.firstOrNull() ?: continue
                val tier = when (productId) {
                    PRODUCT_PRO_ANNUAL -> "pro_annual"
                    PRODUCT_PRO_MONTHLY -> "pro_monthly"
                    else -> "free"
                }
                prefsRepo.updatePlanTier(tier)
                syncPurchaseWithBackend(productId, purchase.purchaseToken)
            }
        }
    }

    private suspend fun syncPurchaseWithBackend(productId: String, purchaseToken: String) {
        val prefs = prefsRepo.preferencesFlow.first()
        val service = apiClient.createService(prefs.apiBaseUrl) ?: return
        if (prefs.authToken.isBlank()) return
        try {
            withContext(Dispatchers.IO) {
                service.verifyPurchase(
                    bearer = "Bearer ${prefs.authToken}",
                    body = VerifyPurchaseRequest(
                        productId = productId,
                        purchaseToken = purchaseToken,
                        packageName = context.packageName
                    )
                )
            }
        } catch (_: Exception) {
        }
    }
}
