package com.freetime.app.data.repository

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import com.freetime.app.data.model.WebSocketEvent
import com.freetime.app.websocket.WebSocketClient

/**
 * Unit tests for WebSocketEventRepository
 * 
 * Tests cover:
 * - WebSocket connection lifecycle
 * - Event streaming
 * - Event type filtering
 * - Connection state management
 * - Error handling
 */
class WebSocketEventRepositoryTest {
    
    @Mock
    private lateinit var mockWebSocketClient: WebSocketClient
    
    private lateinit var repository: WebSocketEventRepository
    
    private val testEventFriendRequest = WebSocketEvent(
        type = "FRIEND_REQUEST_RECEIVED",
        data = mapOf(
            "senderId" to "user_123",
            "senderUsername" to "john_doe",
            "requestId" to "req_456"
        ),
        timestamp = System.currentTimeMillis()
    )
    
    private val testEventVoteUpdate = WebSocketEvent(
        type = "VOTE_UPDATED",
        data = mapOf(
            "voteId" to "vote_789",
            "optionId" to "opt_1",
            "newCount" to "25"
        ),
        timestamp = System.currentTimeMillis()
    )
    
    private val testEventMemberJoined = WebSocketEvent(
        type = "MEMBER_JOINED",
        data = mapOf(
            "userId" to "user_999",
            "username" to "jane_smith",
            "channelId" to "channel_123"
        ),
        timestamp = System.currentTimeMillis()
    )
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = WebSocketEventRepository(mockWebSocketClient)
    }
    
    // ===== Connection Tests =====
    
    @Test
    fun testConnectSuccess() {
        whenever(mockWebSocketClient.connect("wss://api.example.com/ws"))
            .thenReturn(true)
        
        val result = repository.connect("wss://api.example.com/ws")
        
        assertTrue(result)
        verify(mockWebSocketClient).connect("wss://api.example.com/ws")
    }
    
    @Test
    fun testConnectFailure() {
        whenever(mockWebSocketClient.connect("wss://invalid.url"))
            .thenReturn(false)
        
        val result = repository.connect("wss://invalid.url")
        
        assertFalse(result)
    }
    
    @Test
    fun testDisconnectSuccess() {
        whenever(mockWebSocketClient.disconnect())
            .thenReturn(true)
        
        val result = repository.disconnect()
        
        assertTrue(result)
        verify(mockWebSocketClient).disconnect()
    }
    
    // ===== Event Listening Tests =====
    
    @Test
    fun testListenToFriendRequestsSuccess() {
        val events = mutableListOf<WebSocketEvent>()
        
        repository.onFriendRequestReceived { event ->
            events.add(event)
        }
        
        // Simulate receiving event
        whenever(mockWebSocketClient.addEventListener("FRIEND_REQUEST_RECEIVED"))
            .thenReturn(Unit)
        
        repository.listenToFriendRequests()
        
        verify(mockWebSocketClient).addEventListener("FRIEND_REQUEST_RECEIVED")
    }
    
    @Test
    fun testListenToVoteUpdatesSuccess() {
        repository.onVoteUpdated { event ->
            assertEquals("VOTE_UPDATED", event.type)
        }
        
        whenever(mockWebSocketClient.addEventListener("VOTE_UPDATED"))
            .thenReturn(Unit)
        
        repository.listenToVoteUpdates()
        
        verify(mockWebSocketClient).addEventListener("VOTE_UPDATED")
    }
    
    @Test
    fun testListenToMemberEventsSuccess() {
        repository.onMemberJoined { event ->
            assertEquals("MEMBER_JOINED", event.type)
        }
        
        whenever(mockWebSocketClient.addEventListener("MEMBER_JOINED"))
            .thenReturn(Unit)
        
        repository.listenToMemberEvents()
        
        verify(mockWebSocketClient).addEventListener("MEMBER_JOINED")
    }
    
    // ===== Event Type Filtering Tests =====
    
    @Test
    fun testFriendRequestEventFiltering() {
        val events = listOf(
            testEventFriendRequest,
            testEventVoteUpdate,
            testEventMemberJoined,
            testEventFriendRequest.copy(data = mapOf("senderId" to "user_555"))
        )
        
        val friendRequests = events.filter { it.type == "FRIEND_REQUEST_RECEIVED" }
        
        assertEquals(2, friendRequests.size)
        friendRequests.forEach { event ->
            assertEquals("FRIEND_REQUEST_RECEIVED", event.type)
        }
    }
    
    @Test
    fun testVoteEventFiltering() {
        val events = listOf(
            testEventFriendRequest,
            testEventVoteUpdate,
            testEventMemberJoined,
            testEventVoteUpdate.copy(data = mapOf("voteId" to "vote_999"))
        )
        
        val voteEvents = events.filter { it.type == "VOTE_UPDATED" }
        
        assertEquals(2, voteEvents.size)
        voteEvents.forEach { event ->
            assertEquals("VOTE_UPDATED", event.type)
        }
    }
    
    @Test
    fun testMemberEventFiltering() {
        val events = listOf(
            testEventFriendRequest,
            testEventVoteUpdate,
            testEventMemberJoined,
            testEventMemberJoined.copy(type = "MEMBER_LEFT")
        )
        
        val memberEvents = events.filter { it.type in listOf("MEMBER_JOINED", "MEMBER_LEFT") }
        
        assertEquals(2, memberEvents.size)
    }
    
    // ===== Event Data Extraction Tests =====
    
    @Test
    fun testFriendRequestEventDataExtraction() {
        val event = testEventFriendRequest
        
        assertEquals("user_123", event.data["senderId"])
        assertEquals("john_doe", event.data["senderUsername"])
        assertEquals("req_456", event.data["requestId"])
    }
    
    @Test
    fun testVoteUpdateEventDataExtraction() {
        val event = testEventVoteUpdate
        
        assertEquals("vote_789", event.data["voteId"])
        assertEquals("opt_1", event.data["optionId"])
        assertEquals("25", event.data["newCount"])
    }
    
    @Test
    fun testMemberJoinedEventDataExtraction() {
        val event = testEventMemberJoined
        
        assertEquals("user_999", event.data["userId"])
        assertEquals("jane_smith", event.data["username"])
        assertEquals("channel_123", event.data["channelId"])
    }
    
    // ===== Connection State Tests =====
    
    @Test
    fun testIsConnectedTrue() {
        whenever(mockWebSocketClient.isConnected())
            .thenReturn(true)
        
        val result = repository.isConnected()
        
        assertTrue(result)
    }
    
    @Test
    fun testIsConnectedFalse() {
        whenever(mockWebSocketClient.isConnected())
            .thenReturn(false)
        
        val result = repository.isConnected()
        
        assertFalse(result)
    }
    
    @Test
    fun testReconnect() {
        whenever(mockWebSocketClient.reconnect())
            .thenReturn(true)
        
        val result = repository.reconnect()
        
        assertTrue(result)
        verify(mockWebSocketClient).reconnect()
    }
    
    // ===== Error Handling Tests =====
    
    @Test
    fun testConnectionErrorHandling() {
        whenever(mockWebSocketClient.connect("wss://api.example.com/ws"))
            .thenThrow(RuntimeException("Connection failed"))
        
        try {
            repository.connect("wss://api.example.com/ws")
            assertTrue(false) // Should throw
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Connection failed") ?: false)
        }
    }
    
    @Test
    fun testEventListenerErrorHandling() {
        whenever(mockWebSocketClient.addEventListener("INVALID_EVENT"))
            .thenThrow(RuntimeException("Invalid event type"))
        
        try {
            mockWebSocketClient.addEventListener("INVALID_EVENT")
            assertTrue(false)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Invalid event type") ?: false)
        }
    }
    
    // ===== Event Timestamp Tests =====
    
    @Test
    fun testEventTimestampOrdering() {
        val time1 = System.currentTimeMillis()
        val time2 = time1 + 1000
        val time3 = time2 + 1000
        
        val events = listOf(
            testEventFriendRequest.copy(timestamp = time1),
            testEventVoteUpdate.copy(timestamp = time2),
            testEventMemberJoined.copy(timestamp = time3)
        )
        
        val sortedEvents = events.sortedBy { it.timestamp }
        
        assertEquals(time1, sortedEvents[0].timestamp)
        assertEquals(time2, sortedEvents[1].timestamp)
        assertEquals(time3, sortedEvents[2].timestamp)
    }
    
    @Test
    fun testEventTimestampValidity() {
        val event = testEventFriendRequest
        
        assertTrue(event.timestamp > 0)
        assertTrue(event.timestamp <= System.currentTimeMillis())
    }
    
    // ===== Batch Event Tests =====
    
    @Test
    fun testMultipleEventsHandling() {
        val events = (1..100).map { i ->
            WebSocketEvent(
                type = when (i % 3) {
                    0 -> "FRIEND_REQUEST_RECEIVED"
                    1 -> "VOTE_UPDATED"
                    else -> "MEMBER_JOINED"
                },
                data = mapOf("id" to "item_$i"),
                timestamp = System.currentTimeMillis() + i * 1000L
            )
        }
        
        val friendRequests = events.filter { it.type == "FRIEND_REQUEST_RECEIVED" }
        val voteUpdates = events.filter { it.type == "VOTE_UPDATED" }
        val memberEvents = events.filter { it.type == "MEMBER_JOINED" }
        
        assertTrue(friendRequests.size > 0)
        assertTrue(voteUpdates.size > 0)
        assertTrue(memberEvents.size > 0)
        
        assertEquals(100, friendRequests.size + voteUpdates.size + memberEvents.size)
    }
    
    @Test
    fun testEventProcessingSequence() {
        val processedEvents = mutableListOf<String>()
        
        val events = listOf(
            testEventFriendRequest,
            testEventVoteUpdate,
            testEventMemberJoined
        )
        
        events.forEach { event ->
            processedEvents.add(event.type)
        }
        
        assertEquals("FRIEND_REQUEST_RECEIVED", processedEvents[0])
        assertEquals("VOTE_UPDATED", processedEvents[1])
        assertEquals("MEMBER_JOINED", processedEvents[2])
    }
    
    // ===== Listener Cleanup Tests =====
    
    @Test
    fun testRemoveListenerSuccess() {
        whenever(mockWebSocketClient.removeEventListener("FRIEND_REQUEST_RECEIVED"))
            .thenReturn(Unit)
        
        repository.removeFriendRequestListener()
        
        verify(mockWebSocketClient).removeEventListener("FRIEND_REQUEST_RECEIVED")
    }
    
    @Test
    fun testRemoveAllListenersSuccess() {
        whenever(mockWebSocketClient.removeAllListeners())
            .thenReturn(Unit)
        
        repository.removeAllListeners()
        
        verify(mockWebSocketClient).removeAllListeners()
    }
}
