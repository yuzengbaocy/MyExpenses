package org.totschnig.myexpenses.activity

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import eltos.simpledialogfragment.SimpleDialog
import eltos.simpledialogfragment.SimpleDialog.OnDialogResultListener.*
import eltos.simpledialogfragment.input.SimpleInputDialog
import eltos.simpledialogfragment.list.CustomListDialog
import eltos.simpledialogfragment.list.CustomListDialog.SINGLE_CHOICE
import eltos.simpledialogfragment.list.SimpleListDialog
import icepick.Icepick
import icepick.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.sync.DriveServiceHelper
import org.totschnig.myexpenses.sync.GenericAccountService
import org.totschnig.myexpenses.sync.GoogleDriveBackendProvider
import org.totschnig.myexpenses.sync.GoogleDriveBackendProvider.IS_SYNC_FOLDER
import org.totschnig.myexpenses.sync.GoogleDriveBackendProviderFactory
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

const val DIALOG_TAG_FOLDER_SELECT = "FOLDER_SELECT"
const val DIALOG_TAG_FOLDER_CREATE = "FOLDER_CREATE"

class DriveSetup2 : ProtectedFragmentActivity(), SimpleDialog.OnDialogResultListener {
    private val REQUEST_ACCOUNT_PICKER = 1
    private val REQUEST_RESOLUTION = 2
    @JvmField
    @State
    var accountName: String? = null
    @JvmField
    @State
    var idList: ArrayList<String> = ArrayList()

    private var helper: DriveServiceHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            startActivityForResult(AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), true, null, null, null, null),
                    REQUEST_ACCOUNT_PICKER)
        } else {
            Icepick.restoreInstanceState(this, savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Icepick.saveInstanceState(this, outState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (resultCode == Activity.RESULT_OK)
            when (requestCode) {
                REQUEST_ACCOUNT_PICKER -> if (resultData != null) {
                    handleSignInResult(resultData)
                }
                REQUEST_RESOLUTION -> query()
            }

        super.onActivityResult(requestCode, resultCode, resultData)
    }

    private fun handleSignInResult(result: Intent) {
        accountName = result.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        query()
    }

    private fun requireHelper() = helper ?: accountName?.let { DriveServiceHelper(this, it) }

    @SuppressLint("BuildNotImplemented")
    private fun query() {
        requireHelper()?.let {
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val files = it.listFolders()
                            .filter { file -> file.name.endsWith(".mesync") ||
                                    file.appProperties?.containsKey(IS_SYNC_FOLDER) == true }
                    withContext(Dispatchers.Main) {
                        if (files.size > 0) {
                            val names = files.map { file -> file.name }
                            idList.clear()
                            idList.addAll(files.map { file -> file.id })

                            SimpleListDialog.build().choiceMode(SINGLE_CHOICE)
                                    .title(R.string.synchronization_select_folder_dialog_title)
                                    .items(names.toTypedArray(), LongArray(idList.size) { it.toLong() })
                                    .neg()
                                    .pos(R.string.select)
                                    .neut(R.string.menu_create_folder)
                                    .show(this@DriveSetup2, DIALOG_TAG_FOLDER_SELECT)
                        } else {
                            showCreateFolderDialog()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        handleException(e)
                    }
                }
            }
        }
    }

    private fun handleException(e: java.lang.Exception) {
        ((if (e is UserRecoverableAuthIOException) e.cause else e) as? UserRecoverableAuthException)?.let {
            startActivityForResult(it.intent, REQUEST_RESOLUTION);
        } ?: kotlin.run {
            CrashHandler.report(e)
            e.message?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("BuildNotImplemented")
    fun showCreateFolderDialog() {
        SimpleInputDialog.build()
                .title(R.string.menu_create_folder)
                .pos(android.R.string.ok)
                .neut()
                .show(this, DIALOG_TAG_FOLDER_CREATE)
    }

    override fun onResult(dialogTag: String, which: Int, extras: Bundle): Boolean {
        when {
            dialogTag.equals(DIALOG_TAG_FOLDER_SELECT) -> {
                when (which) {
                    BUTTON_POSITIVE -> {
                        success(idList.get(extras.getLong(CustomListDialog.SELECTED_SINGLE_ID).toInt()),
                                extras.getString(SimpleListDialog.SELECTED_SINGLE_LABEL))
                    }
                    BUTTON_NEUTRAL -> showCreateFolderDialog()
                    BUTTON_NEGATIVE -> abort()
                }
                return true
            }
            dialogTag.equals(DIALOG_TAG_FOLDER_CREATE) -> {
                when (which) {
                    BUTTON_POSITIVE -> {
                        requireHelper()?.let {
                            CoroutineScope(Dispatchers.Default).launch {
                                try {
                                    val properties = mutableMapOf<String, String>()
                                    properties[IS_SYNC_FOLDER] = "true"
                                    with(it.createFolder("root",
                                            extras.getString(SimpleInputDialog.TEXT, "MyExpenses"),
                                            properties)) {
                                        success(id, name)
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        handleException(e)
                                    }
                                }
                            }
                        }
                    }
                    BUTTON_NEGATIVE -> abort()
                }
                return true
            }
        }
        return false
    }

    private fun success(folderId: String, folderName: String?) {
        folderName?.let {
            val intent = Intent()
            val bundle = Bundle(2)
            bundle.putString(GenericAccountService.KEY_SYNC_PROVIDER_URL, folderId)
            bundle.putString(GoogleDriveBackendProvider.KEY_GOOGLE_ACCOUNT_EMAIL, accountName)
            intent.putExtra(AccountManager.KEY_USERDATA, bundle)
            intent.putExtra(AccountManager.KEY_ACCOUNT_NAME,
                    GoogleDriveBackendProviderFactory.LABEL + " - " + folderName)
            setResult(RESULT_OK, intent)
        } ?: CrashHandler.report("Success called, but no folderName provided")
        finish()
    }

    private fun abort() {
        setResult(Activity.RESULT_CANCELED)
        finish();
    }
}