package org.totschnig.myexpenses.util.locale

import android.app.Activity
import android.content.Context
import androidx.annotation.Keep
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.MyApplication.DEFAULT_LANGUAGE
import org.totschnig.myexpenses.activity.BaseActivity
import org.totschnig.myexpenses.feature.Callback
import org.totschnig.myexpenses.feature.FeatureManager
import org.totschnig.myexpenses.feature.OCR_MODULE
import org.totschnig.myexpenses.feature.getUserConfiguredEngine
import org.totschnig.myexpenses.preference.PrefHandler
import timber.log.Timber
import java.util.*

@Suppress("unused")
@Keep
class PlatformSplitManager(private val userLocaleProvider: UserLocaleProvider, private val prefHandler: PrefHandler) : FeatureManager() {
    private lateinit var manager: SplitInstallManager
    private var mySessionId = 0
    var listener: SplitInstallStateUpdatedListener? = null

    override fun initApplication(application: MyApplication) {
        super.initApplication(application)
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
            super.requestLocale(context)
        } else {
            val userPreferredLocale = userLocaleProvider.getUserPreferredLocale()
            if (userPreferredLocale.language.equals("en") ||
                    manager.installedLanguages.contains(userPreferredLocale.language)) {
                Timber.i("Already installed")
                callback?.onLanguageAvailable()
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
        super.registerCallback(callback)
        listener = SplitInstallStateUpdatedListener { state ->
            if (state.sessionId() == mySessionId) {
                if (state.status() == SplitInstallSessionStatus.INSTALLED) {
                    state.moduleNames()
                    this.callback?.onFeatureAvailable()
                }
            }
        }.also { manager.registerListener(it) }
    }

    override fun unregister() {
        super.unregister()
        listener?.let { manager.unregisterListener(it) }
    }

    override fun isFeatureInstalled(feature: String, context: Context) =
             areModulesInstalled(feature, context) && super.isFeatureInstalled(feature, context)

    private fun areModulesInstalled(feature: String, context: Context) = isModuleInstalled(feature) &&
            subModule(feature, context)?.let { isModuleInstalled(it) } ?: true

    private fun isModuleInstalled(module: String) = manager.installedModules.contains(module)

    override fun requestFeature(feature: String, activity: BaseActivity) {
        if (areModulesInstalled(feature, activity)) {
            super.requestFeature(feature, activity)
        } else {
            callback?.onAsyncStartedFeature(feature)
            val request = SplitInstallRequest
                    .newBuilder()
                    .addModule(feature)
                    .apply {
                        subModule(feature, activity)?.let { addModule(it) }
                    }
                    .build()

            manager.startInstall(request)
                    .addOnSuccessListener { sessionId -> mySessionId = sessionId }
                    .addOnFailureListener { exception ->
                        callback?.onError(exception)
                    }
        }
    }

    private fun subModule(feature: String, context: Context) =
            if (feature == OCR_MODULE) getUserConfiguredEngine(context, prefHandler) else null

    override fun installedFeatures() = manager.installedModules

    override fun installedLanguages() = manager.installedLanguages

    override fun uninstallFeatures(features: Set<String>) {
        manager.deferredUninstall(features.toList())
    }

    override fun uninstallLanguages(languages: Set<String>) {
        manager.deferredLanguageUninstall(languages.map { language -> Locale(language) })
    }

    override fun allowsUninstall() = true
}