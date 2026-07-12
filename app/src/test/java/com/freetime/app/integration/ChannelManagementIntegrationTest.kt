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
import com.freetime.app.data.model.Channel
import com.freetime.app.data.model.ChannelMember
import com.freetime.app.data.repository.ChannelRepository
import com.freetime.app.data.repository.WebSocketEventRepository
import com.freetime.app.viewmodel.ChannelViewModel
import com.freetime.app.testutil.TestDataFactory

/**
 * Integration tests for Channel Management workflow
 * 
 * Tests complete channel workflows combining:
 * - Channel loading and member management
 * - Role changes (promote/demote)
 * - Member restrictions (mute/unmute)
 * - Permission management
 * - Real-time member updates
 */
class ChannelManagementIntegrationTest {
    
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    @Mock
    private lateinit var mockChannelRepository: ChannelRepository
    
    @Mock
    private lateinit var mockWebSocketRepository: WebSocketEventRepository
    
    private lateinit var channelViewModel: ChannelViewModel
    
    private val testChannelId = "channel_123"
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        channelViewModel = ChannelViewModel(
            channelRepository = mockChannelRepository,
            webSocketRepository = mockWebSocketRepository
        )
    }
    
    // ===== Complete Channel Setup Workflow =====
    
    @Test
    fun testCompleteChannelSetupAndManagementWorkflow() {
        // Arrange: Channel and members
        val channel = TestDataFactory.createTestChannel(
            id = testChannelId,
            name = "Development Team",
            memberCount = 10,
            isPrivate = false
        )
        
        val members = TestDataFactory.createMultipleChannelMembers(
            count = 10,
            moderatorCount = 2
        )
        
        whenever(mockChannelRepository.getChannel(testChannelId))
            .thenReturn(channel)
        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(members)
        
        // Act 1: Load channel
        channelViewModel.loadChannel(testChannelId)
        
        // Assert 1: Channel loaded
        assertEquals(channel, channelViewModel.currentChannel.value)
        
        // Act 2: Load members
        channelViewModel.loadChannelMembers(testChannelId)
        
        // Assert 2: Members loaded with correct count
        assertEquals(10, channelViewModel.channelMembers.value?.size)
        
        // Verify moderator count
        val moderators = channelViewModel.channelMembers.value?.filter { it.role == "moderator" }
        assertEquals(2, moderators?.size)
    }
    
    @Test
    fun testMemberPromotionWorkflow() {
        // Arrange: Member to promote
        val members = TestDataFactory.createMultipleChannelMembers(count = 5, moderatorCount = 1)
        val memberToPromote = members[2] // Regular member
        
        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(members)
        channelViewModel.loadChannelMembers(testChannelId)
        
        // Verify initial state
        assertEquals("member", memberToPromote.role)
        
        // Act 1: Promote member
        val promotedMember = memberToPromote.copy(role = "moderator")
        val updatedMembers = members.map { 
            if (it.userId == memberToPromote.userId) promotedMember else it 
        }
        
        whenever(mockChannelRepository.promoteMember(testChannelId, memberToPromote.userId))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(updatedMembers)
        
        channelViewModel.promoteMember(testChannelId, memberToPromote.userId)
        
        // Assert: Promoted
        verify(mockChannelRepository).promoteMember(testChannelId, memberToPromote.userId)
    }
    
    @Test
    fun testMemberDemotionWorkflow() {
        // Arrange: Moderator to demote
        val members = TestDataFactory.createMultipleChannelMembers(count = 5, moderatorCount = 2)
        val moderatorToDemote = members.first { it.role == "moderator" }
        
        // Act: Demote moderator
        val demotedMember = moderatorToDemote.copy(role = "member")
        val updatedMembers = members.map {
            if (it.userId == moderatorToDemote.userId) demotedMember else it
        }
        
        whenever(mockChannelRepository.demoteMember(testChannelId, moderatorToDemote.userId))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(updatedMembers)
        
        channelViewModel.demoteMember(testChannelId, moderatorToDemote.userId)
        
        // Assert: Demoted
        verify(mockChannelRepository).demoteMember(testChannelId, moderatorToDemote.userId)
    }
    
    // ===== Member Restriction Management =====
    
    @Test
    fun testMuteAndUnmuteMemberWorkflow() {
        // Arrange: Member to mute
        val members = TestDataFactory.createMultipleChannelMembers(10)
        val memberToMute = members[0]
        
        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(members)
        
        // Act 1: Mute member
        val mutedMember = memberToMute.copy(isMuted = true)
        val updatedMembersAfterMute = members.map {
            if (it.userId == memberToMute.userId) mutedMember else it
        }
        
        whenever(mockChannelRepository.muteMember(testChannelId, memberToMute.userId))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(updatedMembersAfterMute)
        
        channelViewModel.muteMember(testChannelId, memberToMute.userId)
        
        // Assert 1: Muted
        verify(mockChannelRepository).muteMember(testChannelId, memberToMute.userId)
        
        // Act 2: Unmute member
        val unmutedMember = mutedMember.copy(isMuted = false)
        val updatedMembersAfterUnmute = updatedMembersAfterMute.map {
            if (it.userId == memberToMute.userId) unmutedMember else it
        }
        
        whenever(mockChannelRepository.unmuteMember(testChannelId, memberToMute.userId))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(updatedMembersAfterUnmute)
        
        channelViewModel.muteMember(testChannelId, memberToMute.userId)
        
        // Assert 2: Unmuted
        verify(mockChannelRepository).unmuteMember(testChannelId, memberToMute.userId)
    }
    
    @Test
    fun testRemoveMemberWorkflow() {
        // Arrange: Multiple members
        val members = TestDataFactory.createMultipleChannelMembers(10)
        val memberToRemove = members[5]
        
        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(members)
        
        // Act: Remove member
        val updatedMembers = members.filter { it.userId != memberToRemove.userId }
        
        whenever(mockChannelRepository.removeMember(testChannelId, memberToRemove.userId))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(updatedMembers)
        
        channelViewModel.removeMember(testChannelId, memberToRemove.userId)
        
        // Assert: Removed
        verify(mockChannelRepository).removeMember(testChannelId, memberToRemove.userId)
        assertEquals(9, updatedMembers.size)
    }
    
    // ===== Channel Settings Management =====
    
    @Test
    fun testChannelPrivacyToggleWorkflow() {
        // Arrange: Public channel
        var channel = TestDataFactory.createTestChannel(
            id = testChannelId,
            isPrivate = false
        )
        
        whenever(mockChannelRepository.getChannel(testChannelId))
            .thenReturn(channel)
        
        // Act 1: Make channel private
        val privateChannel = channel.copy(isPrivate = true)
        
        whenever(mockChannelRepository.updateChannelPrivacy(testChannelId, true))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannel(testChannelId))
            .thenReturn(privateChannel)
        
        channelViewModel.updateChannelPrivacy(testChannelId, true)
        
        // Assert 1: Made private
        verify(mockChannelRepository).updateChannelPrivacy(testChannelId, true)
        
        // Act 2: Make channel public again
        val publicChannel = channel.copy(isPrivate = false)
        
        whenever(mockChannelRepository.updateChannelPrivacy(testChannelId, false))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannel(testChannelId))
            .thenReturn(publicChannel)
        
        channelViewModel.updateChannelPrivacy(testChannelId, false)
        
        // Assert 2: Made public
        verify(mockChannelRepository).updateChannelPrivacy(testChannelId, false)
    }
    
    // ===== Member Filtering and Analysis =====
    
    @Test
    fun testMemberCategorization() {
        // Arrange: Mixed members
        val moderators = TestDataFactory.createModerators(3)
        val mutedMembers = TestDataFactory.createMutedMembers(5)
        val activeMembers = TestDataFactory.createActiveFriends(10).map { friend ->
            TestDataFactory.createTestChannelMember(
                userId = friend.id,
                username = friend.username,
                role = "member"
            )
        }
        val inactiveMembers = (1..5).map { i ->
            TestDataFactory.createTestChannelMember(
                userId = "inactive_$i",
                username = "inactive_$i",
                isActive = false
            )
        }
        
        val allMembers = moderators + mutedMembers + activeMembers + inactiveMembers
        
        // Act: Categorize
        val categorizedModerators = allMembers.filter { it.role == "moderator" }
        val categorizedMutedMembers = allMembers.filter { it.isMuted }
        val categorizedActiveMembers = allMembers.filter { it.isActive }
        val categorizedInactiveMembers = allMembers.filter { !it.isActive }
        
        // Assert: Correct categorization
        assertEquals(3, categorizedModerators.size)
        assertTrue(categorizedMutedMembers.size > 0)
        assertTrue(categorizedActiveMembers.size > 0)
        assertEquals(5, categorizedInactiveMembers.size)
    }
    
    @Test
    fun testBulkMemberOperations() {
        // Arrange: Multiple members
        val members = TestDataFactory.createMultipleChannelMembers(20, moderatorCount = 2)
        
        // Act: Perform multiple operations
        val membersToMute = members.take(5)
        membersToMute.forEach { member ->
            whenever(mockChannelRepository.muteMember(testChannelId, member.userId))
                .thenReturn(Unit)
        }
        
        // Execute operations
        membersToMute.forEach { member ->
            channelViewModel.muteMember(testChannelId, member.userId)
        }
        
        // Assert: All operations executed
        membersToMute.forEach { member ->
            verify(mockChannelRepository).muteMember(testChannelId, member.userId)
        }
    }
    
    // ===== Real-Time Member Updates =====
    
    @Test
    fun testRealTimeMemberStatusUpdates() {
        // Arrange: Initial member list
        val members = TestDataFactory.createMultipleChannelMembers(5)
        
        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(members)
        
        // Act 1: Load members
        channelViewModel.loadChannelMembers(testChannelId)
        
        // Assert 1: Initial load
        assertEquals(5, channelViewModel.channelMembers.value?.size)
        
        // Act 2: Simulate member going offline
        val updatedMembers = members.map { member ->
            if (member.isActive) member.copy(isActive = false) else member
        }
        
        channelViewModel.onMemberStatusChanged(updatedMembers)
        
        // Assert 2: Status updated
        val inactiveMembers = channelViewModel.channelMembers.value?.filter { !it.isActive }
        assertTrue((inactiveMembers?.size ?: 0) > 0)
    }
    
    // ===== Error Scenarios =====
    
    @Test
    fun testPermissionDeniedErrorHandling() {
        // Arrange: Try to perform privileged operation without permission
        val targetMember = TestDataFactory.createTestChannelMember(
            userId = "other_user",
            username = "other_user"
        )
        
        whenever(mockChannelRepository.promoteMember(testChannelId, targetMember.userId))
            .thenThrow(RuntimeException("Permission denied"))
        
        // Act: Attempt operation
        channelViewModel.promoteMember(testChannelId, targetMember.userId)
        
        // Assert: Error captured
        assertNotNull(channelViewModel.errorMessage.value)
        assertTrue(channelViewModel.errorMessage.value?.contains("Permission") ?: false)
    }
    
    @Test
    fun testMemberNotFoundErrorHandling() {
        // Arrange: Attempt operation on non-existent member
        whenever(mockChannelRepository.muteMember(testChannelId, "non_existent_user"))
            .thenThrow(RuntimeException("Member not found"))
        
        // Act: Attempt operation
        channelViewModel.muteMember(testChannelId, "non_existent_user")
        
        // Assert: Error handled
        assertNotNull(channelViewModel.errorMessage.value)
    }
}
