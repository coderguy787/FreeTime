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
import com.freetime.app.api.SecureChatAPI
import com.freetime.app.data.model.FriendRequest
import com.freetime.app.data.model.Friend
import retrofit2.Response

/**
 * Unit tests for FriendSystemRepository
 * 
 * Tests cover:
 * - API call methods
 * - Data parsing and transformation
 * - Error handling
 * - Response validation
 */
class FriendSystemRepositoryTest {
    
    @Mock
    private lateinit var mockAPI: SecureChatAPI
    
    private lateinit var repository: FriendSystemRepository
    
    private val testFriendRequest = FriendRequest(
        id = "req_123",
        senderId = "user_456",
        senderUsername = "john_doe",
        createdAt = System.currentTimeMillis()
    )
    
    private val testFriend = Friend(
        id = "user_789",
        username = "jane_smith",
        isActive = true
    )
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = FriendSystemRepository(mockAPI)
    }
    
    // ===== Get Incoming Requests Tests =====
    
    @Test
    fun testGetIncomingRequestsSuccess() {
        val requests = listOf(testFriendRequest)
        
        whenever(mockAPI.getIncomingFriendRequests())
            .thenReturn(Response.success(requests))
        
        val result = repository.getIncomingRequests()
        
        assertEquals(requests, result)
        verify(mockAPI).getIncomingFriendRequests()
    }
    
    @Test
    fun testGetIncomingRequestsReturnsEmptyList() {
        whenever(mockAPI.getIncomingFriendRequests())
            .thenReturn(Response.success(emptyList()))
        
        val result = repository.getIncomingRequests()
        
        assertEquals(0, result.size)
    }
    
    @Test
    fun testGetIncomingRequestsHandlesFailure() {
        whenever(mockAPI.getIncomingFriendRequests())
            .thenThrow(RuntimeException("Network error"))
        
        try {
            repository.getIncomingRequests()
            assertTrue(false) // Should throw
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Network error") ?: false)
        }
    }
    
    // ===== Get Outgoing Requests Tests =====
    
    @Test
    fun testGetOutgoingRequestsSuccess() {
        val requests = listOf(testFriendRequest)
        
        whenever(mockAPI.getOutgoingFriendRequests())
            .thenReturn(Response.success(requests))
        
        val result = repository.getOutgoingRequests()
        
        assertEquals(requests, result)
        verify(mockAPI).getOutgoingFriendRequests()
    }
    
    @Test
    fun testGetOutgoingRequestsHandlesFailure() {
        whenever(mockAPI.getOutgoingFriendRequests())
            .thenThrow(RuntimeException("Connection timeout"))
        
        try {
            repository.getOutgoingRequests()
            assertTrue(false)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Connection timeout") ?: false)
        }
    }
    
    // ===== Get Friends List Tests =====
    
    @Test
    fun testGetFriendListSuccess() {
        val friends = listOf(
            testFriend,
            testFriend.copy(id = "user_999", username = "bob_smith")
        )
        
        whenever(mockAPI.getFriendsList())
            .thenReturn(Response.success(friends))
        
        val result = repository.getFriendsList()
        
        assertEquals(2, result.size)
        assertEquals("jane_smith", result[0].username)
        assertEquals("bob_smith", result[1].username)
    }
    
    @Test
    fun testGetFriendListHandlesFailure() {
        whenever(mockAPI.getFriendsList())
            .thenThrow(RuntimeException("Server error"))
        
        try {
            repository.getFriendsList()
            assertTrue(false)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Server error") ?: false)
        }
    }
    
    // ===== Accept Friend Request Tests =====
    
    @Test
    fun testAcceptFriendRequestSuccess() {
        whenever(mockAPI.acceptFriendRequest("req_123"))
            .thenReturn(Response.success(Unit))
        
        repository.acceptFriendRequest("req_123")
        
        verify(mockAPI).acceptFriendRequest("req_123")
    }
    
    @Test
    fun testAcceptFriendRequestHandlesError() {
        whenever(mockAPI.acceptFriendRequest("req_123"))
            .thenThrow(RuntimeException("Request not found"))
        
        try {
            repository.acceptFriendRequest("req_123")
            assertTrue(false)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Request not found") ?: false)
        }
    }
    
    // ===== Reject Friend Request Tests =====
    
    @Test
    fun testRejectFriendRequestSuccess() {
        whenever(mockAPI.rejectFriendRequest("req_123"))
            .thenReturn(Response.success(Unit))
        
        repository.rejectFriendRequest("req_123")
        
        verify(mockAPI).rejectFriendRequest("req_123")
    }
    
    @Test
    fun testRejectFriendRequestHandlesError() {
        whenever(mockAPI.rejectFriendRequest("req_123"))
            .thenThrow(RuntimeException("Already rejected"))
        
        try {
            repository.rejectFriendRequest("req_123")
            assertTrue(false)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Already rejected") ?: false)
        }
    }
    
    // ===== Send Friend Request Tests =====
    
    @Test
    fun testSendFriendRequestSuccess() {
        val newRequest = testFriendRequest.copy(
            senderId = "current_user",
            senderUsername = "current_user_name"
        )
        
        whenever(mockAPI.sendFriendRequest("user_789"))
            .thenReturn(Response.success(newRequest))
        
        val result = repository.sendFriendRequest("user_789")
        
        assertNotNull(result)
        assertEquals("user_789", result.senderId)
    }
    
    @Test
    fun testSendFriendRequestHandlesError() {
        whenever(mockAPI.sendFriendRequest("user_789"))
            .thenThrow(RuntimeException("User not found"))
        
        try {
            repository.sendFriendRequest("user_789")
            assertTrue(false)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("User not found") ?: false)
        }
    }
    
    // ===== Cancel Friend Request Tests =====
    
    @Test
    fun testCancelFriendRequestSuccess() {
        whenever(mockAPI.cancelFriendRequest("req_123"))
            .thenReturn(Response.success(Unit))
        
        repository.cancelFriendRequest("req_123")
        
        verify(mockAPI).cancelFriendRequest("req_123")
    }
    
    @Test
    fun testCancelFriendRequestHandlesError() {
        whenever(mockAPI.cancelFriendRequest("req_123"))
            .thenThrow(RuntimeException("Cannot cancel processed request"))
        
        try {
            repository.cancelFriendRequest("req_123")
            assertTrue(false)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Cannot cancel processed request") ?: false)
        }
    }
    
    // ===== Block User Tests =====
    
    @Test
    fun testBlockUserSuccess() {
        whenever(mockAPI.blockUser("user_789"))
            .thenReturn(Response.success(Unit))
        
        repository.blockUser("user_789")
        
        verify(mockAPI).blockUser("user_789")
    }
    
    @Test
    fun testBlockUserHandlesError() {
        whenever(mockAPI.blockUser("user_789"))
            .thenThrow(RuntimeException("User already blocked"))
        
        try {
            repository.blockUser("user_789")
            assertTrue(false)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("User already blocked") ?: false)
        }
    }
    
    // ===== Unblock User Tests =====
    
    @Test
    fun testUnblockUserSuccess() {
        whenever(mockAPI.unblockUser("user_789"))
            .thenReturn(Response.success(Unit))
        
        repository.unblockUser("user_789")
        
        verify(mockAPI).unblockUser("user_789")
    }
    
    @Test
    fun testUnblockUserHandlesError() {
        whenever(mockAPI.unblockUser("user_789"))
            .thenThrow(RuntimeException("User not blocked"))
        
        try {
            repository.unblockUser("user_789")
            assertTrue(false)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("User not blocked") ?: false)
        }
    }
    
    // ===== Batch Operations Tests =====
    
    @Test
    fun testMultipleFriendRequestsHandling() {
        val requests = (1..10).map { i ->
            testFriendRequest.copy(
                id = "req_$i",
                senderId = "user_$i",
                senderUsername = "user_$i"
            )
        }
        
        whenever(mockAPI.getIncomingFriendRequests())
            .thenReturn(Response.success(requests))
        
        val result = repository.getIncomingRequests()
        
        assertEquals(10, result.size)
        assertEquals("req_1", result[0].id)
        assertEquals("req_10", result[9].id)
    }
    
    @Test
    fun testMultipleFriendsHandling() {
        val friends = (1..50).map { i ->
            testFriend.copy(
                id = "user_$i",
                username = "user_$i",
                isActive = i % 2 == 0
            )
        }
        
        whenever(mockAPI.getFriendsList())
            .thenReturn(Response.success(friends))
        
        val result = repository.getFriendsList()
        
        assertEquals(50, result.size)
        
        val activeFriends = result.filter { it.isActive }
        assertEquals(25, activeFriends.size)
    }
}
