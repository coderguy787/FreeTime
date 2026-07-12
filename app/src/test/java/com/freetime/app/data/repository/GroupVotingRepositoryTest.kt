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
import com.freetime.app.data.model.GroupVote
import com.freetime.app.data.model.VoteOption
import retrofit2.Response

/**
 * Unit tests for GroupVotingRepository
 * 
 * Tests cover:
 * - Vote retrieval operations
 * - Vote creation and submission
 * - Vote statistics calculation
 * - Error handling
 */
class GroupVotingRepositoryTest {
    
    @Mock
    private lateinit var mockAPI: SecureChatAPI
    
    private lateinit var repository: GroupVotingRepository
    
    private val testVoteOption1 = VoteOption(
        id = "opt_1",
        text = "Yes",
        voteCount = 25,
        totalVotes = 100
    )
    
    private val testVoteOption2 = VoteOption(
        id = "opt_2",
        text = "No",
        voteCount = 75,
        totalVotes = 100
    )
    
    private val testActiveVote = GroupVote(
        id = "vote_123",
        groupId = "group_456",
        question = "Should we change the meeting time?",
        options = listOf(testVoteOption1, testVoteOption2),
        totalMembers = 50,
        voteCount = 100,
        completedAt = null
    )
    
    private val testCompletedVote = GroupVote(
        id = "vote_789",
        groupId = "group_456",
        question = "Approve budget increase?",
        options = listOf(testVoteOption1, testVoteOption2),
        totalMembers = 50,
        voteCount = 50,
        completedAt = System.currentTimeMillis()
    )
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = GroupVotingRepository(mockAPI)
    }
    
    // ===== Get Group Votes Tests =====
    
    @Test
    fun testGetGroupVotesSuccess() {
        val votes = listOf(testActiveVote, testCompletedVote)
        
        whenever(mockAPI.getGroupVotes("group_456"))
            .thenReturn(Response.success(votes))
        
        val result = repository.getGroupVotes("group_456")
        
        assertEquals(2, result.size)
        assertEquals("vote_123", result[0].id)
        assertEquals("vote_789", result[1].id)
    }
    
    @Test
    fun testGetGroupVotesReturnsEmptyList() {
        whenever(mockAPI.getGroupVotes("group_456"))
            .thenReturn(Response.success(emptyList()))
        
        val result = repository.getGroupVotes("group_456")
        
        assertEquals(0, result.size)
    }
    
    @Test
    fun testGetGroupVotesHandlesFailure() {
        whenever(mockAPI.getGroupVotes("group_456"))
            .thenThrow(RuntimeException("Network error"))
        
        try {
            repository.getGroupVotes("group_456")
            assertTrue(false)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Network error") ?: false)
        }
    }
    
    // ===== Get Active Votes Tests =====
    
    @Test
    fun testGetActiveVotesSuccess() {
        val votes = listOf(testActiveVote, testCompletedVote)
        
        whenever(mockAPI.getGroupVotes("group_456"))
            .thenReturn(Response.success(votes))
        
        val allVotes = repository.getGroupVotes("group_456")
        val activeVotes = allVotes.filter { it.completedAt == null }
        
        assertEquals(1, activeVotes.size)
        assertEquals("vote_123", activeVotes[0].id)
    }
    
    @Test
    fun testGetCompletedVotesSuccess() {
        val votes = listOf(testActiveVote, testCompletedVote)
        
        whenever(mockAPI.getGroupVotes("group_456"))
            .thenReturn(Response.success(votes))
        
        val allVotes = repository.getGroupVotes("group_456")
        val completedVotes = allVotes.filter { it.completedAt != null }
        
        assertEquals(1, completedVotes.size)
        assertEquals("vote_789", completedVotes[0].id)
    }
    
    // ===== Get Specific Vote Tests =====
    
    @Test
    fun testGetVoteByIdSuccess() {
        whenever(mockAPI.getVoteById("vote_123"))
            .thenReturn(Response.success(testActiveVote))
        
        val result = repository.getVoteById("vote_123")
        
        assertNotNull(result)
        assertEquals("vote_123", result.id)
        assertEquals("group_456", result.groupId)
    }
    
    @Test
    fun testGetVoteByIdHandlesNotFound() {
        whenever(mockAPI.getVoteById("vote_999"))
            .thenThrow(RuntimeException("Vote not found"))
        
        try {
            repository.getVoteById("vote_999")
            assertTrue(false)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Vote not found") ?: false)
        }
    }
    
    // ===== Create Vote Tests =====
    
    @Test
    fun testCreateVoteSuccess() {
        val newVote = testActiveVote.copy(id = "vote_new")
        
        whenever(mockAPI.createVote(
            "group_456",
            "Should we change the meeting time?",
            listOf("Yes", "No")
        )).thenReturn(Response.success(newVote))
        
        val result = repository.createVote(
            "group_456",
            "Should we change the meeting time?",
            listOf("Yes", "No")
        )
        
        assertNotNull(result)
        assertEquals("vote_new", result.id)
    }
    
    @Test
    fun testCreateVoteHandlesError() {
        whenever(mockAPI.createVote("group_456", "Test?", listOf("Yes", "No")))
            .thenThrow(RuntimeException("Permission denied"))
        
        try {
            repository.createVote("group_456", "Test?", listOf("Yes", "No"))
            assertTrue(false)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Permission denied") ?: false)
        }
    }
    
    // ===== Cast Vote Tests =====
    
    @Test
    fun testCastVoteSuccess() {
        val updatedVote = testActiveVote.copy(
            voteCount = 101,
            options = listOf(
                testVoteOption1.copy(voteCount = 26),
                testVoteOption2
            )
        )
        
        whenever(mockAPI.castVote("vote_123", "opt_1"))
            .thenReturn(Response.success(updatedVote))
        
        val result = repository.castVote("vote_123", "opt_1")
        
        assertNotNull(result)
        assertEquals(101, result.voteCount)
    }
    
    @Test
    fun testCastVoteHandlesAlreadyVoted() {
        whenever(mockAPI.castVote("vote_123", "opt_1"))
            .thenThrow(RuntimeException("Already voted on this"))
        
        try {
            repository.castVote("vote_123", "opt_1")
            assertTrue(false)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Already voted on this") ?: false)
        }
    }
    
    @Test
    fun testCastVoteHandlesVoteClosed() {
        whenever(mockAPI.castVote("vote_789", "opt_1"))
            .thenThrow(RuntimeException("Vote is closed"))
        
        try {
            repository.castVote("vote_789", "opt_1")
            assertTrue(false)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Vote is closed") ?: false)
        }
    }
    
    // ===== Close Vote Tests =====
    
    @Test
    fun testCloseVoteSuccess() {
        val closedVote = testActiveVote.copy(completedAt = System.currentTimeMillis())
        
        whenever(mockAPI.closeVote("vote_123"))
            .thenReturn(Response.success(closedVote))
        
        val result = repository.closeVote("vote_123")
        
        assertNotNull(result.completedAt)
    }
    
    @Test
    fun testCloseVoteHandlesError() {
        whenever(mockAPI.closeVote("vote_123"))
            .thenThrow(RuntimeException("Permission denied"))
        
        try {
            repository.closeVote("vote_123")
            assertTrue(false)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Permission denied") ?: false)
        }
    }
    
    // ===== Delete Vote Tests =====
    
    @Test
    fun testDeleteVoteSuccess() {
        whenever(mockAPI.deleteVote("vote_123"))
            .thenReturn(Response.success(Unit))
        
        repository.deleteVote("vote_123")
        
        verify(mockAPI).deleteVote("vote_123")
    }
    
    @Test
    fun testDeleteVoteHandlesError() {
        whenever(mockAPI.deleteVote("vote_123"))
            .thenThrow(RuntimeException("Cannot delete completed vote"))
        
        try {
            repository.deleteVote("vote_123")
            assertTrue(false)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Cannot delete completed vote") ?: false)
        }
    }
    
    // ===== Vote Statistics Tests =====
    
    @Test
    fun testVoteOptionPercentages() {
        val option1 = testVoteOption1.copy(voteCount = 25, totalVotes = 100)
        val option2 = testVoteOption2.copy(voteCount = 75, totalVotes = 100)
        
        val percentage1 = (option1.voteCount.toFloat() / option1.totalVotes) * 100
        val percentage2 = (option2.voteCount.toFloat() / option2.totalVotes) * 100
        
        assertEquals(25f, percentage1)
        assertEquals(75f, percentage2)
    }
    
    @Test
    fun testVoteWinnerDetermination() {
        val vote = testActiveVote
        
        val winner = vote.options.maxByOrNull { it.voteCount }
        
        assertNotNull(winner)
        assertEquals("opt_2", winner?.id)
        assertEquals("No", winner?.text)
    }
    
    @Test
    fun testParticipationPercentage() {
        val vote = testActiveVote
        val participationPercentage = (vote.voteCount.toFloat() / vote.totalMembers) * 100
        
        assertEquals(200f, participationPercentage) // 100 votes / 50 members
    }
    
    // ===== Batch Operations Tests =====
    
    @Test
    fun testMultipleVotesHandling() {
        val votes = (1..10).map { i ->
            testActiveVote.copy(
                id = "vote_$i",
                question = "Question $i?",
                completedAt = if (i % 2 == 0) System.currentTimeMillis() else null
            )
        }
        
        whenever(mockAPI.getGroupVotes("group_456"))
            .thenReturn(Response.success(votes))
        
        val result = repository.getGroupVotes("group_456")
        
        assertEquals(10, result.size)
        
        val activeCount = result.count { it.completedAt == null }
        val completedCount = result.count { it.completedAt != null }
        
        assertEquals(5, activeCount)
        assertEquals(5, completedCount)
    }
    
    @Test
    fun testVoteOptionCount() {
        val vote = testActiveVote
        
        assertEquals(2, vote.options.size)
        assertTrue(vote.options.any { it.text == "Yes" })
        assertTrue(vote.options.any { it.text == "No" })
    }
    
    @Test
    fun testComplexVoteScenario() {
        val complexVote = testActiveVote.copy(
            question = "Complex voting scenario?",
            options = listOf(
                testVoteOption1.copy(voteCount = 30),
                testVoteOption2.copy(voteCount = 40),
                VoteOption(id = "opt_3", text = "Maybe", voteCount = 30, totalVotes = 100)
            )
        )
        
        whenever(mockAPI.getVoteById("vote_123"))
            .thenReturn(Response.success(complexVote))
        
        val result = repository.getVoteById("vote_123")
        
        assertEquals(3, result.options.size)
        
        val totalVotes = result.options.sumOf { it.voteCount }
        assertEquals(100, totalVotes)
        
        val winner = result.options.maxByOrNull { it.voteCount }
        assertEquals("opt_2", winner?.id)
    }
}
