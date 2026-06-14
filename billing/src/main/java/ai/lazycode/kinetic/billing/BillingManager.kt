package ai.lazycode.kinetic.billing

import android.app.Activity
import android.content.Context
import android.util.Log
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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the user has unlocked. Pure value type — trivially testable. */
data class Entitlement(val unlocked: Boolean) {
    companion object {
        val LOCKED = Entitlement(false)
    }
}

/**
 * Reusable one-SKU Play Billing wrapper for the app-factory: a single
 * **non-consumable** "unlock everything" product. Connects, exposes live
 * [entitlement] + formatted [price], launches the purchase, acknowledges it, and
 * mirrors the result to local prefs so gating is correct offline. No backend —
 * fine for a cheap one-time unlock. Pass your Play Console product id.
 *
 * ```
 * val billing = BillingManager(context, scope, unlockProductId = "unlock_all").also { it.start() }
 * ```
 */
class BillingManager(
    context: Context,
    private val scope: CoroutineScope,
    private val unlockProductId: String,
    prefsName: String = "kinetic_billing",
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val _entitlement = MutableStateFlow(Entitlement(prefs.getBoolean(KEY, false)))
    val entitlement: StateFlow<Entitlement> = _entitlement.asStateFlow()

    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price.asStateFlow()

    private var details: ProductDetails? = null

    private val purchases = PurchasesUpdatedListener { result, list ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && list != null) {
            scope.launch { reconcile(list) }
        }
    }

    private val client = BillingClient.newBuilder(app)
        .setListener(purchases)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    fun start() {
        if (client.isReady) {
            scope.launch { queryProduct(); refresh() }
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch { queryProduct(); refresh() }
                } else {
                    Log.w(TAG, "setup: ${r.responseCode} ${r.debugMessage}")
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    fun buy(activity: Activity) {
        val pd = details ?: run { Log.w(TAG, "no ProductDetails (product set up in Play Console?)"); return }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd).build(),
                ),
            ).build()
        client.launchBillingFlow(activity, params)
    }

    /** "Restore purchases". */
    fun restore() { scope.launch { refresh() } }

    /** DEBUG ONLY — flip local entitlement to exercise the UI without Play. */
    fun debugSetUnlocked(unlocked: Boolean) {
        prefs.edit().putBoolean(KEY, unlocked).apply()
        _entitlement.value = Entitlement(unlocked)
    }

    fun release() { runCatching { client.endConnection() } }

    private suspend fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(unlockProductId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            ).build()
        val result = client.queryProductDetails(params)
        result.productDetailsList?.firstOrNull()?.let {
            details = it
            _price.value = it.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    private suspend fun refresh() {
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        reconcile(result.purchasesList)
    }

    private suspend fun reconcile(list: List<Purchase>) {
        var owned = false
        for (p in list) {
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            if (unlockProductId in p.products) {
                owned = true
                if (!p.isAcknowledged) {
                    runCatching {
                        client.acknowledgePurchase(
                            AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(p.purchaseToken).build(),
                        )
                    }
                }
            }
        }
        if (owned) {
            prefs.edit().putBoolean(KEY, true).apply()
            _entitlement.value = Entitlement(true)
        }
    }

    private companion object {
        const val TAG = "KineticBilling"
        const val KEY = "unlocked"
    }
}
