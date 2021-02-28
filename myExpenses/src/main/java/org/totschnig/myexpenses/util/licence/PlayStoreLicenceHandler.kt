package org.totschnig.myexpenses.util.licence

import android.app.Activity
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsResponseListener
import com.google.android.vending.licensing.PreferenceObfuscator
import org.json.JSONException
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.activity.ContribInfoDialogActivity
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import java.util.*

open class PlayStoreLicenceHandler(context: MyApplication, preferenceObfuscator: PreferenceObfuscator, crashHandler: CrashHandler, prefHandler: PrefHandler) : AbstractInAppPurchaseLicenceHandler(context, preferenceObfuscator, crashHandler, prefHandler) {
    private fun storeSkuDetails(inventory: List<SkuDetails>) {
        val editor = pricesPrefs.edit()
        for (skuDetails in inventory) {
            log().w("Sku %s, json: %s", skuDetails.sku, skuDetails.toString())
            editor.putString(prefKeyForSkuJson(skuDetails.sku), skuDetails.originalJson)
        }
        editor.apply()
    }

    private fun prefKeyForSkuJson(sku: String): String {
        return String.format(Locale.ROOT, "%s_json", sku)
    }

    override fun getDisplayPriceForPackage(aPackage: Package) =
            getSkuDetailsFromPrefs(getSkuForPackage(aPackage))?.let { skuDetails ->
                skuDetails.introductoryPrice.takeIf { !TextUtils.isEmpty(it) } ?: skuDetails.price
            }

    fun getSkuDetailsFromPrefs(sku: String): SkuDetails? {
        pricesPrefs.getString(prefKeyForSkuJson(sku), null)?.let {
            return try {
                return SkuDetails(it)
            } catch (e: JSONException) {
                CrashHandler.report(e, String.format("unable to parse %s", it))
                null
            }
        } ?: run {
            CrashHandler.report(String.format("originalJson not found for %s", sku))
        }
        return null
    }

    private val currentSubscription: String?
        get() = licenseStatusPrefs.getString(KEY_CURRENT_SUBSCRIPTION, null)

    override fun initBillingManager(activity: Activity, query: Boolean): BillingManagerPlay {
        val billingUpdatesListener: BillingUpdatesListener = object : BillingUpdatesListener {
            override fun onPurchasesUpdated(purchases: List<Purchase>?): Boolean {
                var result = false
                if (purchases != null) {
                    val oldStatus = licenceStatus
                    result = registerInventory(purchases) != null
                    if (activity is BillingListener) {
                        (activity as BillingListener).onLicenceStatusSet(licenceStatus, oldStatus)
                    }
                }
                return result
            }

            override fun onPurchaseCanceled() {
                log().i("onPurchasesUpdated() - user cancelled the purchase flow - skipping")
                if (activity is ContribInfoDialogActivity) {
                    activity.onPurchaseCancelled()
                }
            }

            override fun onPurchaseFailed(resultCode: Int) {
                log().w("onPurchasesUpdated() got unknown resultCode: %s", resultCode)
                if (activity is ContribInfoDialogActivity) {
                    activity.onPurchaseFailed(resultCode)
                }
            }
        }
        val skuDetailsResponseListener = if (query) SkuDetailsResponseListener { result: BillingResult, skuDetailsList: List<SkuDetails>? ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                storeSkuDetails(skuDetailsList)
            } else {
                log().d("skuDetails response %d", result.responseCode)
            }
        } else null
        return BillingManagerPlay(activity, billingUpdatesListener, skuDetailsResponseListener)
    }

    @VisibleForTesting
    fun findHighestValidPurchase(inventory: List<Purchase>) = inventory.mapNotNull { purchase -> extractLicenceStatusFromSku(purchase.sku)?.let { Pair(purchase, it) } }
            .maxByOrNull { pair -> pair.second }?.first

    private fun registerInventory(inventory: List<Purchase>): LicenceStatus? {
        inventory.forEach { purchase: Purchase -> log().i("%s (acknowledged %b)", purchase.sku, purchase.isAcknowledged) }
        return findHighestValidPurchase(inventory)?.let {
            if (it.purchaseState == Purchase.PurchaseState.PURCHASED) {
                handlePurchase(it.sku, it.orderId)
            } else {
                CrashHandler.reportWithTag(String.format("Found purchase in state %s", it.purchaseState), TAG)
                null
            }
        } ?: run {
            maybeCancel()
            null
        }
    }

    override fun launchPurchase(aPackage: Package, shouldReplaceExisting: Boolean, billingManager: BillingManager) {
        val sku = getSkuForPackage(aPackage)
        val skuDetails = getSkuDetailsFromPrefs(sku)
                ?: throw IllegalStateException("Could not determine sku details")
        val oldSku: String?
        if (shouldReplaceExisting) {
            oldSku = currentSubscription
            checkNotNull(oldSku) { "Could not determine current subscription" }
        } else {
            oldSku = null
        }
        (billingManager as BillingManagerPlay).initiatePurchaseFlow(skuDetails, oldSku)
    }
}