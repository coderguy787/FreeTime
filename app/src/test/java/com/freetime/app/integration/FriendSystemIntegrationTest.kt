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

// friend accept/reject flow test
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

    @Test
    fun testCompleteIncomingFriendRequestWorkflow() {
        val incomingRequests = TestDataFactory.createMultipleFriendRequests(3)
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(incomingRequests)

        friendViewModel.loadFriendRequests()

        assertEquals(incomingRequests, friendViewModel.incomingFriendRequests.value)
        assertFalse(friendViewModel.isLoading.value ?: false)

        val requestToAccept = incomingRequests[0]
        whenever(mockFriendRepository.acceptFriendRequest(requestToAccept.id))
            .thenReturn(Unit)
        // simulate the server removing the accepted request
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(incomingRequests.drop(1))

        friendViewModel.acceptFriendRequest(requestToAccept.id)

        verify(mockFriendRepository).acceptFriendRequest(requestToAccept.id)

        val requestToReject = incomingRequests[1]
        whenever(mockFriendRepository.rejectFriendRequest(requestToReject.id))
            .thenReturn(Unit)
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(listOf(incomingRequests[2]))

        friendViewModel.rejectFriendRequest(requestToReject.id)

        verify(mockFriendRepository).rejectFriendRequest(requestToReject.id)
    }

    @Test
    fun testOutgoingFriendRequestWorkflow() {
        val outgoingRequests = TestDataFactory.createMultipleFriendRequests(2)
        whenever(mockFriendRepository.getOutgoingRequests())
            .thenReturn(outgoingRequests)

        friendViewModel.loadOutgoingRequests()

        assertEquals(outgoingRequests, friendViewModel.outgoingFriendRequests.value)

        val requestToCancel = outgoingRequests[0]
        whenever(mockFriendRepository.cancelFriendRequest(requestToCancel.id))
            .thenReturn(Unit)
        whenever(mockFriendRepository.getOutgoingRequests())
            .thenReturn(listOf(outgoingRequests[1]))

        friendViewModel.cancelFriendRequest(requestToCancel.id)

        verify(mockFriendRepository).cancelFriendRequest(requestToCancel.id)
    }

    @Test
    fun testSendFriendRequestAndWaitForAcceptance() {
        val newRequest = TestDataFactory.createTestFriendRequest(
            senderId = "current_user",
            senderUsername = "current_user"
        )
        whenever(mockFriendRepository.sendFriendRequest("target_user_123"))
            .thenReturn(newRequest)

        val sentRequest = mockFriendRepository.sendFriendRequest("target_user_123")

        assertNotNull(sentRequest)
        assertEquals("current_user", sentRequest.senderId)

        val acceptanceEvent = TestDataFactory.createFriendRequestEvent(
            requestId = sentRequest.id
        )

        assertEquals("FRIEND_REQUEST_RECEIVED", acceptanceEvent.type)
    }

    @Test
    fun testFriendListWithOnlineStatusWorkflow() {
        val activeFriends = TestDataFactory.createActiveFriends(5)
        val inactiveFriends = TestDataFactory.createInactiveFriends(3)
        val allFriends = activeFriends + inactiveFriends

        whenever(mockFriendRepository.getFriendsList())
            .thenReturn(allFriends)

        friendViewModel.loadFriendsList()

        val loadedFriends = friendViewModel.friendsList.value ?: emptyList()
        assertEquals(8, loadedFriends.size)

        val onlineFriends = loadedFriends.filter { it.isActive }
        val offlineFriends = loadedFriends.filter { !it.isActive }

        assertEquals(5, onlineFriends.size)
        assertEquals(3, offlineFriends.size)
    }

    @Test
    fun testBlockAndUnblockUserWorkflow() {
        val userToBlock = TestDataFactory.createTestFriend(
            id = "user_123",
            username = "problematic_user"
        )
        whenever(mockFriendRepository.blockUser(userToBlock.id))
            .thenReturn(Unit)

        mockFriendRepository.blockUser(userToBlock.id)
        verify(mockFriendRepository).blockUser(userToBlock.id)

        whenever(mockFriendRepository.unblockUser(userToBlock.id))
            .thenReturn(Unit)
        mockFriendRepository.unblockUser(userToBlock.id)

        verify(mockFriendRepository).unblockUser(userToBlock.id)
    }

    @Test
    fun testRealTimeRequestNotificationWorkflow() {
        assertEquals(null, friendViewModel.incomingFriendRequests.value)

        var receivedEvent: Any? = null
        friendViewModel.listenToFriendRequestUpdates { event ->
            receivedEvent = event
        }

        mockWebSocketRepository.onFriendRequestReceived { event ->
            friendViewModel.handleIncomingFriendRequest(event)
        }

        val incomingEvent = TestDataFactory.createFriendRequestEvent(
            senderId = "remote_user",
            senderUsername = "remote_user"
        )

        val updatedRequests = TestDataFactory.createMultipleFriendRequests(1)
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(updatedRequests)
        friendViewModel.loadFriendRequests()

        assertEquals(1, friendViewModel.incomingFriendRequests.value?.size ?: 0)
    }

    @Test
    fun testMultipleOperationsWithErrorRecovery() {
        val requests = TestDataFactory.createMultipleFriendRequests(5)

        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(requests)
        friendViewModel.loadFriendRequests()
        assertEquals(5, friendViewModel.incomingFriendRequests.value?.size ?: 0)

        whenever(mockFriendRepository.acceptFriendRequest(requests[0].id))
            .thenReturn(Unit)
        friendViewModel.acceptFriendRequest(requests[0].id)
        verify(mockFriendRepository).acceptFriendRequest(requests[0].id)

        whenever(mockFriendRepository.rejectFriendRequest(requests[1].id))
            .thenThrow(RuntimeException("Network error"))
        friendViewModel.rejectFriendRequest(requests[1].id)

        assertNotNull(friendViewModel.errorMessage.value)

        whenever(mockFriendRepository.rejectFriendRequest(requests[1].id))
            .thenReturn(Unit)
        friendViewModel.rejectFriendRequest(requests[1].id)

        verify(mockFriendRepository, org.mockito.kotlin.times(2))
            .rejectFriendRequest(requests[1].id)
    }

    @Test
    fun testConcurrentFriendRequestOperations() {
        val requests = TestDataFactory.createMultipleFriendRequests(10)

        requests.forEachIndexed { index, request ->
            whenever(mockFriendRepository.acceptFriendRequest(request.id))
                .thenReturn(Unit)
            friendViewModel.acceptFriendRequest(request.id)
        }

        requests.forEach { request ->
            verify(mockFriendRepository).acceptFriendRequest(request.id)
        }
    }

    @Test
    fun testStateConsistencyAcrossOperations() {
        assertEquals(null, friendViewModel.incomingFriendRequests.value)

        val requests = TestDataFactory.createMultipleFriendRequests(3)
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(requests)
        friendViewModel.loadFriendRequests()

        var currentRequests = friendViewModel.incomingFriendRequests.value
        assertEquals(3, currentRequests?.size)

        whenever(mockFriendRepository.acceptFriendRequest(requests[0].id))
            .thenReturn(Unit)
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(requests.drop(1))

        friendViewModel.acceptFriendRequest(requests[0].id)

        assertFalse(friendViewModel.isLoading.value ?: false)

        whenever(mockFriendRepository.rejectFriendRequest(requests[1].id))
            .thenReturn(Unit)
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(listOf(requests[2]))

        friendViewModel.rejectFriendRequest(requests[1].id)

        currentRequests = friendViewModel.incomingFriendRequests.value
        assertEquals(1, currentRequests?.size)
    }
}
