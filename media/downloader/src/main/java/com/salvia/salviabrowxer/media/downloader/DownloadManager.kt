package com.salvia.salviabrowxer.media.downloader

import android.content.Context
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.core.model.MediaFormat
import com.salvia.salviabrowxer.core.model.MediaInfo
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import com.salvia.salviabrowxer.media.resolver.MediaResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val mediaResolver: MediaResolver,
    private val okHttpClient: OkHttpClient
) {

    fun enqueueDownload(
        pageUrl: String,
        mediaUrl: String,
        selectedFormat: MediaFormat? = null
    ) {
        // This will be implemented in the service
    }

    suspend fun getFinalUrl(url: String): String {
        val request = okhttp3.Request.Builder()
            .url(url)
            .head()
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            response.request.url.toString()
        }
    }

    suspend fun getContentLength(url: String): Long {
        val request = okhttp3.Request.Builder()
            .url(url)
            .head()
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            response.body?.contentLength() ?: 0L
        }
    }

    private fun generateFilename(title: String?, extension: String?): String {
        val baseName = title?.takeIf { it.isNotEmpty() } ?: "download"
        val ext = extension?.takeIf { it.isNotEmpty() } ?: ""
        val sanitizedName = baseName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return "$sanitizedName${if (ext.isNotEmpty()) ".$ext" else ""}"
    }
}