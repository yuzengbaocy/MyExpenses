package org.totschnig.myexpenses.util.locale

import android.app.Activity
import android.app.Application
import android.content.Context
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import org.totschnig.myexpenses.MyApplication.DEFAULT_LANGUAGE
import timber.log.Timber


@Suppress("unused")
class PlatformLocaleManager(private var userLocaleProvider: UserLocaleProvider) : LocaleManager {
    private lateinit var manager: SplitInstallManager
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
                        .addOnFailureListener { exception -> callback?.onError(exception) }

            }
        }
    }

    override fun onResume(callback: Callback) {
        this.callback = callback
        listener = SplitInstallStateUpdatedListener { state ->
            if (state.status() == SplitInstallSessionStatus.INSTALLED) {
                this.callback?.onAvailable()
            }
        }.also { manager.registerListener(it) }
    }

    override fun onPause() {
        callback = null
        listener?.let { manager.unregisterListener(it) }
    }
}