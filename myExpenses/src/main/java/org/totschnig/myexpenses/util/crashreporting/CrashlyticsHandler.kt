package org.totschnig.myexpenses.util.crashreporting

import android.content.Context
import android.os.Handler
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.preference.PrefKey
import timber.log.Timber

class CrashlyticsHandler(val prefHandler: PrefHandler) : CrashHandler() {
    private var crashReportingTree: CrashReportingTree? = null

    override fun onAttachBaseContext(application: MyApplication) {}
    public override fun setupLoggingDo(context: Context) {
        if (crashReportingTree == null) {
            crashReportingTree = CrashReportingTree().also {
                Timber.plant(it)
            }
        }
        Handler().apply {
            postDelayed({
                instance?.setCrashlyticsCollectionEnabled(true)
                setKeys(context)
            }, 5000)
        }
    }

    override fun setKeys(context: Context) {
        super.setKeys(context)
        setUserEmail(prefHandler.getString(PrefKey.CRASHREPORT_USEREMAIL, null))
    }

    override fun putCustomData(key: String, value: String?) {
        value?.let { instance?.setCustomKey(key, it) }
    }

    private class CrashReportingTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == Log.ERROR) {
                instance?.recordException(t ?: Exception(message))
            } else {
                instance?.log(message)
            }
        }

        override fun isLoggable(tag: String?, priority: Int): Boolean {
            return priority == Log.ERROR || priority == Log.WARN
        }
    }

    override fun initProcess(context: Context, syncService: Boolean) {
        if (syncService) {
            FirebaseApp.initializeApp(context)
        }
    }
    companion object {
        private val instance: FirebaseCrashlytics?
            get() = try {
                FirebaseCrashlytics.getInstance()
            } catch (e: IllegalStateException) {
                null
            }
    }
}