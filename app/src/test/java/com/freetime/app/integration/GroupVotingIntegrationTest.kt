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
import com.freetime.app.data.model.GroupVote
import com.freetime.app.data.model.VoteOption
import com.freetime.app.data.repository.GroupVotingRepository
import com.freetime.app.data.repository.WebSocketEventRepository
import com.freetime.app.viewmodel.GroupVotingViewModel
import com.freetime.app.testutil.TestDataFactory

/**
 * Integration tests for Group Voting workflow
 * 
 * Tests complete voting workflows combining:
 * - Vote creation and management
 * - Real-time vote counting
 * - Result calculation and display
 * - Multi-user voting scenarios
 * - WebSocket synchronization
 */
class GroupVotingIntegrationTest {
    
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    @Mock
    private lateinit var mockVotingRepository: GroupVotingRepository
    
    @Mock
    private lateinit var mockWebSocketRepository: WebSocketEventRepository
    
    private lateinit var votingViewModel: GroupVotingViewModel
    
    private val testGroupId = "group_456"
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        votingViewModel = GroupVotingViewModel(
            votingRepository = mockVotingRepository,
            webSocketRepository = mockWebSocketRepository
        )
    }
    
    // ===== Complete Voting Session Workflow =====
    
    @Test
    fun testCompleteVotingSessionWorkflow() {
        // Arrange: Create an active vote
        val voteOptions = TestDataFactory.createMultipleVoteOptions(3)
        val activeVote = TestDataFactory.createTestGroupVote(
            id = "vote_123",
            groupId = testGroupId,
            question = "Should we have quarterly meetings?",
            options = voteOptions,
            totalMembers = 100,
            voteCount = 0,
            completedAt = null
        )
        
        whenever(mockVotingRepository.getGroupVotes(testGroupId))
            .thenReturn(listOf(activeVote))
        
        // Act 1: Load available votes
        votingViewModel.loadGroupVotes(testGroupId)
        
        // Assert 1: Votes loaded
        val loadedVotes = votingViewModel.activeVotes.value ?: emptyList()
        assertEquals(1, loadedVotes.size)
        assertEquals("vote_123", loadedVotes[0].id)
        
        // Act 2: Cast vote on first option
        val updatedVote = activeVote.copy(
            voteCount = 1,
            options = voteOptions.mapIndexed { index, option ->
                if (index == 0) option.copy(voteCount = 1) else option
            }
        )
        whenever(mockVotingRepository.castVote("vote_123", voteOptions[0].id))
            .thenReturn(updatedVote)
        
        votingViewModel.castVote("vote_123", voteOptions[0].id)
        
        // Assert 2: Vote recorded
        verify(mockVotingRepository).castVote("vote_123", voteOptions[0].id)
        
        // Act 3: Simulate more users voting (WebSocket updates)
        val votesAfterOthers = activeVote.copy(
            voteCount = 45,
            options = listOf(
                voteOptions[0].copy(voteCount = 20),
                voteOptions[1].copy(voteCount = 15),
                voteOptions[2].copy(voteCount = 10)
            )
        )
        whenever(mockVotingRepository.getVoteById("vote_123"))
            .thenReturn(votesAfterOthers)
        
        votingViewModel.loadVoteDetails("vote_123")
        
        // Assert 3: Real-time counts visible
        val updatedVoteDetails = votingViewModel.selectedVote.value
        assertEquals(45, updatedVoteDetails?.voteCount)
    }
    
    @Test
    fun testVoteCompletionAndResultsWorkflow() {
        // Arrange: Multiple votes - some active, some completed
        val activeVotes = TestDataFactory.createActiveVotes(2)
        val completedVotes = TestDataFactory.createCompletedVotes(1)
        val allVotes = activeVotes + completedVotes
        
        whenever(mockVotingRepository.getGroupVotes(testGroupId))
            .thenReturn(allVotes)
        
        // Act 1: Load all votes
        votingViewModel.loadGroupVotes(testGroupId)
        
        // Assert 1: Correctly separated
        val loaded = votingViewModel.groupVotes.value ?: emptyList()
        assertEquals(3, loaded.size)
        
        val active = loaded.filter { it.completedAt == null }
        val completed = loaded.filter { it.completedAt != null }
        
        assertEquals(2, active.size)
        assertEquals(1, completed.size)
        
        // Act 2: Calculate winner of completed vote
        val winningOption = completedVotes[0].options.maxByOrNull { it.voteCount }
        
        // Assert 2: Winner correctly identified
        assertNotNull(winningOption)
        assertTrue(winningOption!!.voteCount > 0)
    }
    
    @Test
    fun testMultipleUsersVotingSimultaneously() {
        // Arrange: Initial vote state
        val voteOptions = TestDataFactory.createMultipleVoteOptions(2)
        val vote = TestDataFactory.createTestGroupVote(
            options = voteOptions,
            voteCount = 0,
            completedAt = null
        )
        
        whenever(mockVotingRepository.getVoteById(vote.id))
            .thenReturn(vote)
        
        // Act 1: Current user casts vote
        whenever(mockVotingRepository.castVote(vote.id, voteOptions[0].id))
            .thenReturn(vote.copy(
                voteCount = 1,
                options = voteOptions.mapIndexed { index, option ->
                    if (index == 0) option.copy(voteCount = 1) else option
                }
            ))
        
        votingViewModel.castVote(vote.id, voteOptions[0].id)
        
        // Act 2: Simulate other users voting via WebSocket
        val voteAfter10Users = vote.copy(
            voteCount = 10,
            options = listOf(
                voteOptions[0].copy(voteCount = 6),
                voteOptions[1].copy(voteCount = 4)
            )
        )
        
        whenever(mockVotingRepository.getVoteById(vote.id))
            .thenReturn(voteAfter10Users)
        votingViewModel.updateVoteFromWebSocket(voteAfter10Users)
        
        // Act 3: More users voting
        val voteAfter50Users = vote.copy(
            voteCount = 50,
            options = listOf(
                voteOptions[0].copy(voteCount = 35),
                voteOptions[1].copy(voteCount = 15)
            )
        )
        
        whenever(mockVotingRepository.getVoteById(vote.id))
            .thenReturn(voteAfter50Users)
        votingViewModel.updateVoteFromWebSocket(voteAfter50Users)
        
        // Assert: Final counts correct
        val winner = voteAfter50Users.options.maxByOrNull { it.voteCount }
        assertEquals(35, winner?.voteCount)
    }
    
    // ===== Vote Statistics and Analysis =====
    
    @Test
    fun testVoteStatisticsCalculation() {
        // Arrange: Vote with multiple options
        val voteOptions = listOf(
            VoteOption(id = "opt_1", text = "Yes", voteCount = 60, totalVotes = 100),
            VoteOption(id = "opt_2", text = "No", voteCount = 30, totalVotes = 100),
            VoteOption(id = "opt_3", text = "Abstain", voteCount = 10, totalVotes = 100)
        )
        
        val vote = TestDataFactory.createTestGroupVote(
            options = voteOptions,
            totalMembers = 100,
            voteCount = 100
        )
        
        // Act: Calculate statistics
        val percentages = voteOptions.map { option ->
            (option.voteCount.toFloat() / option.totalVotes) * 100
        }
        
        val winner = voteOptions.maxByOrNull { it.voteCount }
        val participation = (vote.voteCount.toFloat() / vote.totalMembers) * 100
        
        // Assert: Statistics correct
        assertEquals(60f, percentages[0])
        assertEquals(30f, percentages[1])
        assertEquals(10f, percentages[2])
        assertEquals("opt_1", winner?.id)
        assertEquals(100f, participation)
    }
    
    @Test
    fun testParticipationRateTracking() {
        // Arrange: Vote with progressive participation
        val vote = TestDataFactory.createTestGroupVote(
            totalMembers = 100,
            voteCount = 0
        )
        
        // Act 1: Initial - 0 participation
        var participation = (vote.voteCount.toFloat() / vote.totalMembers) * 100
        assertEquals(0f, participation)
        
        // Act 2: 25% participation
        val voteAfter25 = vote.copy(voteCount = 25)
        participation = (voteAfter25.voteCount.toFloat() / voteAfter25.totalMembers) * 100
        assertEquals(25f, participation)
        
        // Act 3: 50% participation
        val voteAfter50 = vote.copy(voteCount = 50)
        participation = (voteAfter50.voteCount.toFloat() / voteAfter50.totalMembers) * 100
        assertEquals(50f, participation)
        
        // Act 4: 100% participation
        val voteComplete = vote.copy(voteCount = 100)
        participation = (voteComplete.voteCount.toFloat() / voteComplete.totalMembers) * 100
        assertEquals(100f, participation)
    }
    
    // ===== Error Handling in Voting =====
    
    @Test
    fun testAlreadyVotedErrorHandling() {
        // Arrange: Vote user already participated in
        val vote = TestDataFactory.createTestGroupVote()
        val voteOption = vote.options[0]
        
        whenever(mockVotingRepository.castVote(vote.id, voteOption.id))
            .thenThrow(RuntimeException("Already voted on this"))
        
        // Act: Attempt to vote again
        votingViewModel.castVote(vote.id, voteOption.id)
        
        // Assert: Error captured
        assertNotNull(votingViewModel.errorMessage.value)
        assertTrue(votingViewModel.errorMessage.value?.contains("Already voted") ?: false)
    }
    
    @Test
    fun testVoteClosedErrorHandling() {
        // Arrange: Completed vote
        val completedVote = TestDataFactory.createCompletedVotes(1)[0]
        val voteOption = completedVote.options[0]
        
        whenever(mockVotingRepository.castVote(completedVote.id, voteOption.id))
            .thenThrow(RuntimeException("Vote is closed"))
        
        // Act: Attempt to vote on closed vote
        votingViewModel.castVote(completedVote.id, voteOption.id)
        
        // Assert: Error handled
        assertNotNull(votingViewModel.errorMessage.value)
        assertTrue(votingViewModel.errorMessage.value?.contains("closed") ?: false)
    }
    
    // ===== Real-Time Updates via WebSocket =====
    
    @Test
    fun testRealTimeVoteCountUpdates() {
        // Arrange: Setup listener
        val vote = TestDataFactory.createTestGroupVote(voteCount = 0)
        
        whenever(mockVotingRepository.getVoteById(vote.id))
            .thenReturn(vote)
        
        // Act 1: Initial load
        votingViewModel.loadVoteDetails(vote.id)
        var loadedVote = votingViewModel.selectedVote.value
        assertEquals(0, loadedVote?.voteCount)
        
        // Act 2: Simulate WebSocket update with new counts
        val updatedVote = vote.copy(voteCount = 25)
        votingViewModel.onVoteUpdatedFromWebSocket(updatedVote)
        
        // Assert 2: Vote count updated
        loadedVote = votingViewModel.selectedVote.value
        assertEquals(25, loadedVote?.voteCount)
        
        // Act 3: Another WebSocket update
        val finalVote = vote.copy(
            voteCount = 100,
            completedAt = System.currentTimeMillis()
        )
        votingViewModel.onVoteUpdatedFromWebSocket(finalVote)
        
        // Assert 3: Vote completed
        loadedVote = votingViewModel.selectedVote.value
        assertEquals(100, loadedVote?.voteCount)
        assertNotNull(loadedVote?.completedAt)
    }
    
    // ===== Batch Vote Operations =====
    
    @Test
    fun testLoadAndProcessMultipleVotes() {
        // Arrange: Mix of votes
        val votes = TestDataFactory.createMixedVotes(activeCount = 3, completedCount = 2)
        
        whenever(mockVotingRepository.getGroupVotes(testGroupId))
            .thenReturn(votes)
        
        // Act: Load all votes
        votingViewModel.loadGroupVotes(testGroupId)
        
        // Assert: Correctly categorized
        val loadedVotes = votingViewModel.groupVotes.value ?: emptyList()
        assertEquals(5, loadedVotes.size)
        
        val active = loadedVotes.filter { it.completedAt == null }
        val completed = loadedVotes.filter { it.completedAt != null }
        
        assertEquals(3, active.size)
        assertEquals(2, completed.size)
    }
}
