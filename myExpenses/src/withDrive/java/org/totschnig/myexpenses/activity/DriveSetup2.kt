package org.totschnig.myexpenses.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import org.totschnig.myexpenses.sync.DriveServiceHelper
import timber.log.Timber

class DriveSetup2 : ProtectedFragmentActivity() {
    private val REQUEST_CODE_SIGN_IN = 1
    private val REQUEST_AUTHORIZATION = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_FILE))
                .build()
        val client = GoogleSignIn.getClient(this, signInOptions)

        // The result of the sign-in Intent is handled in onActivityResult.
        startActivityForResult(client.signInIntent, REQUEST_CODE_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        when (requestCode) {
            REQUEST_CODE_SIGN_IN -> if (resultCode == Activity.RESULT_OK && resultData != null) {
                handleSignInResult(resultData)
            }
        }

        super.onActivityResult(requestCode, resultCode, resultData)
    }

    private fun handleSignInResult(result: Intent) {
        GoogleSignIn.getSignedInAccountFromIntent(result)
                .addOnSuccessListener { googleAccount ->

                    // Use the authenticated account to sign in to the Drive service.
                    val credential = GoogleAccountCredential.usingOAuth2(
                            this, setOf(DriveScopes.DRIVE_FILE))
                    credential.selectedAccount = googleAccount.account
                    val googleDriveService = Drive.Builder(
                            NetHttpTransport(),
                            GsonFactory(),
                            credential)
                            .setApplicationName("Drive API Migration")
                            .build()

                    val driveServiceHelper = DriveServiceHelper(googleDriveService)
                    driveServiceHelper.queryFiles()
                            .addOnSuccessListener { fileList ->
                                val builder = StringBuilder()
                                for (file in fileList.getFiles()) {
                                    builder.append(file.getName()).append("\n")
                                }
                                Toast.makeText(this, builder.toString(), Toast.LENGTH_LONG).show()
                                finish()
                            }
                            .addOnFailureListener { exception ->
                                if (exception is UserRecoverableAuthIOException) {
                                    startActivityForResult(exception.getIntent(), REQUEST_AUTHORIZATION)
                                } else {
                                    Timber.e(exception)
                                }
                            }
                }
                .addOnFailureListener { exception -> Timber.e(exception, "Unable to sign in.") }
    }
}