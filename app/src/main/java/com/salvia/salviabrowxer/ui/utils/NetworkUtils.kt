package com.salvia.salviabrowxer.ui.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.URL

fun Context.hasNetworkConnection(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

fun Context.isWifiConnected(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

fun Context.isMeteredNetwork(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}

fun getDomainFromUrl(url: String): String {
    return try {
        val uri = URL(url)
        var domain = uri.host ?: ""
        if (domain.startsWith("www.")) {
            domain = domain.substring(4)
        }
        domain
    } catch (e: Exception) {
        ""
    }
}

fun isValidUrl(url: String): Boolean {
    return try {
        URL(url)
        true
    } catch (e: Exception) {
        false
    }
}

fun makeUrlAbsolute(baseUrl: String, relativeUrl: String): String {
    return if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
        relativeUrl
    } else if (relativeUrl.startsWith("//")) {
        "https:$relativeUrl"
    } else if (relativeUrl.startsWith("/")) {
        val baseUri = URL(baseUrl)
        "${baseUri.protocol}://${baseUri.host}${if (baseUri.port != -1) ":${baseUri.port}" else ""}$relativeUrl"
    } else {
        val baseUri = URL(baseUrl)
        val basePath = baseUri.path
        val lastSlashIndex = basePath.lastIndexOf('/')
        val parentPath = if (lastSlashIndex >= 0) basePath.substring(0, lastSlashIndex) else basePath
        "${baseUri.protocol}://${baseUri.host}${if (baseUri.port != -1) ":${baseUri.port}" else ""}$parentPath/$relativeUrl"
    }
}