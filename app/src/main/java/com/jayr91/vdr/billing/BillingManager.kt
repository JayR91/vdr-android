package com.jayr91.vdr.billing

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Thin Play Billing wrapper for the one-time [ProGates.PRODUCT_ID] unlock.
 */
class BillingManager(
    context: Context,
) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    fun start() {
        if (client.isReady) {
            _ready.value = true
            queryProduct()
            refreshPurchases()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _ready.value = true
                    _lastError.value = null
                    queryProduct()
                    refreshPurchases()
                } else {
                    _ready.value = false
                    _lastError.value = billingMessage(result)
                }
            }

            override fun onBillingServiceDisconnected() {
                _ready.value = false
            }
        })
    }

    fun end() {
        runCatching { client.endConnection() }
        // The scope outlives the connection otherwise; a pending acknowledge()
        // would sit on a client that is already gone.
        scope.coroutineContext[Job]?.cancelChildren()
        _ready.value = false
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                _lastError.value = null
                // This callback carries only the purchases that just changed,
                // so it can grant but must never revoke -- an empty list here
                // means "nothing new", not "you own nothing".
                scope.launch { handlePurchases(purchases.orEmpty(), authoritative = false) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _lastError.value = null
            }
            else -> _lastError.value = billingMessage(result)
        }
    }

    fun launchPurchase(activity: Activity): Boolean {
        val details = _productDetails.value
        if (!_ready.value || details == null) {
            _lastError.value = "Billing unavailable. Check Play Store / network, then try Restore."
            return false
        }
        val offer = details.oneTimePurchaseOfferDetails
        if (offer == null) {
            _lastError.value = "Pro product has no offer. Create ${ProGates.PRODUCT_ID} in Play Console."
            return false
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        val result = client.launchBillingFlow(activity, flow)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _lastError.value = billingMessage(result)
            return false
        }
        return true
    }

    fun restorePurchases() {
        if (!_ready.value) {
            _lastError.value = "Billing unavailable. Open Play Store and try again."
            start()
            return
        }
        refreshPurchases()
    }

    private fun queryProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(ProGates.PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        client.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _lastError.value = billingMessage(result)
                return@queryProductDetailsAsync
            }
            // Billing 7.x listener second arg is List<ProductDetails>.
            val list = detailsList
            _productDetails.value = list.firstOrNull()
            if (list.isEmpty()) {
                _lastError.value =
                    "Pro product “${ProGates.PRODUCT_ID}” not found. Activate it in Play Console."
            }
        }
    }

    private fun refreshPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _lastError.value = billingMessage(result)
                return@queryPurchasesAsync
            }
            // queryPurchasesAsync returns everything currently owned, so this
            // result -- and only this one -- can also take Pro away.
            scope.launch { handlePurchases(purchases, authoritative = true) }
        }
    }

    /**
     * @param authoritative true only when [purchases] is the complete set of
     * currently-owned products (i.e. from queryPurchasesAsync). A refund,
     * chargeback or developer revocation shows up as that product simply
     * being absent, so an authoritative empty result is what re-locks Pro.
     * Without this the entitlement was write-once: granted forever on the
     * first purchase and never taken back, no matter what Play later said.
     */
    private suspend fun handlePurchases(purchases: List<Purchase>, authoritative: Boolean) {
        var unlocked = false
        var pending = false
        for (purchase in purchases) {
            if (ProGates.PRODUCT_ID !in purchase.products) continue
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    unlocked = true
                    if (!purchase.isAcknowledged) {
                        acknowledge(purchase)
                    }
                }
                // Slow payment methods (UPI mandates, netbanking, cash) land
                // here. Saying nothing looked exactly like a failed purchase,
                // so people paid and then saw a still-locked app with no
                // explanation.
                Purchase.PurchaseState.PENDING -> pending = true
                else -> Unit
            }
        }
        if (unlocked) {
            ProEntitlement.setPurchased(appContext, true)
            _lastError.value = null
        } else if (authoritative) {
            ProEntitlement.setPurchased(appContext, false)
            if (pending) {
                _lastError.value =
                    "Payment pending — Pro unlocks once your payment clears. Tap Restore later."
            }
        } else if (pending) {
            _lastError.value =
                "Payment pending — Pro unlocks once your payment clears. Tap Restore later."
        }
    }

    private suspend fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        suspendCancellableCoroutine { cont ->
            client.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    _lastError.value = billingMessage(result)
                }
                cont.resume(Unit)
            }
        }
    }

    private fun billingMessage(result: BillingResult): String {
        val debug = result.debugMessage?.takeIf { it.isNotBlank() }
        return debug ?: "Billing error ${result.responseCode}"
    }
}
