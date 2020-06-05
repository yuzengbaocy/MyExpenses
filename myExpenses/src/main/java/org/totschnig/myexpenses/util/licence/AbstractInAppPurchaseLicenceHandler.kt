package org.totschnig.myexpenses.util.licence

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.vending.licensing.PreferenceObfuscator
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.contrib.Config
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.util.crashreporting.CrashHandler


abstract class AbstractInAppPurchaseLicenceHandler(context: MyApplication, preferenceObfuscator: PreferenceObfuscator, crashHandler: CrashHandler) : ContribStatusLicenceHandler(context, preferenceObfuscator, crashHandler) {
    private val KEY_ORDER_ID = "order_id"
    private val REFUND_WINDOW = 172800000L
    private val STATUS_DISABLED = 0
    private val PRICES_PREFS_FILE = "license_prices"
    val pricesPrefs: SharedPreferences

    init {
        pricesPrefs = context.getSharedPreferences(PRICES_PREFS_FILE, Context.MODE_PRIVATE)
    }

    override fun getLegacyStatus() = STATUS_ENABLED_LEGACY_SECOND

    override fun init() {
        super.init()
        d("init")
        readContribStatusFromPrefs()
    }
    override fun getFormattedPrice(aPackage: Package): String? {
        val pricesPrefsString = getDisplayPriceForPackage(aPackage)
        return if (pricesPrefsString != null)
            aPackage.getFormattedPrice(context, pricesPrefsString, false)
        else
            null
    }

    abstract protected fun getDisplayPriceForPackage(aPackage: Package): String?

    /**
     * @param sku
     * @return which LicenceStatus an sku gives access to
     */
    fun extractLicenceStatusFromSku(sku: String): LicenceStatus? {
        if (sku.contains(LicenceStatus.PROFESSIONAL.toSkuType())) return LicenceStatus.PROFESSIONAL
        if (sku.contains(LicenceStatus.EXTENDED.toSkuType())) return LicenceStatus.EXTENDED
        return if (sku.contains(LicenceStatus.CONTRIB.toSkuType())) LicenceStatus.CONTRIB else null
    }

    /**
     * After 2 days, if purchase cannot be verified, we set back
     */
    fun maybeCancel() {
        if (contribStatus != STATUS_ENABLED_LEGACY_SECOND) {
            val timestamp = java.lang.Long.parseLong(licenseStatusPrefs.getString(
                    PrefKey.LICENSE_INITIAL_TIMESTAMP.key, "0"))
            val now = System.currentTimeMillis()
            val timeSincePurchase = now - timestamp
            if (timeSincePurchase > REFUND_WINDOW) {
                cancel()
            }
        }
    }

    fun cancel() {
        updateContribStatus(STATUS_DISABLED)
    }

    fun handlePurchase(sku: String, orderId: String): LicenceStatus? {
        licenseStatusPrefs.putString(KEY_ORDER_ID, orderId)
        return extractLicenceStatusFromSku(sku).also {
            when (it) {
                LicenceStatus.CONTRIB -> registerPurchase(false)
                LicenceStatus.EXTENDED -> registerPurchase(true)
                LicenceStatus.PROFESSIONAL -> registerSubscription(sku)
            }
        }
    }

    /**
     * @param extended if true user has purchase extended licence
     */
    fun registerPurchase(extended: Boolean) {
        var status = if (extended) STATUS_EXTENDED_TEMPORARY else STATUS_ENABLED_TEMPORARY
        val timestamp = java.lang.Long.parseLong(licenseStatusPrefs.getString(
                PrefKey.LICENSE_INITIAL_TIMESTAMP.key, "0"))
        val now = System.currentTimeMillis()
        if (timestamp == 0L) {
            licenseStatusPrefs.putString(PrefKey.LICENSE_INITIAL_TIMESTAMP.key,
                    now.toString())
        } else {
            val timeSincePurchase = now - timestamp
            LicenceHandler.log().d("time since initial check : %d", timeSincePurchase)
            //give user 2 days to request refund
            if (timeSincePurchase > REFUND_WINDOW) {
                status = if (extended) STATUS_EXTENDED_PERMANENT else STATUS_ENABLED_PERMANENT
            }
        }
        updateContribStatus(status)
    }

    fun registerSubscription(sku: String) {
        licenseStatusPrefs.putString(KEY_CURRENT_SUBSCRIPTION, sku)
        updateContribStatus(STATUS_PROFESSIONAL)
    }

    fun getSkuForPackage(aPackage: Package): String {
        val hasExtended = licenceStatus != null && licenceStatus == LicenceStatus.EXTENDED
        return when (aPackage) {
            Package.Contrib -> Config.SKU_PREMIUM
            Package.Upgrade -> Config.SKU_PREMIUM2EXTENDED
            Package.Extended -> Config.SKU_EXTENDED
            Package.Professional_1 -> Config.SKU_PROFESSIONAL_1
            Package.Professional_12 -> if (hasExtended) Config.SKU_EXTENDED2PROFESSIONAL_12 else Config.SKU_PROFESSIONAL_12
            Package.Professional_Amazon -> if (hasExtended) Config.SKU_EXTENDED2PROFESSIONAL_PARENT else Config.SKU_PROFESSIONAL_PARENT
            else -> throw IllegalStateException()
        }
    }

    override fun getProLicenceStatus(context: Context) =
            when (licenseStatusPrefs.getString(KEY_CURRENT_SUBSCRIPTION, "")) {
                Config.SKU_PROFESSIONAL_1, Config.SKU_EXTENDED2PROFESSIONAL_1 -> R.string.monthly
                Config.SKU_PROFESSIONAL_12, Config.SKU_EXTENDED2PROFESSIONAL_12 -> R.string.yearly_plain
                else -> 0
            }.takeIf { it != 0 }?.let { context.getString(it) } ?: ""

    /**
     * do not call on main thread
     */
    override fun buildRoadmapVoteKey(): String {
        return if (isProfessionalEnabled) {
            purchaseExtraInfo ?: super.buildRoadmapVoteKey()
        } else {
            try {
                AdvertisingIdClient.getAdvertisingIdInfo(context).id
            } catch (e: Exception) {
                super.buildRoadmapVoteKey()
            }
        }
    }

    override fun getPurchaseExtraInfo(): String? {
        return licenseStatusPrefs.getString(KEY_ORDER_ID, null)
    }

    override fun doesUseIAP() = true

    override fun needsKeyEntry() = false

    companion object {
        const val KEY_CURRENT_SUBSCRIPTION = "current_subscription"
    }
}