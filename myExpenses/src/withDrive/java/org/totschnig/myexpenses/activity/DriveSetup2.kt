package org.totschnig.myexpenses.activity

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.sync.DriveServiceHelper

class DriveSetup2 : ProtectedFragmentActivity() {
    private val REQUEST_ACCOUNT_PICKER = 1
    private val REQUEST_RESOLUTION = 2
    private var accountName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivityForResult(AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), true, null, null, null, null),
                REQUEST_ACCOUNT_PICKER)
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

    private fun query() {
        accountName?.also {
            val helper = DriveServiceHelper(this, it)
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val name = helper.listChildren("root").get(0).name
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DriveSetup2, name, Toast.LENGTH_LONG).show()
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
}