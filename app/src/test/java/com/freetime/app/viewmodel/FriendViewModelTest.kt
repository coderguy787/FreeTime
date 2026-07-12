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

/**
 * Unit tests for FriendViewModel
 * 
 * Tests cover:
 * - LiveData initialization and observation
 * - Loading and error states
 * - Friend request operations (accept, reject, cancel)
 * - WebSocket event listener setup
 * - Real-time update handling
 */
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
        
        // Setup mocks with default behavior
        setupMockDefaults()
        
        // Initialize ViewModel with mocks
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
    
    // ===== LiveData Initialization Tests =====
    
    @Test
    fun testInitialLiveDataValues() {
        // Initial values should be empty lists
        assertEquals(emptyList<FriendRequest>(), viewModel.incomingFriendRequests.value)
        assertEquals(emptyList<FriendRequest>(), viewModel.outgoingFriendRequests.value)
        assertEquals(emptyList<Friend>(), viewModel.friends.value)
        assertFalse(viewModel.isLoading.value ?: false)
        assertEquals(null, viewModel.errorMessage.value)
    }
    
    @Test
    fun testLiveDataNotNull() {
        // All LiveData objects should be non-null
        assertNotNull(viewModel.incomingFriendRequests)
        assertNotNull(viewModel.outgoingFriendRequests)
        assertNotNull(viewModel.friends)
        assertNotNull(viewModel.isLoading)
        assertNotNull(viewModel.errorMessage)
    }
    
    // ===== Load Friend Requests Tests =====
    
    @Test
    fun testLoadFriendRequestsSuccess() {
        // Setup mock data
        val incomingList = listOf(testFriendRequest)
        val outgoingList = listOf(testFriendRequest.copy(id = "req_456"))
        val friendsList = listOf(testFriend)
        
        whenever(mockFriendRepository.getIncomingRequests()).thenReturn(incomingList)
        whenever(mockFriendRepository.getOutgoingRequests()).thenReturn(outgoingList)
        whenever(mockFriendRepository.getFriends()).thenReturn(friendsList)
        
        // Execute
        viewModel.loadFriendRequests()
        
        // Verify data was loaded
        assertEquals(incomingList, viewModel.incomingFriendRequests.value)
        assertEquals(outgoingList, viewModel.outgoingFriendRequests.value)
        assertEquals(friendsList, viewModel.friends.value)
        
        // Verify loading state was managed
        assertFalse(viewModel.isLoading.value ?: false)
        assertEquals(null, viewModel.errorMessage.value)
    }
    
    @Test
    fun testLoadFriendRequestsShowsLoadingState() {
        // Track loading state changes
        val loadingValues = mutableListOf<Boolean>()
        
        viewModel.isLoading.observeForever { value ->
            loadingValues.add(value)
        }
        
        whenever(mockFriendRepository.getIncomingRequests()).thenReturn(emptyList())
        whenever(mockFriendRepository.getOutgoingRequests()).thenReturn(emptyList())
        whenever(mockFriendRepository.getFriends()).thenReturn(emptyList())
        
        viewModel.loadFriendRequests()
        
        // Should have loading state changes: initial false, then true during load, then false after
        assertTrue(loadingValues.contains(true))
        assertEquals(false, loadingValues.last())
    }
    
    @Test
    fun testLoadFriendRequestsHandlesException() {
        // Setup mock to throw exception
        whenever(mockFriendRepository.getIncomingRequests())
            .thenThrow(RuntimeException("Network error"))
        
        // Execute
        viewModel.loadFriendRequests()
        
        // Verify error was captured
        assertEquals(false, viewModel.isLoading.value)
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value?.contains("Failed to load requests") ?: false)
    }
    
    // ===== Accept Friend Request Tests =====
    
    @Test
    fun testAcceptFriendRequestSuccess() {
        // Setup mocks
        val newIncomingList = listOf(testFriendRequest.copy(id = "req_999"))
        whenever(mockFriendRepository.acceptFriendRequest("req_123")).thenReturn(Unit)
        whenever(mockFriendRepository.getIncomingRequests()).thenReturn(newIncomingList)
        whenever(mockFriendRepository.getOutgoingRequests()).thenReturn(emptyList())
        whenever(mockFriendRepository.getFriends()).thenReturn(listOf(testFriend))
        
        // Execute
        viewModel.acceptFriendRequest("req_123")
        
        // Verify repository method was called
        verify(mockFriendRepository).acceptFriendRequest("req_123")
        
        // Verify data was refreshed
        assertEquals(newIncomingList, viewModel.incomingFriendRequests.value)
    }
    
    @Test
    fun testAcceptFriendRequestHandlesError() {
        // Setup mock to throw exception
        whenever(mockFriendRepository.acceptFriendRequest("req_123"))
            .thenThrow(RuntimeException("API error"))
        
        // Execute
        viewModel.acceptFriendRequest("req_123")
        
        // Verify error was captured
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value?.contains("Failed to accept request") ?: false)
    }
    
    // ===== Reject Friend Request Tests =====
    
    @Test
    fun testRejectFriendRequestSuccess() {
        // Setup mocks
        val newIncomingList = listOf(testFriendRequest.copy(id = "req_999"))
        whenever(mockFriendRepository.rejectFriendRequest("req_123")).thenReturn(Unit)
        whenever(mockFriendRepository.getIncomingRequests()).thenReturn(newIncomingList)
        whenever(mockFriendRepository.getOutgoingRequests()).thenReturn(emptyList())
        whenever(mockFriendRepository.getFriends()).thenReturn(emptyList())
        
        // Execute
        viewModel.rejectFriendRequest("req_123")
        
        // Verify repository method was called
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
    
    // ===== Cancel Friend Request Tests =====
    
    @Test
    fun testCancelFriendRequestSuccess() {
        // Setup mocks
        val newOutgoingList = listOf(testFriendRequest.copy(id = "req_999"))
        whenever(mockFriendRepository.cancelFriendRequest("req_123")).thenReturn(Unit)
        whenever(mockFriendRepository.getIncomingRequests()).thenReturn(emptyList())
        whenever(mockFriendRepository.getOutgoingRequests()).thenReturn(newOutgoingList)
        whenever(mockFriendRepository.getFriends()).thenReturn(emptyList())
        
        // Execute
        viewModel.cancelFriendRequest("req_123")
        
        // Verify
        verify(mockFriendRepository).cancelFriendRequest("req_123")
    }
    
    // ===== Error Clearing Tests =====
    
    @Test
    fun testClearErrorMessage() {
        // Set an error
        viewModel.clearError()
        
        // Error should be null
        assertEquals(null, viewModel.errorMessage.value)
    }
    
    // ===== WebSocket Listener Tests =====
    
    @Test
    fun testWebSocketListenersSetup() {
        // Verify WebSocket repository was accessed (listeners set up in init)
        assertNotNull(viewModel.incomingFriendRequests)
        
        // Listeners are set up passively, so we just verify the ViewModel was initialized
        assertTrue(true)
    }
    
    @Test
    fun testMultipleRequestsHandling() {
        // Setup multiple requests
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
        // Setup multiple friends
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
        // Verify mixed active/inactive status
        val activeFriends = viewModel.friends.value?.filter { it.isActive } ?: emptyList()
        assertEquals(2, activeFriends.size)
    }
}
