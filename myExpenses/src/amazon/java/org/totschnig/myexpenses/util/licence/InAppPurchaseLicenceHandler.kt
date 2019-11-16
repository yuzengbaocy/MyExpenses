package org.totschnig.myexpenses.util.licence

import android.app.Activity
import android.content.Context
import com.amazon.device.iap.model.Product
import com.amazon.device.iap.model.Receipt
import com.google.android.vending.licensing.PreferenceObfuscator
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.contrib.Config
import org.totschnig.myexpenses.contrib.Config.allSkus
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

class InAppPurchaseLicenceHandler(context: MyApplication, preferenceObfuscator: PreferenceObfuscator, crashHandler: CrashHandler) :
        AbstractInAppPurchaseLicenceHandler(context, preferenceObfuscator, crashHandler) {


    override fun initBillingManager(activity: Activity, query: Boolean): BillingManager {
        val billingUpdatesListener: BillingUpdatesListener = object : BillingUpdatesListener {
            override fun onPurchase(receipt: Receipt) = handlePurchase(receipt.sku, receipt.receiptId) != null

            override fun onPurchasesUpdated(purchases: MutableList<Receipt>) {
                registerInventory(purchases)
            }

            override fun onProductDataResponse(productData: MutableMap<String, Product>) {
                storeSkuDetails(productData)
            }

            override fun onPurchaseCanceled() {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onPurchaseFailed(resultCode: Int) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }
        }
        return BillingManagerAmazon(activity, billingUpdatesListener, query)
    }

    private fun registerInventory(purchases: MutableList<Receipt>) {
        val receipt = findHighestValidPurchase(purchases)
        receipt?.let {
            if (it.isCanceled) {
                CrashHandler.reportWithTag("Found canceled receipt", LicenceHandler.TAG)
            } else {
                handlePurchase(it.sku, it.receiptId)
            }
        } ?: kotlin.run { maybeCancel() }
    }

    private fun findHighestValidPurchase(purchases: List<Receipt>) =
            purchases.filter { extractLicenceStatusFromSku(it.sku) != null }
                    .maxBy { extractLicenceStatusFromSku(it.sku)?.ordinal ?: 0}

    private fun storeSkuDetails(productData: MutableMap<String, Product>) {
        val editor = pricesPrefs.edit()
        allSkus.forEach { sku ->
            val product = productData[sku]
            product?.let {
                log().d("Sku: %s", it.toString())
                editor.putString(sku, it.price)
            } ?: kotlin.run {
                log().d("Did not find details for %s", sku)
            }
        }
        editor.apply()
    }

    override fun launchPurchase(aPackage: Package, shouldReplaceExisting: Boolean, billingManager: BillingManager) {
        (billingManager as? BillingManagerAmazon)?.initiatePurchaseFlow(getSkuForPackage(aPackage))
    }


    override fun getProPackages() = arrayOf(Package.Professional_Amazon)

    override fun getProfessionalPriceShortInfo(): String {
        var priceInfo = joinPriceInfos(Package.Professional_1, Package.Professional_12)
        if (licenceStatus === LicenceStatus.EXTENDED) {
            val regularPrice = pricesPrefs.getString(Config.SKU_PROFESSIONAL_12, null)
            if (regularPrice != null) {
                priceInfo += ". " + context.getString(R.string.extended_upgrade_goodie_subscription_amazon, 15, regularPrice)
            }
        }
        return priceInfo
    }

    override fun getDisplayPriceForPackage(aPackage: Package) = pricesPrefs.getString(getSkuForPackage(aPackage), null)

    override fun getProPackagesForExtendOrSwitch() = null

    override fun getProLicenceAction(context: Context?) = ""

}