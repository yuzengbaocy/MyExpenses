package org.totschnig.myexpenses.util.licence

import android.content.Context
import com.google.android.vending.licensing.PreferenceObfuscator
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import java.util.*

class StoreLicenceHandler(application: MyApplication, preferenceObfuscator: PreferenceObfuscator, crashHandler: CrashHandler):
        PlayStoreLicenceHandler(application, preferenceObfuscator, crashHandler) {
    override fun needsKeyEntry() = true

    override fun doesUseIAP() = false

    override fun getFormattedPrice(aPackage: Package) = getFormattedPriceWithExtra(aPackage, false)

    override fun getProLicenceStatus(context: Context) = getProValidUntil(context)

}