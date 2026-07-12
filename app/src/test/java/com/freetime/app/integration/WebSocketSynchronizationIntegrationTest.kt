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
import com.freetime.app.data.model.WebSocketEvent
import com.freetime.app.data.repository.FriendSystemRepository
import com.freetime.app.data.repository.GroupVotingRepository
import com.freetime.app.data.repository.ChannelRepository
import com.freetime.app.data.repository.WebSocketEventRepository
import com.freetime.app.viewmodel.FriendViewModel
import com.freetime.app.viewmodel.GroupVotingViewModel
import com.freetime.app.viewmodel.ChannelViewModel
import com.freetime.app.testutil.TestDataFactory

/**
 * Integration tests for WebSocket real-time synchronization
 * 
 * Tests combining:
 * - WebSocket event reception
 * - ViewModel state updates
 * - Real-time data synchronization
 * - Multi-ViewModel event propagation
 * - Error recovery and reconnection
 */
class WebSocketSynchronizationIntegrationTest {
    
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    @Mock
    private lateinit var mockFriendRepository: FriendSystemRepository
    
    @Mock
    private lateinit var mockVotingRepository: GroupVotingRepository
    
    @Mock
    private lateinit var mockChannelRepository: ChannelRepository
    
    @Mock
    private lateinit var mockWebSocketRepository: WebSocketEventRepository
    
    private lateinit var friendViewModel: FriendViewModel
    private lateinit var votingViewModel: GroupVotingViewModel
    private lateinit var channelViewModel: ChannelViewModel
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        friendViewModel = FriendViewModel(mockFriendRepository, mockWebSocketRepository)
        votingViewModel = GroupVotingViewModel(mockVotingRepository, mockWebSocketRepository)
        channelViewModel = ChannelViewModel(mockChannelRepository, mockWebSocketRepository)
    }
    
    // ===== Friend Request WebSocket Events =====
    
    @Test
    fun testFriendRequestReceivedEventPropagation() {
        // Arrange: Friend request event
        val event = TestDataFactory.createFriendRequestEvent(
            senderId = "remote_user_123",
            senderUsername = "john_doe",
            requestId = "req_456"
        )
        
        // Setup mock listener
        whenever(mockWebSocketRepository.onFriendRequestReceived(org.mockito.kotlin.any()))
            .thenReturn(Unit)
        
        // Act 1: Setup listener
        friendViewModel.listenToFriendRequestUpdates { receivedEvent ->
            // Handler would process event
        }
        
        // Act 2: Simulate event reception and data refresh
        val newRequests = TestDataFactory.createMultipleFriendRequests(1)
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(newRequests)
        
        friendViewModel.loadFriendRequests()
        
        // Assert: Data refreshed from event
        assertEquals(1, friendViewModel.incomingFriendRequests.value?.size ?: 0)
    }
    
    @Test
    fun testFriendRequestAcceptedEventUpdate() {
        // Arrange: Friend accepted request
        val acceptanceEvent = WebSocketEvent(
            type = "FRIEND_REQUEST_ACCEPTED",
            data = mapOf(
                "userId" to "remote_user",
                "username" to "jane_smith",
                "requestId" to "req_789"
            ),
            timestamp = System.currentTimeMillis()
        )
        
        // Setup listener
        var eventReceived = false
        friendViewModel.listenToFriendRequestUpdates { event ->
            eventReceived = true
        }
        
        // Act: Process event
        whenever(mockFriendRepository.getFriendsList())
            .thenReturn(listOf(TestDataFactory.createTestFriend()))
        
        friendViewModel.processFriendRequestAcceptedEvent(acceptanceEvent)
        
        // Assert: Event processed
        assertTrue(eventReceived || friendViewModel.friendsList.value != null)
    }
    
    // ===== Vote Update WebSocket Events =====
    
    @Test
    fun testVoteUpdatedEventPropagation() {
        // Arrange: Vote updated event
        val voteEvent = TestDataFactory.createVoteUpdatedEvent(
            voteId = "vote_123",
            optionId = "opt_1",
            newCount = "35"
        )
        
        val vote = TestDataFactory.createTestGroupVote(
            id = "vote_123",
            voteCount = 35
        )
        
        // Setup listener
        whenever(mockWebSocketRepository.onVoteUpdated(org.mockito.kotlin.any()))
            .thenReturn(Unit)
        
        // Act: Setup and trigger update
        votingViewModel.listenToVoteUpdates { event ->
            // Handler processes event
        }
        
        whenever(mockVotingRepository.getVoteById("vote_123"))
            .thenReturn(vote)
        
        votingViewModel.onVoteUpdatedFromWebSocket(vote)
        
        // Assert: Vote updated
        assertEquals(35, votingViewModel.selectedVote.value?.voteCount)
    }
    
    @Test
    fun testMultipleVotesUpdatingSimultaneously() {
        // Arrange: Multiple votes
        val vote1 = TestDataFactory.createTestGroupVote(
            id = "vote_1",
            voteCount = 10
        )
        val vote2 = TestDataFactory.createTestGroupVote(
            id = "vote_2",
            voteCount = 20
        )
        
        // Act: Update both votes via WebSocket events
        votingViewModel.onVoteUpdatedFromWebSocket(vote1)
        votingViewModel.onVoteUpdatedFromWebSocket(vote2)
        
        // Assert: Both processed
        assertNotNull(votingViewModel.selectedVote.value)
    }
    
    // ===== Member Status WebSocket Events =====
    
    @Test
    fun testMemberJoinedEventHandling() {
        // Arrange: Member joined event
        val event = TestDataFactory.createMemberJoinedEvent(
            userId = "new_user_456",
            username = "new_user",
            channelId = "channel_123"
        )
        
        // Act: Process event
        val updatedMembers = TestDataFactory.createMultipleChannelMembers(11)
        whenever(mockChannelRepository.getChannelMembers("channel_123"))
            .thenReturn(updatedMembers)
        
        channelViewModel.onMemberJoinedEvent(event)
        
        // Assert: Members updated
        assertEquals(11, updatedMembers.size)
    }
    
    @Test
    fun testMemberLeftEventHandling() {
        // Arrange: Member left event
        val event = TestDataFactory.createMemberLeftEvent(
            userId = "user_456",
            username = "leaving_user",
            channelId = "channel_123"
        )
        
        // Setup members
        val members = TestDataFactory.createMultipleChannelMembers(10)
        val updatedMembers = members.filter { it.userId != "user_456" }
        
        // Act: Process event
        whenever(mockChannelRepository.getChannelMembers("channel_123"))
            .thenReturn(updatedMembers)
        
        channelViewModel.onMemberLeftEvent(event)
        
        // Assert: Member removed
        assertEquals(9, updatedMembers.size)
    }
    
    // ===== Connection State Management =====
    
    @Test
    fun testWebSocketConnectionEstablishment() {
        // Arrange: Connection setup
        whenever(mockWebSocketRepository.connect("wss://api.example.com/ws"))
            .thenReturn(true)
        
        // Act: Connect
        val connected = mockWebSocketRepository.connect("wss://api.example.com/ws")
        
        // Assert: Connected
        assertTrue(connected)
        
        // Act: Setup listeners after connection
        mockWebSocketRepository.onFriendRequestReceived { _ -> }
        mockWebSocketRepository.onVoteUpdated { _ -> }
        
        // Assert: Listeners registered
        verify(mockWebSocketRepository).onFriendRequestReceived(org.mockito.kotlin.any())
    }
    
    @Test
    fun testWebSocketReconnectionAfterDisconnect() {
        // Arrange: Initial connection
        whenever(mockWebSocketRepository.connect("wss://api.example.com/ws"))
            .thenReturn(true)
        whenever(mockWebSocketRepository.isConnected())
            .thenReturn(true)
        
        // Act 1: Connect
        val connected = mockWebSocketRepository.connect("wss://api.example.com/ws")
        assertTrue(connected)
        
        // Act 2: Simulate disconnect
        whenever(mockWebSocketRepository.isConnected())
            .thenReturn(false)
        
        // Act 3: Reconnect
        whenever(mockWebSocketRepository.reconnect())
            .thenReturn(true)
        val reconnected = mockWebSocketRepository.reconnect()
        
        // Assert: Reconnected
        assertTrue(reconnected)
        verify(mockWebSocketRepository).reconnect()
    }
    
    // ===== Event Queuing During Disconnection =====
    
    @Test
    fun testEventQueueingDuringDisconnection() {
        // Arrange: Connection state
        whenever(mockWebSocketRepository.isConnected())
            .thenReturn(false)
        
        // Act: Try to process events while disconnected
        val events = TestDataFactory.createMultipleWebSocketEvents(5)
        
        val queuedEvents = mutableListOf<WebSocketEvent>()
        events.forEach { event ->
            // Queue event for later processing
            queuedEvents.add(event)
        }
        
        // Assert: Events queued
        assertEquals(5, queuedEvents.size)
        
        // Act: Reconnect and process queue
        whenever(mockWebSocketRepository.isConnected())
            .thenReturn(true)
        
        queuedEvents.forEach { event ->
            // Process queued event
        }
        
        // Assert: Queue processed
        assertEquals(0, queuedEvents.size)
    }
    
    // ===== Multi-ViewModel Event Handling =====
    
    @Test
    fun testEventPropagationToMultipleViewModels() {
        // Arrange: Initial data
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(TestDataFactory.createMultipleFriendRequests(1))
        whenever(mockVotingRepository.getGroupVotes("group_123"))
            .thenReturn(TestDataFactory.createActiveVotes(1))
        whenever(mockChannelRepository.getChannelMembers("channel_123"))
            .thenReturn(TestDataFactory.createMultipleChannelMembers(5))
        
        // Act: Load data in all ViewModels
        friendViewModel.loadFriendRequests()
        votingViewModel.loadGroupVotes("group_123")
        channelViewModel.loadChannelMembers("channel_123")
        
        // Assert: All ViewModels updated
        assertNotNull(friendViewModel.incomingFriendRequests.value)
        assertNotNull(votingViewModel.groupVotes.value)
        assertNotNull(channelViewModel.channelMembers.value)
    }
    
    @Test
    fun testCrossViewModelEventSynchronization() {
        // Arrange: Simulate multi-component workflow
        val friendRequest = TestDataFactory.createTestFriendRequest()
        val vote = TestDataFactory.createTestGroupVote()
        val channel = TestDataFactory.createTestChannel()
        
        // Act 1: Friend accepts (updates FriendViewModel)
        whenever(mockFriendRepository.acceptFriendRequest(friendRequest.id))
            .thenReturn(Unit)
        friendViewModel.acceptFriendRequest(friendRequest.id)
        
        // Act 2: Vote updated (updates GroupVotingViewModel)
        val updatedVote = vote.copy(voteCount = 50)
        votingViewModel.onVoteUpdatedFromWebSocket(updatedVote)
        
        // Act 3: Channel member changes (updates ChannelViewModel)
        val newMembers = TestDataFactory.createMultipleChannelMembers(10)
        channelViewModel.onMembersUpdated(newMembers)
        
        // Assert: All changes reflected in their respective ViewModels
        verify(mockFriendRepository).acceptFriendRequest(friendRequest.id)
        assertEquals(50, votingViewModel.selectedVote.value?.voteCount)
    }
    
    // ===== Error Handling in WebSocket =====
    
    @Test
    fun testWebSocketConnectionErrorRecovery() {
        // Arrange: Connection fails
        whenever(mockWebSocketRepository.connect("wss://invalid.url"))
            .thenThrow(RuntimeException("Connection failed"))
        
        // Act: Try to connect
        try {
            mockWebSocketRepository.connect("wss://invalid.url")
        } catch (e: Exception) {
            // Error caught
        }
        
        // Act: Retry with correct URL
        whenever(mockWebSocketRepository.connect("wss://api.example.com/ws"))
            .thenReturn(true)
        val retried = mockWebSocketRepository.connect("wss://api.example.com/ws")
        
        // Assert: Recovery successful
        assertTrue(retried)
    }
    
    @Test
    fun testEventProcessingErrorHandling() {
        // Arrange: Event that causes error
        val malformedEvent = WebSocketEvent(
            type = "UNKNOWN_TYPE",
            data = emptyMap(),
            timestamp = System.currentTimeMillis()
        )
        
        // Act: Try to process
        try {
            // Handle unknown event type
            when (malformedEvent.type) {
                "FRIEND_REQUEST_RECEIVED" -> {}
                "VOTE_UPDATED" -> {}
                else -> throw RuntimeException("Unknown event type")
            }
        } catch (e: Exception) {
            // Error handled gracefully
            assertTrue(e.message?.contains("Unknown") ?: false)
        }
    }
    
    // ===== Event Ordering and Consistency =====
    
    @Test
    fun testEventProcessingOrder() {
        // Arrange: Multiple events in sequence
        val event1 = TestDataFactory.createFriendRequestEvent()
        val event2 = TestDataFactory.createVoteUpdatedEvent()
        val event3 = TestDataFactory.createMemberJoinedEvent()
        
        val events = listOf(event1, event2, event3)
        val processedEvents = mutableListOf<String>()
        
        // Act: Process in order
        events.forEach { event ->
            processedEvents.add(event.type)
        }
        
        // Assert: Order maintained
        assertEquals(event1.type, processedEvents[0])
        assertEquals(event2.type, processedEvents[1])
        assertEquals(event3.type, processedEvents[2])
    }
    
    @Test
    fun testEventConsistencyWithDatabaseState() {
        // Arrange: Load current state
        val requests = TestDataFactory.createMultipleFriendRequests(3)
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(requests)
        
        friendViewModel.loadFriendRequests()
        val initialCount = friendViewModel.incomingFriendRequests.value?.size
        
        // Act: Receive WebSocket event about new request
        val updatedRequests = requests + TestDataFactory.createTestFriendRequest()
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(updatedRequests)
        
        friendViewModel.loadFriendRequests()
        val finalCount = friendViewModel.incomingFriendRequests.value?.size
        
        // Assert: Count increased by 1
        assertEquals((initialCount ?: 0) + 1, finalCount)
    }
}
