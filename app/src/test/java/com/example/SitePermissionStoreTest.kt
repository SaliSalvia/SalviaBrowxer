package com.example

import com.example.engine.SitePermissionStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SitePermissionStoreTest {
    @Test
    fun `grants are scoped to normalized origin and resource`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("site_permission_store_v1", 0).edit().clear().commit()
        val store = SitePermissionStore(context)

        store.rememberGrant("https://media.example.com/watch", listOf("CAMERA"))

        assertTrue(store.isGranted("https://media.example.com:443/other", "CAMERA"))
        assertFalse(store.isGranted("https://other.example.com", "CAMERA"))
        assertFalse(store.isGranted("https://media.example.com", "MICROPHONE"))
    }

    @Test
    fun `clear origin revokes all resources for that origin only`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("site_permission_store_v1", 0).edit().clear().commit()
        val store = SitePermissionStore(context)
        store.rememberGrant("https://a.example", listOf("CAMERA", "MICROPHONE"))
        store.rememberGrant("https://b.example", listOf("CAMERA"))

        store.clearOrigin("https://a.example/path")

        assertFalse(store.isGranted("https://a.example", "CAMERA"))
        assertTrue(store.isGranted("https://b.example", "CAMERA"))
    }
}
