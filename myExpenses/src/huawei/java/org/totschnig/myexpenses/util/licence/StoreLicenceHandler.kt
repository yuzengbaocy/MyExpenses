package org.totschnig.myexpenses.util.licence

import com.google.android.vending.licensing.PreferenceObfuscator
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

class StoreLicenceHandler(application: MyApplication, preferenceObfuscator: PreferenceObfuscator, crashHandler: CrashHandler):
        LicenceHandler(application, preferenceObfuscator, crashHandler)