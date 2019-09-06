package org.totschnig.myexpenses.activity

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import eltos.simpledialogfragment.SimpleDialog
import eltos.simpledialogfragment.list.CustomListDialog
import eltos.simpledialogfragment.list.CustomListDialog.SINGLE_CHOICE
import eltos.simpledialogfragment.list.SimpleListDialog
import icepick.Icepick
import icepick.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.sync.DriveServiceHelper
import org.totschnig.myexpenses.sync.GenericAccountService.KEY_SYNC_PROVIDER_URL
import org.totschnig.myexpenses.sync.GoogleDriveBackendProvider.KEY_GOOGLE_ACCOUNT_EMAIL
import org.totschnig.myexpenses.sync.GoogleDriveBackendProviderFactory

const val DIALOG_TAG_FOLDER_SELECT = "FOLDER_SELECT"

class DriveSetup2 : ProtectedFragmentActivity(), SimpleDialog.OnDialogResultListener {
    private val REQUEST_ACCOUNT_PICKER = 1
    private val REQUEST_RESOLUTION = 2
    @JvmField @State
    var accountName: String? = null
    @JvmField @State
    var idList: ArrayList<String> = ArrayList()

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

    @SuppressLint("BuildNotImplemented")
    private fun query() {
        accountName?.also {
            val helper = DriveServiceHelper(this, it)
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val files = helper.listFolders()
                            .filter { file -> file.name.endsWith(".mesync") }
                    val names = files.map { file -> file.name }
                    idList.clear()
                    idList.addAll(files.map { file -> file.id })
                    IntArray(11) { it }
                    withContext(Dispatchers.Main) {
                        SimpleListDialog.build().choiceMode(SINGLE_CHOICE)
                                .items(names.toTypedArray(), LongArray(idList.size) { it.toLong() })
                                .neg()
                                .show(this@DriveSetup2, DIALOG_TAG_FOLDER_SELECT)
                    }
                } catch (e: Exception) {
                    ((if (e is UserRecoverableAuthIOException) e.cause else e) as? UserRecoverableAuthException)?.let {
                        withContext(Dispatchers.Main) {
                            startActivityForResult(it.intent, REQUEST_RESOLUTION);
                        }
                    } ?: throw e
                }
            }
        }
    }


    override fun onResult(dialogTag: String, which: Int, extras: Bundle): Boolean {
        if (dialogTag.equals(DIALOG_TAG_FOLDER_SELECT)) {
            if (which == SimpleDialog.OnDialogResultListener.BUTTON_POSITIVE) {
                val intent = Intent()
                val bundle = Bundle(2)
                bundle.putString(KEY_SYNC_PROVIDER_URL, idList.get(extras.getLong(CustomListDialog.SELECTED_SINGLE_ID).toInt()));
                bundle.putString(KEY_GOOGLE_ACCOUNT_EMAIL, accountName)
                intent.putExtra(AccountManager.KEY_USERDATA, bundle)
                intent.putExtra(AccountManager.KEY_ACCOUNT_NAME,
                        GoogleDriveBackendProviderFactory.LABEL + " - " + extras.getString(SimpleListDialog.SELECTED_SINGLE_LABEL))
                setResult(RESULT_OK, intent)
            } else {
                setResult(Activity.RESULT_CANCELED)
            }
            finish();
            return true
        }
        return false
    }

}