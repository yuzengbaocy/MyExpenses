package org.totschnig.myexpenses.activity

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.CallSuper
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.theartofdev.edmodo.cropper.CropImage
import com.vmadalin.easypermissions.EasyPermissions
import com.vmadalin.easypermissions.dialogs.SettingsDialog
import icepick.State
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.dialog.MessageDialogFragment
import org.totschnig.myexpenses.dialog.VersionDialogFragment
import org.totschnig.myexpenses.feature.Feature
import org.totschnig.myexpenses.model.Account
import org.totschnig.myexpenses.model.Transaction
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.ui.SnackbarAction
import org.totschnig.myexpenses.util.PermissionHelper
import org.totschnig.myexpenses.util.UiUtils
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.locale.UserLocaleProvider
import org.totschnig.myexpenses.util.tracking.Tracker
import org.totschnig.myexpenses.viewmodel.FeatureViewModel
import org.totschnig.myexpenses.viewmodel.OcrViewModel
import org.totschnig.myexpenses.viewmodel.data.EventObserver
import timber.log.Timber
import javax.inject.Inject

abstract class BaseActivity : AppCompatActivity(), MessageDialogFragment.MessageDialogListener, EasyPermissions.PermissionCallbacks {
    private var snackbar: Snackbar? = null
    private val downloadReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            onDownloadComplete()
        }
    }

    fun copyToClipboard(text: String) {
        showSnackbar(try {
            ContextCompat.getSystemService(this, ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(null, text))
            "${getString(R.string.toast_text_copied)}: $text"
        } catch (e: RuntimeException) {
            Timber.e(e)
            e.message ?: "Error"
        })
    }


    fun startActivity(intent: Intent, notAvailableMessage: Int, forResultRequestCode: Int? = null) {
        try {
            if (forResultRequestCode != null)
                startActivityForResult(intent, forResultRequestCode)
            else
                startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showSnackbar(notAvailableMessage)
        }
    }

    private fun onDownloadComplete() {
        downloadPending?.let {
            showSnackbar(getString(R.string.download_completed, it))
        }
        downloadPending = null
    }

    @State
    @JvmField
    var downloadPending: String? = null

    @Inject
    lateinit var prefHandler: PrefHandler

    @Inject
    lateinit var tracker: Tracker

    @Inject
    lateinit var userLocaleProvider: UserLocaleProvider

    @Inject
    lateinit var crashHandler: CrashHandler

    lateinit var ocrViewModel: OcrViewModel
    lateinit var featureViewModel: FeatureViewModel
    private var helpVariant: Enum<*>? = null


    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase)
        injectDependencies()
    }

    protected open fun injectDependencies() {
        (applicationContext as MyApplication).appComponent.inject(this)
    }

    open fun onFeatureAvailable(feature: Feature) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        val viewModelProvider = ViewModelProvider(this)
        ocrViewModel = viewModelProvider[OcrViewModel::class.java]
        featureViewModel = viewModelProvider[FeatureViewModel::class.java]
        with((applicationContext as MyApplication).appComponent) {
            inject(ocrViewModel)
            inject(featureViewModel)
        }
        featureViewModel.getFeatureState().observe(this, EventObserver { featureState ->
            when (featureState) {
                is FeatureViewModel.FeatureState.FeatureLoading -> showSnackbar(
                    getString(
                        R.string.feature_download_requested,
                        getString(featureState.feature.labelResId)
                    )
                )
                is FeatureViewModel.FeatureState.FeatureAvailable -> {
                    Feature.values().find { featureState.modules.contains(it.moduleName) }?.let {
                        showSnackbar(
                            getString(
                                R.string.feature_downloaded,
                                getString(it.labelResId)
                            )
                        )
                        //after the dynamic feature module has been installed, we need to check if data needed by the module (e.g. Tesseract) has been downloaded
                        if (!featureViewModel.isFeatureAvailable(this, it)) {
                            featureViewModel.requestFeature(this, it)
                        } else {
                            onFeatureAvailable(it)
                        }
                    }
                }
                is FeatureViewModel.FeatureState.Error -> {
                    with(featureState.throwable) {
                        CrashHandler.report(this)
                        message?.let { showSnackbar(it) }
                    }
                }
                is FeatureViewModel.FeatureState.LanguageLoading -> showSnackbar(
                    getString(
                        R.string.language_download_requested,
                        featureState.language
                    )
                )
                is FeatureViewModel.FeatureState.LanguageAvailable -> {
                    rebuildDbConstants()
                    recreate()
                }
            }
        })
        super.onCreate(savedInstanceState)
        tracker.init(this)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        featureViewModel.registerCallback()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: IllegalArgumentException) {
            //Mainly hits Android 4, 5 and 6, no need to report
            //CrashHandler.report(e)
        }
        featureViewModel.unregisterCallback()
    }

    fun setTrackingEnabled(enabled: Boolean) {
        tracker.setEnabled(enabled)
    }

    fun logEvent(event: String?, params: Bundle?) {
        tracker.logEvent(event, params)
    }

    fun trackCommand(command: Int) {
        try {
            resources.getResourceName(command)
        } catch (e: Resources.NotFoundException) {
            null
        }?.let { fullResourceName ->
            logEvent(Tracker.EVENT_DISPATCH_COMMAND, Bundle().apply {
                putString(
                    Tracker.EVENT_PARAM_ITEM_ID,
                    fullResourceName.substring(fullResourceName.indexOf('/') + 1)
                )
            })
        }
    }

    @CallSuper
    override fun dispatchCommand(command: Int, tag: Any?): Boolean {
        trackCommand(command)
        if (command == R.id.TESSERACT_DOWNLOAD_COMMAND) {
            ocrViewModel.downloadTessData().observe(this) {
                downloadPending = it
            }
            return true
        }
        return false
    }

    fun processImageCaptureError(resultCode: Int, activityResult: CropImage.ActivityResult?) {
        if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
            val throwable = activityResult?.error ?: Throwable("ERROR")
            CrashHandler.report(throwable)
            showSnackbar(if (throwable is ActivityNotFoundException) getString(R.string.image_capture_not_installed) else throwable.message ?: "ERROR")
        }
    }

    fun showDismissibleSnackbar(message: Int) {
        showDismissibleSnackbar(getText(message))
    }

    @JvmOverloads
    fun showDismissibleSnackbar(message: CharSequence, callback: Snackbar.Callback? = null) {
        showSnackbar(
            message, Snackbar.LENGTH_INDEFINITE,
            SnackbarAction(R.string.dialog_dismiss) { snackbar?.dismiss() }, callback
        )
    }

    fun showSnackbarIndefinite(message: Int) {
        showSnackbar(message, Snackbar.LENGTH_INDEFINITE)
    }

    @JvmOverloads
    fun showSnackbar(message: Int, duration: Int = Snackbar.LENGTH_LONG) {
        showSnackbar(getText(message), duration)
    }

    @JvmOverloads
    fun showSnackbar(
        message: CharSequence,
        duration: Int = Snackbar.LENGTH_LONG,
        snackbarAction: SnackbarAction? = null,
        callback: Snackbar.Callback? = null
    ) {
        findViewById<View>(getSnackbarContainerId())?.let {
            showSnackbar(message, duration, snackbarAction, callback, it)
        } ?: showSnackBarFallBack(message)
    }

    private fun showSnackBarFallBack(message: CharSequence) {
        reportMissingSnackbarContainer()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    open fun reportMissingSnackbarContainer() {
        CrashHandler.report(String.format("Class %s is unable to display snackbar", javaClass))
    }

    fun showProgressSnackBar(message: CharSequence, total: Int = 0, progress: Int = 0) {
        findViewById<View>(getSnackbarContainerId())?.let {
            val displayMessage = if (total > 0) "$message ($progress/$total)" else message
            if (progress > 0) {
                snackbar?.setText(displayMessage)
            } else {
                snackbar = Snackbar.make(it, displayMessage, Snackbar.LENGTH_INDEFINITE).apply {
                    (view.findViewById<View>(com.google.android.material.R.id.snackbar_text).parent as ViewGroup)
                        .addView(
                            ProgressBar(
                                ContextThemeWrapper(
                                    this@BaseActivity,
                                    R.style.SnackBarTheme
                                )
                            )
                        )
                    show()
                }
            }
        } ?: showSnackBarFallBack(message)
    }

    fun updateSnackBar(message: CharSequence) {
        snackbar?.setText(message) ?: run {
            CrashHandler.report("updateSnackBar called without snackbar being instantiated")
        }
    }

    fun showSnackbar(
        message: CharSequence, duration: Int, snackbarAction: SnackbarAction?,
        callback: Snackbar.Callback?, container: View
    ) {
        snackbar = Snackbar.make(container, message, duration).apply {
            UiUtils.increaseSnackbarMaxLines(this)
            if (snackbarAction != null) {
                setAction(snackbarAction.resId, snackbarAction.listener)
            }
            if (callback != null) {
                addCallback(callback)
            }
            show()
        }

    }

    fun dismissSnackbar() {
        snackbar?.dismiss()
        snackbar = null
    }

    @IdRes
    protected open fun getSnackbarContainerId(): Int {
        return R.id.fragment_container
    }

    private fun offerTessDataDownload() {
        ocrViewModel.offerTessDataDownload(this)
    }

    fun checkTessDataDownload() {
        ocrViewModel.tessDataExists().observe(this) {
            if (!it)
                offerTessDataDownload()
        }
    }

    fun startActionView(uri: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(uri)
            })
        } catch (e: ActivityNotFoundException) {
            showSnackbar("No activity found for opening $uri", Snackbar.LENGTH_LONG, null)
        }
    }

    @JvmOverloads
    open fun showMessage(
        message: CharSequence,
        positive: MessageDialogFragment.Button = MessageDialogFragment.okButton(),
        neutral: MessageDialogFragment.Button? = null,
        negative: MessageDialogFragment.Button? = null,
        cancellable: Boolean = true
    ) {
        lifecycleScope.launchWhenResumed {
            MessageDialogFragment.newInstance(null, message, positive, neutral, negative).apply {
                isCancelable = cancellable
            }.show(supportFragmentManager, "MESSAGE")
        }
    }

    fun showVersionDialog(prev_version: Int, showImportantUpgradeInfo: Boolean) {
        lifecycleScope.launchWhenResumed {
            VersionDialogFragment.newInstance(prev_version, showImportantUpgradeInfo)
                .show(supportFragmentManager, "VERSION_INFO")
        }
    }

    fun unencryptedBackupWarning() = getString(
        R.string.warning_unencrypted_backup,
        getString(R.string.pref_security_export_passphrase_title)
    )

    override fun onMessageDialogDismissOrCancel() {}

    fun rebuildDbConstants() {
        DatabaseConstants.buildLocalized(userLocaleProvider.getUserPreferredLocale())
        Transaction.buildProjection(this)
        Account.buildProjection()
    }

    fun showMessage(resId: Int) {
        showMessage(getString(resId))
    }

    fun showDeleteFailureFeedback(message: String? = null) {
        showDismissibleSnackbar("There was an error deleting the object${message?.let { " ($it)" } ?: ""}. Please contact support@myexenses.mobi !")
    }

    fun getHelpVariant() = helpVariant?.name

    fun setHelpVariant(helpVariant: Enum<*>, addBreadCrumb: Boolean = false) {
        this.helpVariant = helpVariant
        if (addBreadCrumb) {
            crashHandler.addBreadcrumb(helpVariant.toString())
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    open fun requestPermission(permissionGroup: PermissionHelper.PermissionGroup) {
        EasyPermissions.requestPermissions(
            host = this,
            rationale = permissionGroup.permissionRequestRationale(this),
            requestCode = permissionGroup.requestCode,
            perms = permissionGroup.androidPermissions
        )
    }

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            SettingsDialog.Builder(this)
                .title(R.string.permissions_label)
                .rationale(PermissionHelper.PermissionGroup.fromRequestCode(requestCode).permissionRequestRationale(this))
                .build().show()
        }
    }
}