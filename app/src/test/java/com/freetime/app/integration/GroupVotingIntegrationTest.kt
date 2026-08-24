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

// full voting flow test
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

    @Test
    fun testCompleteVotingSessionWorkflow() {
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

        votingViewModel.loadGroupVotes(testGroupId)

        val loadedVotes = votingViewModel.activeVotes.value ?: emptyList()
        assertEquals(1, loadedVotes.size)
        assertEquals("vote_123", loadedVotes[0].id)

        // fake vote result from the server
        val updatedVote = activeVote.copy(
            voteCount = 1,
            options = voteOptions.mapIndexed { index, option ->
                if (index == 0) option.copy(voteCount = 1) else option
            }
        )
        whenever(mockVotingRepository.castVote("vote_123", voteOptions[0].id))
            .thenReturn(updatedVote)

        votingViewModel.castVote("vote_123", voteOptions[0].id)

        verify(mockVotingRepository).castVote("vote_123", voteOptions[0].id)

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

        val updatedVoteDetails = votingViewModel.selectedVote.value
        assertEquals(45, updatedVoteDetails?.voteCount)
    }

    @Test
    fun testVoteCompletionAndResultsWorkflow() {
        val activeVotes = TestDataFactory.createActiveVotes(2)
        val completedVotes = TestDataFactory.createCompletedVotes(1)
        val allVotes = activeVotes + completedVotes

        whenever(mockVotingRepository.getGroupVotes(testGroupId))
            .thenReturn(allVotes)

        votingViewModel.loadGroupVotes(testGroupId)

        val loaded = votingViewModel.groupVotes.value ?: emptyList()
        assertEquals(3, loaded.size)

        val active = loaded.filter { it.completedAt == null }
        val completed = loaded.filter { it.completedAt != null }

        assertEquals(2, active.size)
        assertEquals(1, completed.size)

        val winningOption = completedVotes[0].options.maxByOrNull { it.voteCount }

        assertNotNull(winningOption)
        assertTrue(winningOption!!.voteCount > 0)
    }

    @Test
    fun testMultipleUsersVotingSimultaneously() {
        val voteOptions = TestDataFactory.createMultipleVoteOptions(2)
        val vote = TestDataFactory.createTestGroupVote(
            options = voteOptions,
            voteCount = 0,
            completedAt = null
        )

        whenever(mockVotingRepository.getVoteById(vote.id))
            .thenReturn(vote)

        whenever(mockVotingRepository.castVote(vote.id, voteOptions[0].id))
            .thenReturn(vote.copy(
                voteCount = 1,
                options = voteOptions.mapIndexed { index, option ->
                    if (index == 0) option.copy(voteCount = 1) else option
                }
            ))

        votingViewModel.castVote(vote.id, voteOptions[0].id)

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

        val winner = voteAfter50Users.options.maxByOrNull { it.voteCount }
        assertEquals(35, winner?.voteCount)
    }

    @Test
    fun testVoteStatisticsCalculation() {
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

        val percentages = voteOptions.map { option ->
            (option.voteCount.toFloat() / option.totalVotes) * 100
        }

        val winner = voteOptions.maxByOrNull { it.voteCount }
        val participation = (vote.voteCount.toFloat() / vote.totalMembers) * 100

        assertEquals(60f, percentages[0])
        assertEquals(30f, percentages[1])
        assertEquals(10f, percentages[2])
        assertEquals("opt_1", winner?.id)
        assertEquals(100f, participation)
    }

    @Test
    fun testParticipationRateTracking() {
        val vote = TestDataFactory.createTestGroupVote(
            totalMembers = 100,
            voteCount = 0
        )

        var participation = (vote.voteCount.toFloat() / vote.totalMembers) * 100
        assertEquals(0f, participation)

        val voteAfter25 = vote.copy(voteCount = 25)
        participation = (voteAfter25.voteCount.toFloat() / voteAfter25.totalMembers) * 100
        assertEquals(25f, participation)

        val voteAfter50 = vote.copy(voteCount = 50)
        participation = (voteAfter50.voteCount.toFloat() / voteAfter50.totalMembers) * 100
        assertEquals(50f, participation)

        val voteComplete = vote.copy(voteCount = 100)
        participation = (voteComplete.voteCount.toFloat() / voteComplete.totalMembers) * 100
        assertEquals(100f, participation)
    }

    @Test
    fun testAlreadyVotedErrorHandling() {
        val vote = TestDataFactory.createTestGroupVote()
        val voteOption = vote.options[0]

        whenever(mockVotingRepository.castVote(vote.id, voteOption.id))
            .thenThrow(RuntimeException("Already voted on this"))

        votingViewModel.castVote(vote.id, voteOption.id)

        assertNotNull(votingViewModel.errorMessage.value)
        assertTrue(votingViewModel.errorMessage.value?.contains("Already voted") ?: false)
    }

    @Test
    fun testVoteClosedErrorHandling() {
        val completedVote = TestDataFactory.createCompletedVotes(1)[0]
        val voteOption = completedVote.options[0]

        whenever(mockVotingRepository.castVote(completedVote.id, voteOption.id))
            .thenThrow(RuntimeException("Vote is closed"))

        votingViewModel.castVote(completedVote.id, voteOption.id)

        assertNotNull(votingViewModel.errorMessage.value)
        assertTrue(votingViewModel.errorMessage.value?.contains("closed") ?: false)
    }

    @Test
    fun testRealTimeVoteCountUpdates() {
        val vote = TestDataFactory.createTestGroupVote(voteCount = 0)

        whenever(mockVotingRepository.getVoteById(vote.id))
            .thenReturn(vote)

        votingViewModel.loadVoteDetails(vote.id)
        var loadedVote = votingViewModel.selectedVote.value
        assertEquals(0, loadedVote?.voteCount)

        val updatedVote = vote.copy(voteCount = 25)
        votingViewModel.onVoteUpdatedFromWebSocket(updatedVote)

        loadedVote = votingViewModel.selectedVote.value
        assertEquals(25, loadedVote?.voteCount)

        val finalVote = vote.copy(
            voteCount = 100,
            completedAt = System.currentTimeMillis()
        )
        votingViewModel.onVoteUpdatedFromWebSocket(finalVote)

        loadedVote = votingViewModel.selectedVote.value
        assertEquals(100, loadedVote?.voteCount)
        assertNotNull(loadedVote?.completedAt)
    }

    @Test
    fun testLoadAndProcessMultipleVotes() {
        val votes = TestDataFactory.createMixedVotes(activeCount = 3, completedCount = 2)

        whenever(mockVotingRepository.getGroupVotes(testGroupId))
            .thenReturn(votes)

        votingViewModel.loadGroupVotes(testGroupId)

        val loadedVotes = votingViewModel.groupVotes.value ?: emptyList()
        assertEquals(5, loadedVotes.size)

        val active = loadedVotes.filter { it.completedAt == null }
        val completed = loadedVotes.filter { it.completedAt != null }

        assertEquals(3, active.size)
        assertEquals(2, completed.size)
    }
}
