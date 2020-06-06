package org.totschnig.myexpenses.util.licence

import android.content.Context
import com.google.android.vending.licensing.PreferenceObfuscator
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.contrib.Config
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

class StoreLicenceHandler(context: MyApplication, preferenceObfuscator: PreferenceObfuscator, crashHandler: CrashHandler):
        PlayStoreLicenceHandler(context, preferenceObfuscator, crashHandler) {
    override fun getProPackages(): Array<Package> {
        return arrayOf(Package.Professional_1, Package.Professional_12)
    }

    override fun getExtendedUpgradeGoodieMessage(selectedPackage: Package): String? {
        if (selectedPackage == Package.Professional_12) {
            val pricesPrefsString = pricesPrefs.getString(Config.SKU_EXTENDED2PROFESSIONAL_12, null)
            if (pricesPrefsString != null) {
                return context.getString(R.string.extended_upgrade_goodie_subscription, pricesPrefsString)
            }
        }
        return null
    }

    override fun getProPackagesForExtendOrSwitch(): Array<Package>? {
        val switchPackage = getPackageForSwitch()
        return if (switchPackage == null) null else arrayOf(switchPackage)
    }

    private fun getPackageForSwitch(): Package? {
        val currentSubscription = licenseStatusPrefs.getString(KEY_CURRENT_SUBSCRIPTION, null)
                ?: return null
        when (currentSubscription) {
            Config.SKU_PROFESSIONAL_1 -> return Package.Professional_12
            Config.SKU_PROFESSIONAL_12, Config.SKU_EXTENDED2PROFESSIONAL_12 -> return Package.Professional_1
            else -> return null
        }
    }

    override fun getExtendOrSwitchMessage(aPackage: Package): String {
        val recurrenceResId: Int
        when (aPackage) {
            Package.Professional_12 -> recurrenceResId = R.string.switch_to_yearly
            Package.Professional_1 -> recurrenceResId = R.string.switch_to_monthly
            else -> return ""
        }
        return context.getString(recurrenceResId)
    }

    override fun getProLicenceAction(context: Context) =
            getPackageForSwitch()?.let { getExtendOrSwitchMessage(it) } ?: ""
}