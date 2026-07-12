package com.freetime.app.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WebSocketApprovalInstrumentedTest {

    @Test
    fun simulateMediaDownloadApprovedInvokesListener() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val wsManager = WebSocketManager.getInstance()

        val latch = CountDownLatch(1)
        var receivedPayload: WebSocketManager.MediaDownloadResponseData? = null

        val listener = object : WebSocketManager.WebSocketListener {
            override fun onMediaDownloadApproved(data: WebSocketManager.MediaDownloadResponseData) {
                receivedPayload = data
                latch.countDown()
            }
            // no-op other callbacks
        }

        try {
            wsManager.addListener(listener)

            // Build a test JSON payload similar to server approval
            val json = JSONObject()
            json.put("requestId", "req-test-123")
            json.put("mediaId", "media-test-456")
            json.put("downloadUrl", "https://example.com/media/media-test-456.enc")
            json.put("encryptionKey", "test-key-abc")
            json.put("iv", "ivvalue")
            json.put("fileName", "photo.jpg")
            json.put("mimeType", "image/jpeg")
            json.put("size", 1024)
            json.put("encrypted", true)

            // Use reflection to invoke private handler on WebSocketManager
            val method = wsManager.javaClass.getDeclaredMethod("handleMediaDownloadApproved", JSONObject::class.java)
            method.isAccessible = true
            method.invoke(wsManager, json)

            val ok = latch.await(3, TimeUnit.SECONDS)
            assertTrue("Listener should be invoked", ok)
            assertNotNull(receivedPayload)
            assertEquals("media-test-456", receivedPayload?.mediaId)
            assertEquals(true, receivedPayload?.encrypted)
            assertEquals("test-key-abc", receivedPayload?.encryptionKey)
            assertEquals("photo.jpg", receivedPayload?.fileName)
            assertTrue(receivedPayload?.downloadUrl?.startsWith("https://") == true)
        } finally {
            wsManager.removeListener(listener)
        }
    }
}
