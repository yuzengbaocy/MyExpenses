package org.totschnig.myexpenses.activity

import android.accounts.AccountManager
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.SubMenu
import androidx.lifecycle.ViewModelProvider
import com.annimon.stream.Exceptional
import com.google.android.material.snackbar.Snackbar
import eltos.simpledialogfragment.SimpleDialog.OnDialogResultListener
import eltos.simpledialogfragment.form.Input
import eltos.simpledialogfragment.form.SimpleFormDialog
import icepick.State
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.dialog.EditTextDialog
import org.totschnig.myexpenses.dialog.EditTextDialog.EditTextDialogListener
import org.totschnig.myexpenses.dialog.ProgressDialogFragment
import org.totschnig.myexpenses.dialog.SetupWebdavDialogFragment
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.sync.GenericAccountService
import org.totschnig.myexpenses.sync.ServiceLoader
import org.totschnig.myexpenses.sync.SyncBackendProvider.ResolvableSetupException
import org.totschnig.myexpenses.sync.SyncBackendProviderFactory
import org.totschnig.myexpenses.sync.WebDavBackendProvider
import org.totschnig.myexpenses.sync.WebDavBackendProviderFactory
import org.totschnig.myexpenses.sync.json.AccountMetaData
import org.totschnig.myexpenses.task.TaskExecutionFragment
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.viewmodel.SyncViewModel
import org.totschnig.myexpenses.viewmodel.SyncViewModel.Companion.KEY_RETURN_REMOTE_DATA_LIST
import java.io.File

abstract class SyncBackendSetupActivity : ProtectedFragmentActivity(), EditTextDialogListener,
    OnDialogResultListener {

    protected lateinit var backendProviders: List<SyncBackendProviderFactory>
    protected lateinit var viewModel: SyncViewModel
    private var isResumed = false
    private var setupPending = false

    @JvmField
    @State
    var selectedFactoryId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backendProviders = ServiceLoader.load(this)
        viewModel = ViewModelProvider(this).get(SyncViewModel::class.java)
        (applicationContext as MyApplication).appComponent.inject(viewModel)
    }

    //LocalFileBackend
    override fun onFinishEditDialog(args: Bundle) {
        val filePath = args.getString(EditTextDialog.KEY_RESULT)!!
        val baseFolder = File(filePath)
        if (!baseFolder.isDirectory) {
            showSnackbar("No directory $filePath", Snackbar.LENGTH_SHORT)
        } else {
            val accountName =
                getSyncBackendProviderFactoryByIdOrThrow(R.id.SYNC_BACKEND_LOCAL).buildAccountName(
                    filePath
                )
            val bundle = Bundle(1)
            bundle.putString(GenericAccountService.KEY_SYNC_PROVIDER_URL, filePath)
            createAccount(accountName, null, null, bundle)
        }
    }

    //WebDav
    fun onFinishWebDavSetup(data: Bundle) {
        val userName = data.getString(AccountManager.KEY_ACCOUNT_NAME)
        val password = data.getString(AccountManager.KEY_PASSWORD)
        val url = data.getString(GenericAccountService.KEY_SYNC_PROVIDER_URL)
        val certificate = data.getString(WebDavBackendProvider.KEY_WEB_DAV_CERTIFICATE)
        val accountName =
            getSyncBackendProviderFactoryByIdOrThrow(R.id.SYNC_BACKEND_WEBDAV).buildAccountName(
                url!!
            )
        val bundle = Bundle()
        bundle.putString(GenericAccountService.KEY_SYNC_PROVIDER_URL, url)
        bundle.putString(GenericAccountService.KEY_SYNC_PROVIDER_USERNAME, userName)
        if (certificate != null) {
            bundle.putString(WebDavBackendProvider.KEY_WEB_DAV_CERTIFICATE, certificate)
        }
        if (data.getBoolean(WebDavBackendProvider.KEY_WEB_DAV_FALLBACK_TO_CLASS1)) {
            bundle.putString(WebDavBackendProvider.KEY_WEB_DAV_FALLBACK_TO_CLASS1, "1")
        }
        if (prefHandler.getBoolean(PrefKey.WEBDAV_ALLOW_UNVERIFIED_HOST, false)) {
            bundle.putString(WebDavBackendProvider.KEY_ALLOW_UNVERIFIED, "true")
        }
        createAccount(accountName, password, null, bundle)
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        if (setupPending) {
            startSetupDo()
            setupPending = false
        }
    }

    override fun onPause() {
        super.onPause()
        isResumed = false
    }

    fun startSetup(itemId: Int) {
        selectedFactoryId = itemId
        if (isResumed) {
            startSetupDo()
        } else {
            setupPending = true
        }
    }

    private fun startSetupDo() {
        val syncBackendProviderFactory = getSyncBackendProviderFactoryById(selectedFactoryId)
        syncBackendProviderFactory?.startSetup(this)
    }

    //Google Drive & Dropbox
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (requestCode == SYNC_BACKEND_SETUP_REQUEST && resultCode == RESULT_OK && intent != null) {
            val accountName = getSyncBackendProviderFactoryByIdOrThrow(
                intent.getIntExtra(
                    KEY_SYNC_PROVIDER_ID, 0
                )
            )
                .buildAccountName(intent.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)!!)
            createAccount(
                accountName,
                null,
                intent.getStringExtra(AccountManager.KEY_AUTHTOKEN),
                intent.getBundleExtra(AccountManager.KEY_USERDATA)
            )
        }
        if (requestCode == REQUEST_CODE_RESOLUTION) {
            showSnackbar("Please try again")
        }
    }

    private fun createAccount(
        accountName: String?,
        password: String?,
        authToken: String?,
        bundle: Bundle?
    ) {
        val args = Bundle()
        args.putString(AccountManager.KEY_ACCOUNT_NAME, accountName)
        args.putString(AccountManager.KEY_PASSWORD, password)
        args.putString(AccountManager.KEY_AUTHTOKEN, authToken)
        args.putParcelable(AccountManager.KEY_USERDATA, bundle)
        args.putBoolean(
            KEY_RETURN_REMOTE_DATA_LIST,
            createAccountTaskShouldReturnDataList()
        )
        SimpleFormDialog.build().msg(R.string.passphrase_for_synchronization)
            .fields(
                Input.password(GenericAccountService.KEY_PASSWORD_ENCRYPTION).required()
                    .hint(R.string.input_label_passphrase)
            )
            .extra(args)
            .neut(R.string.button_label_no_encryption)
            .show(this, DIALOG_TAG_PASSWORD)
    }

    private fun createAccountDo(args: Bundle) {
        viewModel.createSyncAccount(args).observe(this) {
            it.onSuccess {
                recordUsage(ContribFeature.SYNCHRONIZATION)
                if ("xiaomi".equals(Build.MANUFACTURER, ignoreCase = true)) {
                    showMessage("On some Xiaomi devices, synchronization does not work without AutoStart permission. Visit <a href=\"https://github.com/mtotschnig/MyExpenses/wiki/FAQ:-Synchronization#q2\">MyExpenses FAQ</a> for more information.")
                }
                onReceiveSyncAccountData(it)
            }.onFailure { throwable ->
                if (throwable is ResolvableSetupException) {
                    throwable.resolution?.let {
                        try {
                            startIntentSenderForResult(
                                it.intentSender,
                                REQUEST_CODE_RESOLUTION,
                                null,
                                0,
                                0,
                                0
                            )
                        } catch (e: SendIntentException) {
                            CrashHandler.report(e)
                        }
                    }
                } else {
                    showSnackbar("Unable to set up account: " + throwable.message)
                }
            }
        }
    }

    abstract fun onReceiveSyncAccountData(data: SyncViewModel.SyncAccountData)

    fun checkForDuplicateUuids(data: List<AccountMetaData>) =
        data.map(AccountMetaData::uuid).distinct().count() < data.count()

    fun fetchAccountData(accountName: String) {
        showSnackbar(R.string.progress_dialog_fetching_data_from_sync_backend, Snackbar.LENGTH_INDEFINITE)
        viewModel.fetchAccountData(accountName).observe(this) { result ->
            dismissSnackbar()
            result.onSuccess {
                onReceiveSyncAccountData(it)
            }.onFailure {
                showSnackbar(it.message ?: "ERROR")
            }
        }
    }

    protected open fun createAccountTaskShouldReturnDataList(): Boolean {
        return false
    }

    override fun onCancelEditDialog() {}
    override fun onPostExecute(taskId: Int, o: Any?) {
        super.onPostExecute(taskId, o)
        when (taskId) {
            TaskExecutionFragment.TASK_WEBDAV_TEST_LOGIN -> {
                webdavFragment!!.onTestLoginResult(o as Exceptional<Void?>?)
            }
        }
    }

    fun addSyncProviderMenuEntries(subMenu: SubMenu) {
        for (factory in backendProviders) {
            subMenu.add(Menu.NONE, factory.id, Menu.NONE, factory.label)
        }
    }

    fun getSyncBackendProviderFactoryById(id: Int): SyncBackendProviderFactory? {
        return try {
            getSyncBackendProviderFactoryByIdOrThrow(id)
        } catch (e: IllegalStateException) {
            null
        }
    }

    @Throws(IllegalStateException::class)
    fun getSyncBackendProviderFactoryByIdOrThrow(id: Int): SyncBackendProviderFactory {
        for (factory in backendProviders) {
            if (factory.id == id) {
                return factory
            }
        }
        throw IllegalStateException()
    }

    private val webdavFragment: SetupWebdavDialogFragment?
        get() = supportFragmentManager.findFragmentByTag(
            WebDavBackendProviderFactory.WEBDAV_SETUP
        ) as SetupWebdavDialogFragment?

    override fun onResult(dialogTag: String, which: Int, extras: Bundle): Boolean {
        if (DIALOG_TAG_PASSWORD == dialogTag) {
            if (which != OnDialogResultListener.BUTTON_POSITIVE || "" == extras.getString(
                    GenericAccountService.KEY_PASSWORD_ENCRYPTION
                )
            ) {
                extras.remove(GenericAccountService.KEY_PASSWORD_ENCRYPTION)
            }
            createAccountDo(extras)
        }
        return false
    }

    companion object {
        private const val DIALOG_TAG_PASSWORD = "password"
        private const val REQUEST_CODE_RESOLUTION = 1
        const val KEY_SYNC_PROVIDER_ID = "syncProviderId"
    }
}