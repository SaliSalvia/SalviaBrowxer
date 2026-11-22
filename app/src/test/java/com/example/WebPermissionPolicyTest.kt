package com.example

import android.Manifest
import com.example.engine.WebPermissionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPermissionPolicyTest {

    @Test
    fun `camera and microphone map only to their Android runtime permissions`() {
        assertEquals(
            listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            WebPermissionPolicy.androidPermissionsForCapture(
                listOf(
                    WebPermissionPolicy.CAMERA_RESOURCE,
                    WebPermissionPolicy.MICROPHONE_RESOURCE,
                ),
            ),
        )
    }

    @Test
    fun `unknown or mixed web resources are denied as a whole`() {
        assertNull(WebPermissionPolicy.androidPermissionsForCapture(emptyList()))
        assertNull(
            WebPermissionPolicy.androidPermissionsForCapture(
                listOf(WebPermissionPolicy.CAMERA_RESOURCE, "android.webkit.resource.PROTECTED_MEDIA_ID"),
            ),
        )
    }

    @Test
    fun `same secure origin is allowed independent of page path`() {
        assertTrue(
            WebPermissionPolicy.isSameSecureOrigin(
                "https://media.example.com",
                "https://media.example.com/watch?id=1",
            ),
        )
        assertTrue(
            WebPermissionPolicy.isSameSecureOrigin(
                "https://media.example.com:443",
                "https://media.example.com/watch",
            ),
        )
    }

    @Test
    fun `cross origin and insecure non local origins are denied`() {
        assertFalse(
            WebPermissionPolicy.isSameSecureOrigin(
                "https://tracker.example.net",
                "https://media.example.com/watch",
            ),
        )
        assertFalse(
            WebPermissionPolicy.isSameSecureOrigin(
                "http://media.example.com",
                "http://media.example.com/watch",
            ),
        )
    }

    @Test
    fun `localhost is permitted as a secure context for development`() {
        assertTrue(
            WebPermissionPolicy.isSameSecureOrigin(
                "http://localhost:8080",
                "http://localhost:8080/capture",
            ),
        )
        assertFalse(
            WebPermissionPolicy.isSameSecureOrigin(
                "http://localhost:8080",
                "http://localhost:8081/capture",
            ),
        )
    }

    @Test
    fun `credentials embedded in origins are rejected`() {
        assertFalse(
            WebPermissionPolicy.isSameSecureOrigin(
                "https://user:pass@example.com",
                "https://example.com/page",
            ),
        )
    }
}
