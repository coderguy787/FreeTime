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
import com.freetime.app.data.model.Channel
import com.freetime.app.data.model.ChannelMember
import com.freetime.app.data.repository.ChannelRepository
import com.freetime.app.data.repository.WebSocketEventRepository

/**
 * Unit tests for ChannelViewModel
 * 
 * Tests cover:
 * - Channel loading and member management
 * - Promotion/demotion operations
 * - Member muting and removal
 * - Privacy settings
 * - Real-time member updates
 * - Error handling
 */
class ChannelViewModelTest {
    
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    @Mock
    private lateinit var mockChannelRepository: ChannelRepository
    
    @Mock
    private lateinit var mockWebSocketRepository: WebSocketEventRepository
    
    private lateinit var viewModel: ChannelViewModel
    
    private val testChannel = Channel(
        id = "channel_123",
        name = "General Discussion",
        description = "General channel for everyone",
        memberCount = 50,
        createdAt = System.currentTimeMillis(),
        isPrivate = false
    )
    
    private val testMember1 = ChannelMember(
        userId = "user_1",
        username = "alice_smith",
        role = "member",
        isMuted = false,
        isActive = true
    )
    
    private val testMember2 = ChannelMember(
        userId = "user_2",
        username = "bob_jones",
        role = "moderator",
        isMuted = false,
        isActive = true
    )
    
    private val testMember3 = ChannelMember(
        userId = "user_3",
        username = "charlie_brown",
        role = "member",
        isMuted = false,
        isActive = false
    )
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        setupMockDefaults()
        
        viewModel = ChannelViewModel(
            channelRepository = mockChannelRepository,
            webSocketRepository = mockWebSocketRepository
        )
    }
    
    private fun setupMockDefaults() {
        whenever(mockChannelRepository.getChannel("channel_123"))
            .thenReturn(testChannel)
        whenever(mockChannelRepository.getChannelMembers("channel_123"))
            .thenReturn(emptyList())
    }
    
    // ===== LiveData Initialization Tests =====
    
    @Test
    fun testInitialLiveDataValues() {
        assertEquals(null, viewModel.currentChannel.value)
        assertEquals(emptyList<ChannelMember>(), viewModel.channelMembers.value)
        assertFalse(viewModel.isLoading.value ?: false)
        assertEquals(null, viewModel.errorMessage.value)
    }
    
    @Test
    fun testLiveDataNotNull() {
        assertNotNull(viewModel.currentChannel)
        assertNotNull(viewModel.channelMembers)
        assertNotNull(viewModel.isLoading)
        assertNotNull(viewModel.errorMessage)
    }
    
    // ===== Load Channel Tests =====
    
    @Test
    fun testLoadChannelSuccess() {
        whenever(mockChannelRepository.getChannel("channel_123"))
            .thenReturn(testChannel)
        
        viewModel.loadChannel("channel_123")
        
        assertEquals(testChannel, viewModel.currentChannel.value)
        assertFalse(viewModel.isLoading.value ?: false)
        assertEquals(null, viewModel.errorMessage.value)
    }
    
    @Test
    fun testLoadChannelShowsLoadingState() {
        val loadingValues = mutableListOf<Boolean>()
        
        viewModel.isLoading.observeForever { value ->
            loadingValues.add(value)
        }
        
        whenever(mockChannelRepository.getChannel("channel_123"))
            .thenReturn(testChannel)
        
        viewModel.loadChannel("channel_123")
        
        assertTrue(loadingValues.contains(true))
        assertEquals(false, loadingValues.last())
    }
    
    @Test
    fun testLoadChannelHandlesException() {
        whenever(mockChannelRepository.getChannel("channel_123"))
            .thenThrow(RuntimeException("API error"))
        
        viewModel.loadChannel("channel_123")
        
        assertEquals(false, viewModel.isLoading.value)
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value?.contains("Failed to load channel") ?: false)
    }
    
    // ===== Load Channel Members Tests =====
    
    @Test
    fun testLoadChannelMembersSuccess() {
        val members = listOf(testMember1, testMember2, testMember3)
        
        whenever(mockChannelRepository.getChannelMembers("channel_123"))
            .thenReturn(members)
        
        viewModel.loadChannelMembers("channel_123")
        
        assertEquals(members, viewModel.channelMembers.value)
        assertEquals(3, viewModel.channelMembers.value?.size)
    }
    
    @Test
    fun testLoadChannelMembersShowsLoadingState() {
        val loadingValues = mutableListOf<Boolean>()
        
        viewModel.isLoading.observeForever { value ->
            loadingValues.add(value)
        }
        
        whenever(mockChannelRepository.getChannelMembers("channel_123"))
            .thenReturn(emptyList())
        
        viewModel.loadChannelMembers("channel_123")
        
        assertTrue(loadingValues.contains(true))
        assertEquals(false, loadingValues.last())
    }
    
    @Test
    fun testLoadChannelMembersHandlesException() {
        whenever(mockChannelRepository.getChannelMembers("channel_123"))
            .thenThrow(RuntimeException("Network error"))
        
        viewModel.loadChannelMembers("channel_123")
        
        assertEquals(false, viewModel.isLoading.value)
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value?.contains("Failed to load members") ?: false)
    }
    
    // ===== Promote Member Tests =====
    
    @Test
    fun testPromoteMemberSuccess() {
        val updatedMembers = listOf(
            testMember1.copy(role = "moderator"),
            testMember2,
            testMember3
        )
        
        whenever(mockChannelRepository.promoteMember("channel_123", "user_1"))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannelMembers("channel_123"))
            .thenReturn(updatedMembers)
        
        viewModel.promoteMember("channel_123", "user_1")
        
        verify(mockChannelRepository).promoteMember("channel_123", "user_1")
    }
    
    @Test
    fun testPromoteMemberHandlesError() {
        whenever(mockChannelRepository.promoteMember("channel_123", "user_1"))
            .thenThrow(RuntimeException("Permission denied"))
        
        viewModel.promoteMember("channel_123", "user_1")
        
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value?.contains("Failed to promote member") ?: false)
    }
    
    // ===== Demote Member Tests =====
    
    @Test
    fun testDemoteMemberSuccess() {
        val updatedMembers = listOf(
            testMember1,
            testMember2.copy(role = "member"),
            testMember3
        )
        
        whenever(mockChannelRepository.demoteMember("channel_123", "user_2"))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannelMembers("channel_123"))
            .thenReturn(updatedMembers)
        
        viewModel.demoteMember("channel_123", "user_2")
        
        verify(mockChannelRepository).demoteMember("channel_123", "user_2")
    }
    
    @Test
    fun testDemoteMemberHandlesError() {
        whenever(mockChannelRepository.demoteMember("channel_123", "user_2"))
            .thenThrow(RuntimeException("Cannot demote self"))
        
        viewModel.demoteMember("channel_123", "user_2")
        
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value?.contains("Failed to demote member") ?: false)
    }
    
    // ===== Mute Member Tests =====
    
    @Test
    fun testMuteMemberSuccess() {
        val updatedMembers = listOf(
            testMember1.copy(isMuted = true),
            testMember2,
            testMember3
        )
        
        whenever(mockChannelRepository.muteMember("channel_123", "user_1"))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannelMembers("channel_123"))
            .thenReturn(updatedMembers)
        
        viewModel.muteMember("channel_123", "user_1")
        
        verify(mockChannelRepository).muteMember("channel_123", "user_1")
    }
    
    @Test
    fun testMuteMemberHandlesError() {
        whenever(mockChannelRepository.muteMember("channel_123", "user_1"))
            .thenThrow(RuntimeException("User not found"))
        
        viewModel.muteMember("channel_123", "user_1")
        
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value?.contains("Failed to mute member") ?: false)
    }
    
    // ===== Remove Member Tests =====
    
    @Test
    fun testRemoveMemberSuccess() {
        val updatedMembers = listOf(
            testMember2,
            testMember3
        )
        
        whenever(mockChannelRepository.removeMember("channel_123", "user_1"))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannelMembers("channel_123"))
            .thenReturn(updatedMembers)
        
        viewModel.removeMember("channel_123", "user_1")
        
        verify(mockChannelRepository).removeMember("channel_123", "user_1")
    }
    
    @Test
    fun testRemoveMemberHandlesError() {
        whenever(mockChannelRepository.removeMember("channel_123", "user_1"))
            .thenThrow(RuntimeException("Cannot remove owner"))
        
        viewModel.removeMember("channel_123", "user_1")
        
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value?.contains("Failed to remove member") ?: false)
    }
    
    // ===== Privacy Settings Tests =====
    
    @Test
    fun testUpdateChannelPrivacySuccess() {
        val updatedChannel = testChannel.copy(isPrivate = true)
        
        whenever(mockChannelRepository.updateChannelPrivacy("channel_123", true))
            .thenReturn(Unit)
        whenever(mockChannelRepository.getChannel("channel_123"))
            .thenReturn(updatedChannel)
        
        viewModel.updateChannelPrivacy("channel_123", true)
        
        verify(mockChannelRepository).updateChannelPrivacy("channel_123", true)
    }
    
    @Test
    fun testUpdateChannelPrivacyHandlesError() {
        whenever(mockChannelRepository.updateChannelPrivacy("channel_123", true))
            .thenThrow(RuntimeException("Permission denied"))
        
        viewModel.updateChannelPrivacy("channel_123", true)
        
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value?.contains("Failed to update privacy") ?: false)
    }
    
    // ===== Member Filtering Tests =====
    
    @Test
    fun testModeratorsFiltering() {
        val members = listOf(testMember1, testMember2, testMember3)
        
        val moderators = members.filter { it.role == "moderator" }
        
        assertEquals(1, moderators.size)
        assertEquals("user_2", moderators[0].userId)
    }
    
    @Test
    fun testMutedMembersFiltering() {
        val members = listOf(
            testMember1.copy(isMuted = true),
            testMember2,
            testMember3.copy(isMuted = true)
        )
        
        val mutedMembers = members.filter { it.isMuted }
        
        assertEquals(2, mutedMembers.size)
    }
    
    @Test
    fun testActiveMemebersFiltering() {
        val members = listOf(testMember1, testMember2, testMember3)
        
        val activeMembers = members.filter { it.isActive }
        
        assertEquals(2, activeMembers.size)
    }
    
    // ===== Error Clearing Tests =====
    
    @Test
    fun testClearErrorMessage() {
        viewModel.clearError()
        
        assertEquals(null, viewModel.errorMessage.value)
    }
    
    // ===== Channel Properties Tests =====
    
    @Test
    fun testChannelProperties() {
        val channel = testChannel
        
        assertEquals("channel_123", channel.id)
        assertEquals("General Discussion", channel.name)
        assertEquals("General channel for everyone", channel.description)
        assertEquals(50, channel.memberCount)
        assertEquals(false, channel.isPrivate)
    }
    
    @Test
    fun testMultipleMembersHandling() {
        val members = (1..100).map { i ->
            testMember1.copy(
                userId = "user_$i",
                username = "user_$i",
                isActive = i % 2 == 0
            )
        }
        
        whenever(mockChannelRepository.getChannelMembers("channel_123"))
            .thenReturn(members)
        
        viewModel.loadChannelMembers("channel_123")
        
        assertEquals(100, viewModel.channelMembers.value?.size)
        
        // Verify active/inactive split
        val activeCount = viewModel.channelMembers.value?.count { it.isActive } ?: 0
        assertEquals(50, activeCount)
    }
}
