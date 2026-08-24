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

// channel management flow tests
class ChannelManagementIntegrationTest {
    // livedata runs synchronously here
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

    @Test
    fun testCompleteChannelSetupAndManagementWorkflow() {
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

        channelViewModel.loadChannel(testChannelId)

        assertEquals(channel, channelViewModel.currentChannel.value)

        channelViewModel.loadChannelMembers(testChannelId)

        assertEquals(10, channelViewModel.channelMembers.value?.size)

        val moderators = channelViewModel.channelMembers.value?.filter { it.role == "moderator" }
        assertEquals(2, moderators?.size)
    }

    @Test
    fun testMemberPromotionWorkflow() {
        val members = TestDataFactory.createMultipleChannelMembers(count = 5, moderatorCount = 1)
        val memberToPromote = members[2]

        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(members)
        channelViewModel.loadChannelMembers(testChannelId)

        assertEquals("member", memberToPromote.role)

        val promotedMember = memberToPromote.copy(role = "moderator")
        val updatedMembers = members.map {
            if (it.userId == memberToPromote.userId) promotedMember else it
        }

        whenever(mockChannelRepository.promoteMember(testChannelId, memberToPromote.userId))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(updatedMembers)

        channelViewModel.promoteMember(testChannelId, memberToPromote.userId)

        verify(mockChannelRepository).promoteMember(testChannelId, memberToPromote.userId)
    }

    @Test
    fun testMemberDemotionWorkflow() {
        val members = TestDataFactory.createMultipleChannelMembers(count = 5, moderatorCount = 2)
        val moderatorToDemote = members.first { it.role == "moderator" }

        val demotedMember = moderatorToDemote.copy(role = "member")
        val updatedMembers = members.map {
            if (it.userId == moderatorToDemote.userId) demotedMember else it
        }

        whenever(mockChannelRepository.demoteMember(testChannelId, moderatorToDemote.userId))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(updatedMembers)

        channelViewModel.demoteMember(testChannelId, moderatorToDemote.userId)

        verify(mockChannelRepository).demoteMember(testChannelId, moderatorToDemote.userId)
    }

    @Test
    fun testMuteAndUnmuteMemberWorkflow() {
        val members = TestDataFactory.createMultipleChannelMembers(10)
        val memberToMute = members[0]

        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(members)

        val mutedMember = memberToMute.copy(isMuted = true)
        val updatedMembersAfterMute = members.map {
            if (it.userId == memberToMute.userId) mutedMember else it
        }

        whenever(mockChannelRepository.muteMember(testChannelId, memberToMute.userId))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(updatedMembersAfterMute)

        channelViewModel.muteMember(testChannelId, memberToMute.userId)

        verify(mockChannelRepository).muteMember(testChannelId, memberToMute.userId)

        val unmutedMember = mutedMember.copy(isMuted = false)
        val updatedMembersAfterUnmute = updatedMembersAfterMute.map {
            if (it.userId == memberToMute.userId) unmutedMember else it
        }

        whenever(mockChannelRepository.unmuteMember(testChannelId, memberToMute.userId))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(updatedMembersAfterUnmute)

        channelViewModel.muteMember(testChannelId, memberToMute.userId)

        verify(mockChannelRepository).unmuteMember(testChannelId, memberToMute.userId)
    }

    @Test
    fun testRemoveMemberWorkflow() {
        val members = TestDataFactory.createMultipleChannelMembers(10)
        val memberToRemove = members[5]

        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(members)

        val updatedMembers = members.filter { it.userId != memberToRemove.userId }

        whenever(mockChannelRepository.removeMember(testChannelId, memberToRemove.userId))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(updatedMembers)

        channelViewModel.removeMember(testChannelId, memberToRemove.userId)

        verify(mockChannelRepository).removeMember(testChannelId, memberToRemove.userId)
        assertEquals(9, updatedMembers.size)
    }

    @Test
    fun testChannelPrivacyToggleWorkflow() {
        var channel = TestDataFactory.createTestChannel(
            id = testChannelId,
            isPrivate = false
        )

        whenever(mockChannelRepository.getChannel(testChannelId))
            .thenReturn(channel)

        val privateChannel = channel.copy(isPrivate = true)

        whenever(mockChannelRepository.updateChannelPrivacy(testChannelId, true))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannel(testChannelId))
            .thenReturn(privateChannel)

        channelViewModel.updateChannelPrivacy(testChannelId, true)

        verify(mockChannelRepository).updateChannelPrivacy(testChannelId, true)

        val publicChannel = channel.copy(isPrivate = false)

        whenever(mockChannelRepository.updateChannelPrivacy(testChannelId, false))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannel(testChannelId))
            .thenReturn(publicChannel)

        channelViewModel.updateChannelPrivacy(testChannelId, false)

        verify(mockChannelRepository).updateChannelPrivacy(testChannelId, false)
    }

    @Test
    fun testMemberCategorization() {
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

        val categorizedModerators = allMembers.filter { it.role == "moderator" }
        val categorizedMutedMembers = allMembers.filter { it.isMuted }
        val categorizedActiveMembers = allMembers.filter { it.isActive }
        val categorizedInactiveMembers = allMembers.filter { !it.isActive }

        assertEquals(3, categorizedModerators.size)
        assertTrue(categorizedMutedMembers.size > 0)
        assertTrue(categorizedActiveMembers.size > 0)
        assertEquals(5, categorizedInactiveMembers.size)
    }

    @Test
    fun testBulkMemberOperations() {
        val members = TestDataFactory.createMultipleChannelMembers(20, moderatorCount = 2)

        val membersToMute = members.take(5)
        membersToMute.forEach { member ->
            whenever(mockChannelRepository.muteMember(testChannelId, member.userId))
                .thenReturn(Unit)
        }

        membersToMute.forEach { member ->
            channelViewModel.muteMember(testChannelId, member.userId)
        }

        membersToMute.forEach { member ->
            verify(mockChannelRepository).muteMember(testChannelId, member.userId)
        }
    }

    @Test
    fun testRealTimeMemberStatusUpdates() {
        val members = TestDataFactory.createMultipleChannelMembers(5)

        whenever(mockChannelRepository.getChannelMembers(testChannelId))
            .thenReturn(members)

        channelViewModel.loadChannelMembers(testChannelId)

        assertEquals(5, channelViewModel.channelMembers.value?.size)

        val updatedMembers = members.map { member ->
            if (member.isActive) member.copy(isActive = false) else member
        }

        channelViewModel.onMemberStatusChanged(updatedMembers)

        val inactiveMembers = channelViewModel.channelMembers.value?.filter { !it.isActive }
        assertTrue((inactiveMembers?.size ?: 0) > 0)
    }

    @Test
    fun testPermissionDeniedErrorHandling() {
        val targetMember = TestDataFactory.createTestChannelMember(
            userId = "other_user",
            username = "other_user"
        )

        whenever(mockChannelRepository.promoteMember(testChannelId, targetMember.userId))
            .thenThrow(RuntimeException("Permission denied"))

        channelViewModel.promoteMember(testChannelId, targetMember.userId)

        assertNotNull(channelViewModel.errorMessage.value)
        assertTrue(channelViewModel.errorMessage.value?.contains("Permission") ?: false)
    }

    @Test
    fun testMemberNotFoundErrorHandling() {
        whenever(mockChannelRepository.muteMember(testChannelId, "non_existent_user"))
            .thenThrow(RuntimeException("Member not found"))

        channelViewModel.muteMember(testChannelId, "non_existent_user")

        assertNotNull(channelViewModel.errorMessage.value)
    }
}
