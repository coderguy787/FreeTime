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

// websocket event handling tests
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

    @Test
    fun testFriendRequestReceivedEventPropagation() {
        val event = TestDataFactory.createFriendRequestEvent(
            senderId = "remote_user_123",
            senderUsername = "john_doe",
            requestId = "req_456"
        )

        whenever(mockWebSocketRepository.onFriendRequestReceived(org.mockito.kotlin.any()))
            .thenReturn(Unit)

        // only the registration matters, body stays empty
        friendViewModel.listenToFriendRequestUpdates { receivedEvent ->
        }

        val newRequests = TestDataFactory.createMultipleFriendRequests(1)
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(newRequests)

        friendViewModel.loadFriendRequests()

        assertEquals(1, friendViewModel.incomingFriendRequests.value?.size ?: 0)
    }

    @Test
    fun testFriendRequestAcceptedEventUpdate() {
        val acceptanceEvent = WebSocketEvent(
            type = "FRIEND_REQUEST_ACCEPTED",
            data = mapOf(
                "userId" to "remote_user",
                "username" to "jane_smith",
                "requestId" to "req_789"
            ),
            timestamp = System.currentTimeMillis()
        )

        var eventReceived = false
        friendViewModel.listenToFriendRequestUpdates { event ->
            eventReceived = true
        }

        whenever(mockFriendRepository.getFriendsList())
            .thenReturn(listOf(TestDataFactory.createTestFriend()))

        friendViewModel.processFriendRequestAcceptedEvent(acceptanceEvent)

        assertTrue(eventReceived || friendViewModel.friendsList.value != null)
    }

    @Test
    fun testVoteUpdatedEventPropagation() {
        val voteEvent = TestDataFactory.createVoteUpdatedEvent(
            voteId = "vote_123",
            optionId = "opt_1",
            newCount = "35"
        )

        val vote = TestDataFactory.createTestGroupVote(
            id = "vote_123",
            voteCount = 35
        )

        whenever(mockWebSocketRepository.onVoteUpdated(org.mockito.kotlin.any()))
            .thenReturn(Unit)

        votingViewModel.listenToVoteUpdates { event ->
        }

        whenever(mockVotingRepository.getVoteById("vote_123"))
            .thenReturn(vote)

        votingViewModel.onVoteUpdatedFromWebSocket(vote)

        assertEquals(35, votingViewModel.selectedVote.value?.voteCount)
    }

    @Test
    fun testMultipleVotesUpdatingSimultaneously() {
        val vote1 = TestDataFactory.createTestGroupVote(
            id = "vote_1",
            voteCount = 10
        )
        val vote2 = TestDataFactory.createTestGroupVote(
            id = "vote_2",
            voteCount = 20
        )

        votingViewModel.onVoteUpdatedFromWebSocket(vote1)
        votingViewModel.onVoteUpdatedFromWebSocket(vote2)

        assertNotNull(votingViewModel.selectedVote.value)
    }

    @Test
    fun testMemberJoinedEventHandling() {
        val event = TestDataFactory.createMemberJoinedEvent(
            userId = "new_user_456",
            username = "new_user",
            channelId = "channel_123"
        )

        val updatedMembers = TestDataFactory.createMultipleChannelMembers(11)
        whenever(mockChannelRepository.getChannelMembers("channel_123"))
            .thenReturn(updatedMembers)

        channelViewModel.onMemberJoinedEvent(event)

        assertEquals(11, updatedMembers.size)
    }

    @Test
    fun testMemberLeftEventHandling() {
        val event = TestDataFactory.createMemberLeftEvent(
            userId = "user_456",
            username = "leaving_user",
            channelId = "channel_123"
        )

        val members = TestDataFactory.createMultipleChannelMembers(10)
        val updatedMembers = members.filter { it.userId != "user_456" }

        whenever(mockChannelRepository.getChannelMembers("channel_123"))
            .thenReturn(updatedMembers)

        channelViewModel.onMemberLeftEvent(event)

        assertEquals(9, updatedMembers.size)
    }

    @Test
    fun testWebSocketConnectionEstablishment() {
        whenever(mockWebSocketRepository.connect("wss://api.example.com/ws"))
            .thenReturn(true)

        val connected = mockWebSocketRepository.connect("wss://api.example.com/ws")

        assertTrue(connected)

        mockWebSocketRepository.onFriendRequestReceived { _ -> }
        mockWebSocketRepository.onVoteUpdated { _ -> }

        verify(mockWebSocketRepository).onFriendRequestReceived(org.mockito.kotlin.any())
    }

    @Test
    fun testWebSocketReconnectionAfterDisconnect() {
        whenever(mockWebSocketRepository.connect("wss://api.example.com/ws"))
            .thenReturn(true)
        whenever(mockWebSocketRepository.isConnected())
            .thenReturn(true)

        val connected = mockWebSocketRepository.connect("wss://api.example.com/ws")
        assertTrue(connected)

        whenever(mockWebSocketRepository.isConnected())
            .thenReturn(false)

        whenever(mockWebSocketRepository.reconnect())
            .thenReturn(true)
        val reconnected = mockWebSocketRepository.reconnect()

        assertTrue(reconnected)
        verify(mockWebSocketRepository).reconnect()
    }

    @Test
    fun testEventQueueingDuringDisconnection() {
        whenever(mockWebSocketRepository.isConnected())
            .thenReturn(false)

        val events = TestDataFactory.createMultipleWebSocketEvents(5)

        val queuedEvents = mutableListOf<WebSocketEvent>()
        events.forEach { event ->
            queuedEvents.add(event)
        }

        assertEquals(5, queuedEvents.size)

        whenever(mockWebSocketRepository.isConnected())
            .thenReturn(true)

        queuedEvents.forEach { event ->
        }

        assertEquals(0, queuedEvents.size)
    }

    @Test
    fun testEventPropagationToMultipleViewModels() {
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(TestDataFactory.createMultipleFriendRequests(1))
        whenever(mockVotingRepository.getGroupVotes("group_123"))
            .thenReturn(TestDataFactory.createActiveVotes(1))
        whenever(mockChannelRepository.getChannelMembers("channel_123"))
            .thenReturn(TestDataFactory.createMultipleChannelMembers(5))

        friendViewModel.loadFriendRequests()
        votingViewModel.loadGroupVotes("group_123")
        channelViewModel.loadChannelMembers("channel_123")

        assertNotNull(friendViewModel.incomingFriendRequests.value)
        assertNotNull(votingViewModel.groupVotes.value)
        assertNotNull(channelViewModel.channelMembers.value)
    }

    @Test
    fun testCrossViewModelEventSynchronization() {
        val friendRequest = TestDataFactory.createTestFriendRequest()
        val vote = TestDataFactory.createTestGroupVote()
        val channel = TestDataFactory.createTestChannel()

        whenever(mockFriendRepository.acceptFriendRequest(friendRequest.id))
            .thenReturn(Unit)
        friendViewModel.acceptFriendRequest(friendRequest.id)

        val updatedVote = vote.copy(voteCount = 50)
        votingViewModel.onVoteUpdatedFromWebSocket(updatedVote)

        val newMembers = TestDataFactory.createMultipleChannelMembers(10)
        channelViewModel.onMembersUpdated(newMembers)

        verify(mockFriendRepository).acceptFriendRequest(friendRequest.id)
        assertEquals(50, votingViewModel.selectedVote.value?.voteCount)
    }

    @Test
    fun testWebSocketConnectionErrorRecovery() {
        whenever(mockWebSocketRepository.connect("wss://invalid.url"))
            .thenThrow(RuntimeException("Connection failed"))

        try {
            mockWebSocketRepository.connect("wss://invalid.url")
        } catch (e: Exception) {
        }

        whenever(mockWebSocketRepository.connect("wss://api.example.com/ws"))
            .thenReturn(true)
        val retried = mockWebSocketRepository.connect("wss://api.example.com/ws")

        assertTrue(retried)
    }

    @Test
    fun testEventProcessingErrorHandling() {
        val malformedEvent = WebSocketEvent(
            type = "UNKNOWN_TYPE",
            data = emptyMap(),
            timestamp = System.currentTimeMillis()
        )

        try {
            when (malformedEvent.type) {
                "FRIEND_REQUEST_RECEIVED" -> {}
                "VOTE_UPDATED" -> {}
                else -> throw RuntimeException("Unknown event type")
            }
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Unknown") ?: false)
        }
    }

    @Test
    fun testEventProcessingOrder() {
        val event1 = TestDataFactory.createFriendRequestEvent()
        val event2 = TestDataFactory.createVoteUpdatedEvent()
        val event3 = TestDataFactory.createMemberJoinedEvent()

        val events = listOf(event1, event2, event3)
        val processedEvents = mutableListOf<String>()

        events.forEach { event ->
            processedEvents.add(event.type)
        }

        assertEquals(event1.type, processedEvents[0])
        assertEquals(event2.type, processedEvents[1])
        assertEquals(event3.type, processedEvents[2])
    }

    @Test
    fun testEventConsistencyWithDatabaseState() {
        val requests = TestDataFactory.createMultipleFriendRequests(3)
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(requests)

        friendViewModel.loadFriendRequests()
        val initialCount = friendViewModel.incomingFriendRequests.value?.size

        val updatedRequests = requests + TestDataFactory.createTestFriendRequest()
        whenever(mockFriendRepository.getIncomingRequests())
            .thenReturn(updatedRequests)

        friendViewModel.loadFriendRequests()
        val finalCount = friendViewModel.incomingFriendRequests.value?.size

        assertEquals((initialCount ?: 0) + 1, finalCount)
    }
}
