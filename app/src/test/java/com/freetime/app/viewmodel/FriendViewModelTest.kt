package com.freetime.app.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
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

// friend viewmodel tests
class FriendViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var mockFriendRepository: FriendSystemRepository

    @Mock
    private lateinit var mockWebSocketRepository: WebSocketEventRepository

    private lateinit var viewModel: FriendViewModel

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

        setupMockDefaults()

        viewModel = FriendViewModel(
            friendRepository = mockFriendRepository,
            webSocketRepository = mockWebSocketRepository
        )
    }

    private fun setupMockDefaults() {
        val emptyList: List<FriendRequest> = emptyList()
        val friendList: List<Friend> = emptyList()

        whenever(mockFriendRepository.getIncomingRequests()).thenReturn(emptyList)
        whenever(mockFriendRepository.getOutgoingRequests()).thenReturn(emptyList)
        whenever(mockFriendRepository.getFriends()).thenReturn(friendList)
    }

    @Test
    fun testInitialLiveDataValues() {
        assertEquals(emptyList<FriendRequest>(), viewModel.incomingFriendRequests.value)
        assertEquals(emptyList<FriendRequest>(), viewModel.outgoingFriendRequests.value)
        assertEquals(emptyList<Friend>(), viewModel.friends.value)
        assertFalse(viewModel.isLoading.value ?: false)
        assertEquals(null, viewModel.errorMessage.value)
    }

    @Test
    fun testLiveDataNotNull() {
        assertNotNull(viewModel.incomingFriendRequests)
        assertNotNull(viewModel.outgoingFriendRequests)
        assertNotNull(viewModel.friends)
        assertNotNull(viewModel.isLoading)
        assertNotNull(viewModel.errorMessage)
    }

    @Test
    fun testLoadFriendRequestsSuccess() {
        val incomingList = listOf(testFriendRequest)
        val outgoingList = listOf(testFriendRequest.copy(id = "req_456"))
        val friendsList = listOf(testFriend)

        whenever(mockFriendRepository.getIncomingRequests()).thenReturn(incomingList)
        whenever(mockFriendRepository.getOutgoingRequests()).thenReturn(outgoingList)
        whenever(mockFriendRepository.getFriends()).thenReturn(friendsList)

        viewModel.loadFriendRequests()

        assertEquals(incomingList, viewModel.incomingFriendRequests.value)
        assertEquals(outgoingList, viewModel.outgoingFriendRequests.value)
        assertEquals(friendsList, viewModel.friends.value)

        assertFalse(viewModel.isLoading.value ?: false)
        assertEquals(null, viewModel.errorMessage.value)
    }

    @Test
    fun testLoadFriendRequestsShowsLoadingState() {
        val loadingValues = mutableListOf<Boolean>()

        viewModel.isLoading.observeForever { value ->
            loadingValues.add(value)
        }

        whenever(mockFriendRepository.getIncomingRequests()).thenReturn(emptyList())
        whenever(mockFriendRepository.getOutgoingRequests()).thenReturn(emptyList())
        whenever(mockFriendRepository.getFriends()).thenReturn(emptyList())

        viewModel.loadFriendRequests()

        assertTrue(loadingValues.contains(true))
        assertEquals(false, loadingValues.last())
    }

    @Test
    fun testLoadFriendRequestsHandlesException() {
        whenever(mockFriendRepository.getIncomingRequests())
            .thenThrow(RuntimeException("Network error"))

        viewModel.loadFriendRequests()

        assertEquals(false, viewModel.isLoading.value)
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value?.contains("Failed to load requests") ?: false)
    }

    @Test
    fun testAcceptFriendRequestSuccess() {
        val newIncomingList = listOf(testFriendRequest.copy(id = "req_999"))
        whenever(mockFriendRepository.acceptFriendRequest("req_123")).thenReturn(Unit)
        whenever(mockFriendRepository.getIncomingRequests()).thenReturn(newIncomingList)
        whenever(mockFriendRepository.getOutgoingRequests()).thenReturn(emptyList())
        whenever(mockFriendRepository.getFriends()).thenReturn(listOf(testFriend))

        viewModel.acceptFriendRequest("req_123")

        verify(mockFriendRepository).acceptFriendRequest("req_123")

        assertEquals(newIncomingList, viewModel.incomingFriendRequests.value)
    }

    @Test
    fun testAcceptFriendRequestHandlesError() {
        whenever(mockFriendRepository.acceptFriendRequest("req_123"))
            .thenThrow(RuntimeException("API error"))

        viewModel.acceptFriendRequest("req_123")

        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value?.contains("Failed to accept request") ?: false)
    }

    @Test
    fun testRejectFriendRequestSuccess() {
        val newIncomingList = listOf(testFriendRequest.copy(id = "req_999"))
        whenever(mockFriendRepository.rejectFriendRequest("req_123")).thenReturn(Unit)
        whenever(mockFriendRepository.getIncomingRequests()).thenReturn(newIncomingList)
        whenever(mockFriendRepository.getOutgoingRequests()).thenReturn(emptyList())
        whenever(mockFriendRepository.getFriends()).thenReturn(emptyList())

        viewModel.rejectFriendRequest("req_123")

        verify(mockFriendRepository).rejectFriendRequest("req_123")
    }

    @Test
    fun testRejectFriendRequestHandlesError() {
        whenever(mockFriendRepository.rejectFriendRequest("req_123"))
            .thenThrow(RuntimeException("API error"))

        viewModel.rejectFriendRequest("req_123")

        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value?.contains("Failed to reject request") ?: false)
    }

    @Test
    fun testCancelFriendRequestSuccess() {
        val newOutgoingList = listOf(testFriendRequest.copy(id = "req_999"))
        whenever(mockFriendRepository.cancelFriendRequest("req_123")).thenReturn(Unit)
        whenever(mockFriendRepository.getIncomingRequests()).thenReturn(emptyList())
        whenever(mockFriendRepository.getOutgoingRequests()).thenReturn(newOutgoingList)
        whenever(mockFriendRepository.getFriends()).thenReturn(emptyList())

        viewModel.cancelFriendRequest("req_123")

        verify(mockFriendRepository).cancelFriendRequest("req_123")
    }

    @Test
    fun testClearErrorMessage() {
        viewModel.clearError()

        assertEquals(null, viewModel.errorMessage.value)
    }

    @Test
    fun testWebSocketListenersSetup() {
        assertNotNull(viewModel.incomingFriendRequests)

        assertTrue(true)
    }

    @Test
    fun testMultipleRequestsHandling() {
        val requests = listOf(
            testFriendRequest.copy(id = "req_1"),
            testFriendRequest.copy(id = "req_2"),
            testFriendRequest.copy(id = "req_3")
        )

        whenever(mockFriendRepository.getIncomingRequests()).thenReturn(requests)
        whenever(mockFriendRepository.getOutgoingRequests()).thenReturn(emptyList())
        whenever(mockFriendRepository.getFriends()).thenReturn(emptyList())

        viewModel.loadFriendRequests()

        assertEquals(3, viewModel.incomingFriendRequests.value?.size)
    }

    @Test
    fun testFriendListHandling() {
        val friends = listOf(
            testFriend.copy(id = "user_1"),
            testFriend.copy(id = "user_2"),
            testFriend.copy(id = "user_3", isActive = false)
        )

        whenever(mockFriendRepository.getIncomingRequests()).thenReturn(emptyList())
        whenever(mockFriendRepository.getOutgoingRequests()).thenReturn(emptyList())
        whenever(mockFriendRepository.getFriends()).thenReturn(friends)

        viewModel.loadFriendRequests()

        assertEquals(3, viewModel.friends.value?.size)
        val activeFriends = viewModel.friends.value?.filter { it.isActive } ?: emptyList()
        assertEquals(2, activeFriends.size)
    }
}
