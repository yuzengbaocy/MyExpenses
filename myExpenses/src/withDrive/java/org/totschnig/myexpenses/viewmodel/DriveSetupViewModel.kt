package org.totschnig.myexpenses.viewmodel

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.sync.DriveServiceHelper
import org.totschnig.myexpenses.sync.GoogleDriveBackendProvider

class DriveSetupViewModel(application: Application) : AbstractSetupViewModel(application) {

    private var helper: DriveServiceHelper? = null

    fun initWithAccount(accountName: String) {
        helper = DriveServiceHelper(getApplication(), accountName)
    }

    override suspend fun getFolders() = withContext(Dispatchers.IO) {
        helper?.let {
            it.listFolders()
                    .filter { file ->
                        file.name.endsWith(".mesync") ||
                                file.appProperties?.containsKey(GoogleDriveBackendProvider.IS_SYNC_FOLDER) == true
                    }
                    .map { file -> Pair(file.id, file.name) }
        } ?: throw Exception("Helper not initialized")
    }

    override suspend fun createFolderBackground(label: String): Pair<String, String> = withContext(Dispatchers.IO) {
        helper?.let {
            it.createFolder("root", label, mapOf(Pair(GoogleDriveBackendProvider.IS_SYNC_FOLDER, "true"))).let {
                Pair(it.id, it.name)
            }
        }  ?: throw Exception("Helper not initialized")
    }
}
