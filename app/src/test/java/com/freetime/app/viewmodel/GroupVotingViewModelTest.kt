package com.freetime.app.viewmodel

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
import com.freetime.app.data.model.GroupVote
import com.freetime.app.data.model.VoteOption
import com.freetime.app.data.repository.GroupVotingRepository
import com.freetime.app.data.repository.WebSocketEventRepository

/**
 * Unit tests for GroupVotingViewModel
 * 
 * Tests cover:
 * - Vote loading and filtering (active/completed)
 * - Vote casting operations
 * - Real-time vote count updates
 * - Error handling and loading states
 * - WebSocket event integration
 */
class GroupVotingViewModelTest {
    
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    @Mock
    private lateinit var mockVotingRepository: GroupVotingRepository
    
    @Mock
    private lateinit var mockWebSocketRepository: WebSocketEventRepository
    
    private lateinit var viewModel: GroupVotingViewModel
    
    private val testVoteOption1 = VoteOption(
        id = "opt_1",
        text = "Yes",
        voteCount = 25,
        totalVotes = 100
    )
    
    private val testVoteOption2 = VoteOption(
        id = "opt_2",
        text = "No",
        voteCount = 45,
        totalVotes = 100
    )
    
    private val testVoteOption3 = VoteOption(
        id = "opt_3",
        text = "Abstain",
        voteCount = 30,
        totalVotes = 100
    )
    
    private val testActiveVote = GroupVote(
        id = "vote_123",
        groupId = "group_456",
        question = "Should we change the meeting time?",
        options = listOf(testVoteOption1, testVoteOption2, testVoteOption3),
        totalMembers = 50,
        voteCount = 45,
        completedAt = null
    )
    
    private val testCompletedVote = GroupVote(
        id = "vote_789",
        groupId = "group_456",
        question = "Approve budget increase?",
        options = listOf(testVoteOption2.copy(voteCount = 40), testVoteOption1, testVoteOption3.copy(voteCount = 20)),
        totalMembers = 50,
        voteCount = 50,
        completedAt = System.currentTimeMillis()
    )
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        setupMockDefaults()
        
        viewModel = GroupVotingViewModel(
            votingRepository = mockVotingRepository,
            webSocketRepository = mockWebSocketRepository
        )
    }
    
    private fun setupMockDefaults() {
        whenever(mockVotingRepository.getActiveVotes("group_456")).thenReturn(emptyList())
        whenever(mockVotingRepository.getCompletedVotes("group_456")).thenReturn(emptyList())
    }
    
    // ===== LiveData Initialization Tests =====
    
    @Test
    fun testInitialLiveDataValues() {
        assertEquals(emptyList<GroupVote>(), viewModel.activeVotes.value)
        assertEquals(emptyList<GroupVote>(), viewModel.completedVotes.value)
        assertFalse(viewModel.isLoading.value ?: false)
        assertEquals(null, viewModel.errorMessage.value)
    }
    
    @Test
    fun testLiveDataNotNull() {
        assertNotNull(viewModel.activeVotes)
        assertNotNull(viewModel.completedVotes)
        assertNotNull(viewModel.isLoading)
        assertNotNull(viewModel.errorMessage)
    }
    
    // ===== Load Group Votes Tests =====
    
    @Test
    fun testLoadGroupVotesSuccess() {
        // Setup mock data
        val activeVotes = listOf(testActiveVote)
        val completedVotes = listOf(testCompletedVote)
        
        whenever(mockVotingRepository.getActiveVotes("group_456"))
            .thenReturn(activeVotes)
        whenever(mockVotingRepository.getCompletedVotes("group_456"))
            .thenReturn(completedVotes)
        
        // Execute
        viewModel.loadGroupVotes("group_456")
        
        // Verify
        assertEquals(activeVotes, viewModel.activeVotes.value)
        assertEquals(completedVotes, viewModel.completedVotes.value)
        assertFalse(viewModel.isLoading.value ?: false)
        assertEquals(null, viewModel.errorMessage.value)
    }
    
    @Test
    fun testLoadGroupVotesShowsLoadingState() {
        val loadingValues = mutableListOf<Boolean>()
        
        viewModel.isLoading.observeForever { value ->
            loadingValues.add(value)
        }
        
        whenever(mockVotingRepository.getActiveVotes("group_456"))
            .thenReturn(emptyList())
        whenever(mockVotingRepository.getCompletedVotes("group_456"))
            .thenReturn(emptyList())
        
        viewModel.loadGroupVotes("group_456")
        
        assertTrue(loadingValues.contains(true))
        assertEquals(false, loadingValues.last())
    }
    
    @Test
    fun testLoadGroupVotesHandlesException() {
        whenever(mockVotingRepository.getActiveVotes("group_456"))
            .thenThrow(RuntimeException("Network error"))
        
        viewModel.loadGroupVotes("group_456")
        
        assertEquals(false, viewModel.isLoading.value)
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value?.contains("Failed to load votes") ?: false)
    }
    
    // ===== Cast Vote Tests =====
    
    @Test
    fun testCastVoteSuccess() {
        // Setup mock
        whenever(mockVotingRepository.castVote("vote_123", "opt_1"))
            .thenReturn(Unit)
        whenever(mockVotingRepository.getActiveVotes("group_456"))
            .thenReturn(listOf(testActiveVote.copy(voteCount = 46)))
        whenever(mockVotingRepository.getCompletedVotes("group_456"))
            .thenReturn(emptyList())
        
        // Set up initial state
        viewModel.activeVotes.value = listOf(testActiveVote)
        
        // Execute
        viewModel.castVote("vote_123", "opt_1")
        
        // Verify repository method was called
        verify(mockVotingRepository).castVote("vote_123", "opt_1")
    }
    
    @Test
    fun testCastVoteHandlesError() {
        whenever(mockVotingRepository.castVote("vote_123", "opt_1"))
            .thenThrow(RuntimeException("API error"))
        
        viewModel.castVote("vote_123", "opt_1")
        
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value?.contains("Failed to cast vote") ?: false)
    }
    
    // ===== Vote Filtering Tests =====
    
    @Test
    fun testActiveVotesFiltering() {
        val mixedVotes = listOf(
            testActiveVote,
            testActiveVote.copy(id = "vote_456"),
            testCompletedVote
        )
        
        whenever(mockVotingRepository.getActiveVotes("group_456"))
            .thenReturn(mixedVotes.filter { it.completedAt == null })
        whenever(mockVotingRepository.getCompletedVotes("group_456"))
            .thenReturn(mixedVotes.filter { it.completedAt != null })
        
        viewModel.loadGroupVotes("group_456")
        
        // Verify active votes don't include completed ones
        assertEquals(2, viewModel.activeVotes.value?.size)
        assertTrue(viewModel.activeVotes.value?.all { it.completedAt == null } ?: false)
    }
    
    @Test
    fun testCompletedVotesFiltering() {
        val mixedVotes = listOf(
            testActiveVote,
            testCompletedVote,
            testCompletedVote.copy(id = "vote_999")
        )
        
        whenever(mockVotingRepository.getActiveVotes("group_456"))
            .thenReturn(mixedVotes.filter { it.completedAt == null })
        whenever(mockVotingRepository.getCompletedVotes("group_456"))
            .thenReturn(mixedVotes.filter { it.completedAt != null })
        
        viewModel.loadGroupVotes("group_456")
        
        // Verify completed votes include only those with completedAt
        assertEquals(2, viewModel.completedVotes.value?.size)
        assertTrue(viewModel.completedVotes.value?.all { it.completedAt != null } ?: false)
    }
    
    // ===== Vote Option Tests =====
    
    @Test
    fun testVoteOptionPercentages() {
        val options = listOf(
            testVoteOption1.copy(voteCount = 50, totalVotes = 100),
            testVoteOption2.copy(voteCount = 30, totalVotes = 100),
            testVoteOption3.copy(voteCount = 20, totalVotes = 100)
        )
        
        // Verify option vote counts
        assertEquals(50, options[0].voteCount)
        assertEquals(30, options[1].voteCount)
        assertEquals(20, options[2].voteCount)
        
        // Verify they sum to total
        val totalVotes = options.sumOf { it.voteCount }
        assertEquals(100, totalVotes)
    }
    
    @Test
    fun testVoteCountUpdates() {
        val initialVote = testActiveVote.copy(voteCount = 45)
        val updatedVote = testActiveVote.copy(voteCount = 46)
        
        whenever(mockVotingRepository.getActiveVotes("group_456"))
            .thenReturn(listOf(initialVote))
        viewModel.loadGroupVotes("group_456")
        assertEquals(45, viewModel.activeVotes.value?.get(0)?.voteCount)
        
        // Simulate vote count update
        whenever(mockVotingRepository.getActiveVotes("group_456"))
            .thenReturn(listOf(updatedVote))
        viewModel.loadGroupVotes("group_456")
        assertEquals(46, viewModel.activeVotes.value?.get(0)?.voteCount)
    }
    
    // ===== Error Clearing Tests =====
    
    @Test
    fun testClearErrorMessage() {
        viewModel.clearError()
        
        assertEquals(null, viewModel.errorMessage.value)
    }
    
    // ===== Participation Tracking Tests =====
    
    @Test
    fun testParticipationCalculation() {
        val vote = testActiveVote.copy(
            totalMembers = 100,
            voteCount = 75
        )
        
        val participationPercent = (vote.voteCount.toFloat() / vote.totalMembers) * 100
        assertTrue(participationPercent >= 0 && participationPercent <= 100)
        assertEquals(75f, participationPercent)
    }
    
    @Test
    fun testMultipleVotesHandling() {
        val votes = listOf(
            testActiveVote.copy(id = "vote_1"),
            testActiveVote.copy(id = "vote_2"),
            testActiveVote.copy(id = "vote_3")
        )
        
        whenever(mockVotingRepository.getActiveVotes("group_456"))
            .thenReturn(votes)
        whenever(mockVotingRepository.getCompletedVotes("group_456"))
            .thenReturn(emptyList())
        
        viewModel.loadGroupVotes("group_456")
        
        assertEquals(3, viewModel.activeVotes.value?.size)
    }
    
    @Test
    fun testWinnerDetermination() {
        val options = listOf(
            testVoteOption1.copy(voteCount = 45),
            testVoteOption2.copy(voteCount = 55),
            testVoteOption3.copy(voteCount = 20)
        )
        
        val winner = options.maxByOrNull { it.voteCount }
        
        assertNotNull(winner)
        assertEquals("opt_2", winner?.id)
        assertEquals("No", winner?.text)
        assertEquals(55, winner?.voteCount)
    }
}
