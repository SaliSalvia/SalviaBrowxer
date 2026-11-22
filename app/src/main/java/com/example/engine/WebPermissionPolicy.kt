package com.example.engine

import android.Manifest
import android.webkit.PermissionRequest
import java.net.URI
import java.util.Locale

/**
 * Fail-closed policy for permissions requested by untrusted web content.
 *
 * We currently support only camera and microphone capture. Unknown WebView
 * resources (including protected-media identifiers) are deliberately denied.
 */
object WebPermissionPolicy {

    const val CAMERA_RESOURCE = PermissionRequest.RESOURCE_VIDEO_CAPTURE
    const val MICROPHONE_RESOURCE = PermissionRequest.RESOURCE_AUDIO_CAPTURE

    val locationAndroidPermissions: List<String> = listOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    fun androidPermissionsForCapture(resources: Collection<String>): List<String>? {
        if (resources.isEmpty()) return null
        val permissions = mutableListOf<String>()
        for (resource in resources) {
            when (resource) {
                CAMERA_RESOURCE -> permissions += Manifest.permission.CAMERA
                MICROPHONE_RESOURCE -> permissions += Manifest.permission.RECORD_AUDIO
                else -> return null
            }
        }
        return permissions.distinct()
    }

    fun isSameSecureOrigin(requestOrigin: String, currentPageUrl: String): Boolean {
        val request = parseOrigin(requestOrigin) ?: return false
        val page = parseOrigin(currentPageUrl) ?: return false
        if (!isSecureScheme(request.scheme, request.host) ||
            !isSecureScheme(page.scheme, page.host)
        ) return false

        return request.scheme == page.scheme &&
            request.host == page.host &&
            effectivePort(request) == effectivePort(page)
    }

    private fun parseOrigin(value: String): URI? {
        return runCatching {
            val uri = URI(value).normalize()
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            if (uri.userInfo != null || host.isBlank()) return null
            if (scheme != "https" && scheme != "http") return null
            URI(scheme, null, host, uri.port, null, null, null)
        }.getOrNull()
    }

    private fun isSecureScheme(scheme: String, host: String): Boolean {
        if (scheme == "https") return true
        if (scheme != "http") return false
        return host == "localhost" ||
            host == "127.0.0.1" ||
            host == "::1"
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme == "https" -> 443
        else -> 80
    }
}
