/**
 * Copyright 2018 Google LLC
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.totschnig.myexpenses.sync

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import java.io.IOException
import java.io.InputStream
import java.util.*

/**
 * A utility for performing read/write operations on Drive files via the REST API
 */

const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"

class DriveServiceHelper(context: Context, accountName: String) {
    private val mDriveService: Drive

    init {
        val credential = GoogleAccountCredential.usingOAuth2(
                context, setOf(DriveScopes.DRIVE_FILE))
        credential.selectedAccountName = accountName
        mDriveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory(),
                credential)
                .setApplicationName("Drive API Migration")
                .build()

    }

    @Throws(IOException::class)
    fun createFolder(parent: String, name: String, properties: MutableMap<String, String>?): File {
        return createFile(parent, name, MIME_TYPE_FOLDER, properties)
    }

    fun isFolder(file: File) = file.mimeType.equals(MIME_TYPE_FOLDER)

    /**
     * Creates a text file in the user's My Drive folder and returns its file ID.
     */
    @Throws(IOException::class)
    fun createFile(parent: String, name: String, mimeType: String, properties: MutableMap<String, String>?): File {
        val metadata = File()
                .setParents(listOf(parent))
                .setMimeType(mimeType)
                .setAppProperties(properties)
                .setName(name)

        return mDriveService.files().create(metadata).execute()
                ?: throw IOException("Null result when requesting file creation.")
    }

    /**
     * Updates the file identified by `fileId` with the given `name` and `content`.
     */
    @Throws(IOException::class)
    fun saveFile(fileId: String, mimeType: String, content: InputStream) {
        // Convert content to an AbstractInputStreamContent instance.
        val contentStream = InputStreamContent(mimeType, content)

        // Update the metadata and contents.
        mDriveService.files().update(fileId, null, contentStream).execute()
    }

    @Throws(IOException::class)
    fun setMetadataProperty(fileId: String, key: String, value: String) {
        val metadata = File().apply {
            appProperties = mapOf(Pair(key, value))
        }
        mDriveService.files().update(fileId, metadata, null).execute()
    }

    @Throws(IOException::class)
    fun listChildren(parent: File, query: String? = null) = listChildren(parent.id, query)

    @Throws(IOException::class)
    fun listChildren(parentId: String, query: String? = null): List<File> {
        val result = mutableListOf<File>()
        var pageToken: String? = null
        do {
            val fileList = mDriveService.files().list().apply {
                q = "'%s' in parents%s".format(Locale.ROOT, parentId, query?.let { " and " + it } ?: "")
                spaces = "drive"
                fields = "nextPageToken, files(id, name, mimeType)"
                this.pageToken = pageToken
            }.execute()
            pageToken = fileList.nextPageToken
            result.addAll(fileList.files)
        } while (pageToken != null)
        return result
    }

    @Throws(IOException::class)
    fun getFile(fileId: String): File {
        return mDriveService.files().get(fileId).execute()
    }

    @Throws(IOException::class)
    fun getFileByNameAndParent(parent: File, name: String): File? {
        val result = mDriveService.files().list().setSpaces("drive")
                .setQ("'%s' in parents and name = '%s".format(Locale.ROOT, parent.id, name))
                .execute().files
        return if (result != null && result.size > 0) result[0] else null
    }

    @Throws(IOException::class)
    fun downloadFile(parent: File, name: String): InputStream {
        getFileByNameAndParent(parent, name)?.let {
            return read(it.id)
        } ?: throw IOException("File not found")
    }

    @Throws(IOException::class)
    fun delete(fileId: String) {
        mDriveService.files().delete(fileId)
    }

    @Throws(IOException::class)
    fun read(fileId: String): InputStream {
        return mDriveService.files().get(fileId).executeMediaAsInputStream()
    }
}
