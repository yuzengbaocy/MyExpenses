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
import java.util.*

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
            val userPreferredLocale = userLocaleProvider.getUserPreferredLocale()
            if (userPreferredLocale.language.equals("en") ||
                    manager.installedLanguages.contains(userPreferredLocale.language)) {
                Timber.i("Already installed")
                callback?.onAvailable()
            } else {
                callback?.onAsyncStartedLanguage(userPreferredLocale.displayLanguage)
                val request = SplitInstallRequest.newBuilder()
                        .addLanguage(userPreferredLocale)
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

    override fun isFeatureInstalled(feature: String, context: Context) =
            when {
                BuildConfig.DEBUG -> true
                else -> manager.installedModules.contains(feature)
            }

    override fun requestFeature(feature: String, fragmentActivity: FragmentActivity) {
        callback?.onAsyncStartedFeature(feature)
        val request = SplitInstallRequest
                .newBuilder()
                .addModule(feature)
                .build()

        manager.startInstall(request)
                .addOnSuccessListener { sessionId -> mySessionId = sessionId }
                .addOnFailureListener { exception ->
                    callback?.onError(exception)
                }
    }

    override fun installedFeatures() = manager.installedModules

    override fun installedLanguages() = manager.installedLanguages

    override fun uninstallFeatures(features: Set<String>) {
        manager.deferredUninstall(features.toList())
    }

    override fun uninstallLanguages(languages: Set<String>) {
        manager.deferredLanguageUninstall(languages.map { language ->  Locale(language) })
    }

    override fun allowsUninstall() = true
}