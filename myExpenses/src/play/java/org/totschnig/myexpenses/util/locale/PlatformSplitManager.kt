package org.totschnig.myexpenses.util.locale

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import org.totschnig.myexpenses.MyApplication.DEFAULT_LANGUAGE
import org.totschnig.myexpenses.BuildConfig
import org.totschnig.myexpenses.feature.Callback
import org.totschnig.myexpenses.feature.FeatureManager
import timber.log.Timber

const val OCR_MODULE = "ocr"

@Suppress("unused")
class PlatformSplitManager(private var userLocaleProvider: UserLocaleProvider) : FeatureManager {
    private lateinit var manager: SplitInstallManager
    private var mySessionId = 0
    var listener: SplitInstallStateUpdatedListener? = null
    var callback: Callback? = null
    override fun initApplication(application: Application) {
        SplitCompat.install(application)
        manager = SplitInstallManagerFactory.create(application)
    }

    override fun initActivity(activity: Activity) {
        SplitCompat.installActivity(activity)
    }

    override fun requestLocale(context: Context) {
        val userLanguage = userLocaleProvider.getPreferredLanguage()
        if (userLanguage == DEFAULT_LANGUAGE) {
            Timber.i("userLanguage == DEFAULT_LANGUAGE")
            callback?.onAvailable()
        } else {
            val installedLanguages = manager.installedLanguages
            Timber.d("Downloaded languages: %s", installedLanguages.joinToString())
            val userPreferedLocale = userLocaleProvider.getUserPreferredLocale()
            if (installedLanguages.contains(userPreferedLocale.language)) {
                Timber.i("Already installed")
                callback?.onAvailable()
            } else {
                callback?.onAsyncStarted(userPreferedLocale.displayLanguage)
                val request = SplitInstallRequest.newBuilder()
                        .addLanguage(userPreferedLocale)
                        .build()
                manager.startInstall(request)
                        .addOnSuccessListener { sessionId -> mySessionId = sessionId }
                        .addOnFailureListener { exception -> callback?.onError(exception) }

            }
        }
    }

    override fun registerCallback(callback: Callback) {
        this.callback = callback
        listener = SplitInstallStateUpdatedListener { state ->
            if (state.sessionId() == mySessionId) {
                if (state.status() == SplitInstallSessionStatus.INSTALLED) {
                    this.callback?.onAvailable()
                }
            }
        }.also { manager.registerListener(it) }
    }

    override fun unregister() {
        callback = null
        listener?.let { manager.unregisterListener(it) }
    }

    override fun isFeatureInstalled(feature: FeatureManager.Feature, context: Context) =
            when {
                BuildConfig.DEBUG -> true
                feature == FeatureManager.Feature.OCR -> manager.installedModules.contains(OCR_MODULE)
                else -> false
            }

    override fun requestFeature(feature: FeatureManager.Feature, fragmentActivity: FragmentActivity) {
        if (feature == FeatureManager.Feature.OCR) {
            callback?.onAsyncStarted(feature)
            val request = SplitInstallRequest
                    .newBuilder()
                    .addModule(OCR_MODULE)
                    .build()

            manager.startInstall(request)
                    .addOnSuccessListener { sessionId -> mySessionId = sessionId }
                    .addOnFailureListener { exception ->
                        callback?.onError(exception)
                    }
        }
    }
}