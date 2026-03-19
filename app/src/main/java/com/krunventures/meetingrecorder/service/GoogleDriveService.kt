package com.krunventures.meetingrecorder.service

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

class GoogleDriveService(private val context: Context) {

    companion object {
        private const val APP_NAME = "회의녹음요약"
        val SCOPES = listOf(DriveScopes.DRIVE_FILE)
    }

    private var driveService: Drive? = null

    fun getSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent(): Intent = getSignInClient().signInIntent

    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))
    }

    fun getAccountEmail(): String {
        return GoogleSignIn.getLastSignedInAccount(context)?.email ?: ""
    }

    fun handleSignInResult(account: GoogleSignInAccount): Boolean {
        return try {
            initDriveService(account)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun initDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(context, SCOPES)
        credential.selectedAccount = account.account
        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName(APP_NAME).build()
    }

    fun initFromLastAccount(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return try {
            initDriveService(account)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            getSignInClient().signOut()
            driveService = null
        }
    }

    data class DriveResult(val success: Boolean, val message: String, val id: String = "", val link: String = "")

    suspend fun ensureFolder(folderName: String, parentId: String = "root"): DriveResult {
        return withContext(Dispatchers.IO) {
            val service = driveService ?: return@withContext DriveResult(false, "Google Drive 인증이 필요합니다.")
            try {
                // Search for existing folder
                val query = "name='$folderName' and mimeType='application/vnd.google-apps.folder' and '$parentId' in parents and trashed=false"
                val result = service.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("files(id, name)")
                    .setPageSize(1)
                    .execute()

                val files = result.files ?: emptyList()
                if (files.isNotEmpty()) {
                    val fid = files[0].id
                    return@withContext DriveResult(true, "기존 폴더 사용: '$folderName'", id = fid)
                }

                // Create new folder
                val meta = com.google.api.services.drive.model.File().apply {
                    name = folderName
                    mimeType = "application/vnd.google-apps.folder"
                    parents = listOf(parentId)
                }
                val created = service.files().create(meta).setFields("id").execute()
                DriveResult(true, "새 폴더 생성: '$folderName'", id = created.id)
            } catch (e: Exception) {
                DriveResult(false, "폴더 생성 오류: ${e.message?.take(200)}")
            }
        }
    }

    suspend fun uploadFile(localFile: File, folderId: String): DriveResult {
        return withContext(Dispatchers.IO) {
            val service = driveService ?: return@withContext DriveResult(false, "Google Drive 인증이 필요합니다.")
            if (!localFile.exists()) return@withContext DriveResult(false, "파일 없음: ${localFile.name}")
            if (folderId.isBlank()) return@withContext DriveResult(false, "업로드 폴더 미설정")

            try {
                val mimeType = when (localFile.extension.lowercase()) {
                    "mp3" -> "audio/mpeg"
                    "m4a" -> "audio/mp4"
                    "wav" -> "audio/wav"
                    "txt" -> "text/plain"
                    else -> "application/octet-stream"
                }

                val meta = com.google.api.services.drive.model.File().apply {
                    name = localFile.name
                    parents = listOf(folderId)
                }
                val content = FileContent(mimeType, localFile)
                val uploaded = service.files().create(meta, content)
                    .setFields("id, webViewLink")
                    .execute()

                val link = uploaded.webViewLink ?: "https://drive.google.com/file/d/${uploaded.id}/view"

                // Try to set sharing (ignore errors from org policy restrictions)
                try {
                    val perm = com.google.api.services.drive.model.Permission().apply {
                        type = "anyone"
                        role = "reader"
                    }
                    service.permissions().create(uploaded.id, perm).execute()
                } catch (_: Exception) {}

                DriveResult(true, "업로드 완료", id = uploaded.id, link = link)
            } catch (e: Exception) {
                val msg = e.message ?: ""
                when {
                    "invalid_grant" in msg || "Token" in msg ->
                        DriveResult(false, "Drive 토큰 만료 — 설정에서 재인증해주세요.")
                    "insufficientPermissions" in msg ->
                        DriveResult(false, "Drive 권한 오류 — Google API 권한을 확인해주세요.")
                    "notFound" in msg ->
                        DriveResult(false, "폴더를 찾을 수 없음 — 설정에서 폴더를 다시 생성해주세요.")
                    else -> DriveResult(false, "업로드 실패: ${msg.take(200)}")
                }
            }
        }
    }

    suspend fun uploadMeetingFiles(
        mp3File: File?,
        sttFile: File?,
        summaryFile: File?,
        mp3FolderId: String,
        txtFolderId: String
    ): Map<String, DriveResult> {
        val results = mutableMapOf<String, DriveResult>()
        mp3File?.let {
            if (it.exists() && mp3FolderId.isNotBlank()) {
                results["mp3"] = uploadFile(it, mp3FolderId)
            }
        }
        sttFile?.let {
            if (it.exists() && txtFolderId.isNotBlank()) {
                results["stt"] = uploadFile(it, txtFolderId)
            }
        }
        summaryFile?.let {
            if (it.exists() && txtFolderId.isNotBlank()) {
                results["summary"] = uploadFile(it, txtFolderId)
            }
        }
        return results
    }

    suspend fun initDriveFolders(mp3FolderName: String, txtFolderName: String): Pair<DriveResult, DriveResult> {
        val mp3Result = ensureFolder(mp3FolderName)
        val txtResult = ensureFolder(txtFolderName)
        return Pair(mp3Result, txtResult)
    }

    suspend fun listFolders(parentId: String = "root"): List<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            val service = driveService ?: return@withContext emptyList()
            try {
                val query = "mimeType='application/vnd.google-apps.folder' and '$parentId' in parents and trashed=false"
                val result = service.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("files(id, name)")
                    .setOrderBy("name")
                    .setPageSize(50)
                    .execute()
                result.files?.map { Pair(it.id, it.name) } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getFolderName(folderId: String): String {
        return withContext(Dispatchers.IO) {
            val service = driveService ?: return@withContext ""
            try {
                service.files().get(folderId).setFields("name").execute().name ?: ""
            } catch (e: Exception) { "" }
        }
    }
}
