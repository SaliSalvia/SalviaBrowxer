package com.salvia.salviabrowxer.media.downloader

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request

class DownloadManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    suspend fun getFinalUrl(url: String): String {
        val request = Request.Builder()
            .url(url)
            .head()
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            response.request.url.toString()
        }
    }

    suspend fun getContentLength(url: String): Long {
        val request = Request.Builder()
            .url(url)
            .head()
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            response.body?.contentLength() ?: 0L
        }
    }

    fun generateFilename(title: String?, extension: String?): String {
        val baseName = title?.takeIf { it.isNotEmpty() } ?: "download"
        val ext = extension?.takeIf { it.isNotEmpty() } ?: ""
        val sanitizedName = baseName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return "$sanitizedName${if (ext.isNotEmpty()) ".$ext" else ""}"
    }
}
