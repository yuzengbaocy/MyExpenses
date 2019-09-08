package org.totschnig.myexpenses.activity

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import eltos.simpledialogfragment.list.CustomListDialog
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
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

class DriveSetup2 : AbstractSyncBackup() {

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
                            showSelectFolderDialog(names)
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
                finish()
            }
        }
    }

    override fun onFolderSelect(extras: Bundle) {
        success(idList.get(extras.getLong(CustomListDialog.SELECTED_SINGLE_ID).toInt()),
                extras.getString(SimpleListDialog.SELECTED_SINGLE_LABEL))
    }

    override fun onFolderCreate(label: String) {
        requireHelper()?.let {
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    with(it.createFolder("root", label, mapOf(Pair(IS_SYNC_FOLDER, "true")))) {
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

    private fun success(folderId: String, folderName: String?) {
        folderName?.let {
            val intent = Intent()
            val bundle = Bundle(2)
            bundle.putString(GenericAccountService.KEY_SYNC_PROVIDER_URL, folderId)
            bundle.putString(GoogleDriveBackendProvider.KEY_GOOGLE_ACCOUNT_EMAIL, accountName)
            intent.putExtra(AccountManager.KEY_USERDATA, bundle)
            intent.putExtra(SyncBackendSetupActivity.KEY_SYNC_PROVIDER_ID, R.id.SYNC_BACKEND_DRIVE)
            intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, folderName)
            setResult(RESULT_OK, intent)
        } ?: CrashHandler.report("Success called, but no folderName provided")
        finish()
    }
}