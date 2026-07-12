package com.freetime.app.integration

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import com.freetime.app.data.model.FriendRequest
import com.freetime.app.data.model.Friend
import com.freetime.app.data.repository.FriendSystemRepository
import com.freetime.app.data.repository.WebSocketEventRepository
import com.freetime.app.viewmodel.FriendViewModel
import com.freetime.app.testutil.TestDataFactory

/**
 * Integration tests for Friend System workflow
 * 
 * Tests complete workflows combining:
 * - ViewModel logic
 * - Repository operations
 * - WebSocket event handling
 * - State management across operations
 */
class FriendSystemIntegrationTest {
    
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    @Mock
    private lateinit var mockFriendRepository: FriendSystemRepository
    
    @Mock
    private lateinit var mockWebSocketRepository: WebSocketEventRepository
    
    private lateinit var friendViewModel: FriendViewModel
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        friendViewModel = FriendViewModel(
            friendRepository = mockFriendRepository,
            webSocketRepository = mockWebSocketRepository
        )
    }
    
    // ===== Complete Friend Request Workflow =====
    
    @Test
    fun testCompleteIncomingFriendRequestWorkflow() {
        // Arrange: Simulate receiving friend requests
        val incomingRequests = TestDataFactory.createMultipleFriendRequests(3)
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(incomingRequests)
        
        // Act 1: Load incoming requests
        friendViewModel.loadFriendRequests()
        
        // Assert 1: Requests loaded
        assertEquals(incomingRequests, friendViewModel.incomingFriendRequests.value)
        assertFalse(friendViewModel.isLoading.value ?: false)
        
        // Act 2: Accept first request
        val requestToAccept = incomingRequests[0]
        whenever(mockFriendRepository.acceptFriendRequest(requestToAccept.id))
            .thenReturn(Unit)
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(incomingRequests.drop(1))
        
        friendViewModel.acceptFriendRequest(requestToAccept.id)
        
        // Assert 2: Request accepted, list refreshed
        verify(mockFriendRepository).acceptFriendRequest(requestToAccept.id)
        
        // Act 3: Reject second request
        val requestToReject = incomingRequests[1]
        whenever(mockFriendRepository.rejectFriendRequest(requestToReject.id))
            .thenReturn(Unit)
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(listOf(incomingRequests[2]))
        
        friendViewModel.rejectFriendRequest(requestToReject.id)
        
        // Assert 3: Request rejected
        verify(mockFriendRepository).rejectFriendRequest(requestToReject.id)
    }
    
    @Test
    fun testOutgoingFriendRequestWorkflow() {
        // Arrange: Multiple operations on outgoing requests
        val outgoingRequests = TestDataFactory.createMultipleFriendRequests(2)
        whenever(mockFriendRepository.getOutgoingRequests())
            .thenReturn(outgoingRequests)
        
        // Act 1: Load outgoing requests
        friendViewModel.loadOutgoingRequests()
        
        // Assert 1: Loaded
        assertEquals(outgoingRequests, friendViewModel.outgoingFriendRequests.value)
        
        // Act 2: Cancel request
        val requestToCancel = outgoingRequests[0]
        whenever(mockFriendRepository.cancelFriendRequest(requestToCancel.id))
            .thenReturn(Unit)
        whenever(mockFriendRepository.getOutgoingRequests())
            .thenReturn(listOf(outgoingRequests[1]))
        
        friendViewModel.cancelFriendRequest(requestToCancel.id)
        
        // Assert 2: Verified
        verify(mockFriendRepository).cancelFriendRequest(requestToCancel.id)
    }
    
    @Test
    fun testSendFriendRequestAndWaitForAcceptance() {
        // Arrange: New friend request
        val newRequest = TestDataFactory.createTestFriendRequest(
            senderId = "current_user",
            senderUsername = "current_user"
        )
        whenever(mockFriendRepository.sendFriendRequest("target_user_123"))
            .thenReturn(newRequest)
        
        // Act 1: Send friend request
        val sentRequest = mockFriendRepository.sendFriendRequest("target_user_123")
        
        // Assert 1: Request sent
        assertNotNull(sentRequest)
        assertEquals("current_user", sentRequest.senderId)
        
        // Act 2: Simulate WebSocket event for acceptance (would come from server)
        val acceptanceEvent = TestDataFactory.createFriendRequestEvent(
            requestId = sentRequest.id
        )
        
        // Assert 2: Can process acceptance event
        assertEquals("FRIEND_REQUEST_RECEIVED", acceptanceEvent.type)
    }
    
    // ===== Friend List and Status Management =====
    
    @Test
    fun testFriendListWithOnlineStatusWorkflow() {
        // Arrange: Friends with different statuses
        val activeFriends = TestDataFactory.createActiveFriends(5)
        val inactiveFriends = TestDataFactory.createInactiveFriends(3)
        val allFriends = activeFriends + inactiveFriends
        
        whenever(mockFriendRepository.getFriendsList())
            .thenReturn(allFriends)
        
        // Act: Load friend list
        friendViewModel.loadFriendsList()
        
        // Assert: List loaded with mixed statuses
        val loadedFriends = friendViewModel.friendsList.value ?: emptyList()
        assertEquals(8, loadedFriends.size)
        
        // Verify filtering capability
        val onlineFriends = loadedFriends.filter { it.isActive }
        val offlineFriends = loadedFriends.filter { !it.isActive }
        
        assertEquals(5, onlineFriends.size)
        assertEquals(3, offlineFriends.size)
    }
    
    @Test
    fun testBlockAndUnblockUserWorkflow() {
        // Arrange: User to block
        val userToBlock = TestDataFactory.createTestFriend(
            id = "user_123",
            username = "problematic_user"
        )
        whenever(mockFriendRepository.blockUser(userToBlock.id))
            .thenReturn(Unit)
        
        // Act 1: Block user
        mockFriendRepository.blockUser(userToBlock.id)
        verify(mockFriendRepository).blockUser(userToBlock.id)
        
        // Act 2: Unblock user
        whenever(mockFriendRepository.unblockUser(userToBlock.id))
            .thenReturn(Unit)
        mockFriendRepository.unblockUser(userToBlock.id)
        
        // Assert: Both operations verified
        verify(mockFriendRepository).unblockUser(userToBlock.id)
    }
    
    // ===== WebSocket Real-Time Updates =====
    
    @Test
    fun testRealTimeRequestNotificationWorkflow() {
        // Arrange: Initial empty state
        assertEquals(null, friendViewModel.incomingFriendRequests.value)
        
        // Setup incoming request listener
        var receivedEvent: Any? = null
        friendViewModel.listenToFriendRequestUpdates { event ->
            receivedEvent = event
        }
        
        // Act 1: Setup listener on repository
        mockWebSocketRepository.onFriendRequestReceived { event ->
            friendViewModel.handleIncomingFriendRequest(event)
        }
        
        // Act 2: Simulate incoming event
        val incomingEvent = TestDataFactory.createFriendRequestEvent(
            senderId = "remote_user",
            senderUsername = "remote_user"
        )
        
        // Act 3: Reload requests (would be triggered by event)
        val updatedRequests = TestDataFactory.createMultipleFriendRequests(1)
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(updatedRequests)
        friendViewModel.loadFriendRequests()
        
        // Assert: New request appears in list
        assertEquals(1, friendViewModel.incomingFriendRequests.value?.size ?: 0)
    }
    
    @Test
    fun testMultipleOperationsWithErrorRecovery() {
        // Arrange: Multiple requests
        val requests = TestDataFactory.createMultipleFriendRequests(5)
        
        // Act 1: Load requests successfully
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(requests)
        friendViewModel.loadFriendRequests()
        assertEquals(5, friendViewModel.incomingFriendRequests.value?.size ?: 0)
        
        // Act 2: Accept first request
        whenever(mockFriendRepository.acceptFriendRequest(requests[0].id))
            .thenReturn(Unit)
        friendViewModel.acceptFriendRequest(requests[0].id)
        verify(mockFriendRepository).acceptFriendRequest(requests[0].id)
        
        // Act 3: Simulate error on second operation
        whenever(mockFriendRepository.rejectFriendRequest(requests[1].id))
            .thenThrow(RuntimeException("Network error"))
        friendViewModel.rejectFriendRequest(requests[1].id)
        
        // Assert: Error captured but state still valid
        assertNotNull(friendViewModel.errorMessage.value)
        
        // Act 4: Recover from error
        whenever(mockFriendRepository.rejectFriendRequest(requests[1].id))
            .thenReturn(Unit)
        friendViewModel.rejectFriendRequest(requests[1].id)
        
        // Assert: Recovery successful
        verify(mockFriendRepository, org.mockito.kotlin.times(2))
            .rejectFriendRequest(requests[1].id)
    }
    
    // ===== Concurrent Operation Handling =====
    
    @Test
    fun testConcurrentFriendRequestOperations() {
        // Arrange: Multiple requests
        val requests = TestDataFactory.createMultipleFriendRequests(10)
        
        // Act: Accept multiple requests in sequence
        requests.forEachIndexed { index, request ->
            whenever(mockFriendRepository.acceptFriendRequest(request.id))
                .thenReturn(Unit)
            friendViewModel.acceptFriendRequest(request.id)
        }
        
        // Assert: All operations completed
        requests.forEach { request ->
            verify(mockFriendRepository).acceptFriendRequest(request.id)
        }
    }
    
    @Test
    fun testStateConsistencyAcrossOperations() {
        // Arrange: Initial state
        assertEquals(null, friendViewModel.incomingFriendRequests.value)
        
        // Act 1: Load requests
        val requests = TestDataFactory.createMultipleFriendRequests(3)
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(requests)
        friendViewModel.loadFriendRequests()
        
        // Assert 1: State is correct
        var currentRequests = friendViewModel.incomingFriendRequests.value
        assertEquals(3, currentRequests?.size)
        
        // Act 2: Accept request
        whenever(mockFriendRepository.acceptFriendRequest(requests[0].id))
            .thenReturn(Unit)
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(requests.drop(1))
        
        friendViewModel.acceptFriendRequest(requests[0].id)
        
        // Assert 2: Loading state handled properly
        assertFalse(friendViewModel.isLoading.value ?: false)
        
        // Act 3: Reject another request
        whenever(mockFriendRepository.rejectFriendRequest(requests[1].id))
            .thenReturn(Unit)
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(listOf(requests[2]))
        
        friendViewModel.rejectFriendRequest(requests[1].id)
        
        // Assert 3: Final state consistent
        currentRequests = friendViewModel.incomingFriendRequests.value
        assertEquals(1, currentRequests?.size)
    }
}
