package com.example.engine

import android.content.Context
import android.net.Uri

/**
 * Small persistent allow-list for web-origin permissions.
 *
 * Only explicit user grants are stored. Denials are not cached, unknown resources fail closed,
 * and entries are keyed by normalized origin plus WebView resource name. Incognito callers
 * must not consult this store.
 */
class SitePermissionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "site_permission_store_v1",
        Context.MODE_PRIVATE,
    )

    fun isGranted(origin: String, resource: String): Boolean {
        val key = key(origin, resource) ?: return false
        return prefs.getBoolean(key, false)
    }

    fun rememberGrant(origin: String, resources: Collection<String>) {
        val editor = prefs.edit()
        resources.forEach { resource ->
            key(origin, resource)?.let { editor.putBoolean(it, true) }
        }
        editor.apply()
    }

    fun revoke(origin: String, resource: String) {
        key(origin, resource)?.let { prefs.edit().remove(it).apply() }
    }

    fun clearOrigin(origin: String) {
        val normalized = normalizeOrigin(origin) ?: return
        val prefix = "$normalized|"
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { key ->
            prefs.edit().remove(key).apply()
        }
    }

    private fun key(origin: String, resource: String): String? {
        if (resource.isBlank() || resource.length > 128) return null
        return normalizeOrigin(origin)?.let { "$it|$resource" }
    }

    private fun normalizeOrigin(value: String): String? = runCatching {
        val uri = Uri.parse(value)
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if ((scheme != "https" && scheme != "http") || host.isBlank() || uri.userInfo != null) {
            return null
        }
        val port = when {
            uri.port >= 0 -> uri.port
            scheme == "https" -> 443
            else -> 80
        }
        "$scheme://$host:$port"
    }.getOrNull()
}
