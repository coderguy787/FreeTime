package com.freetime.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freetime.app.data.models.ChannelMember
import com.freetime.app.data.models.Channel
import com.freetime.app.data.models.GroupVote
import com.freetime.app.data.models.VoteOption
import com.freetime.app.data.models.MediaEntity
import com.freetime.app.data.repository.WebSocketEventRepository
import com.freetime.app.data.repository.FriendSystemRepository
import com.freetime.app.data.repository.GroupVotingRepository
import com.freetime.app.data.repository.ChannelRepository
import com.freetime.app.data.repository.FriendRequest
import com.freetime.app.data.repository.Friend
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * FriendViewModel - Manages friend system UI state
 * 
 * Wires:
 * - Incoming/outgoing friend requests
 * - Accept/reject/cancel operations
 * - Real-time WebSocket updates
 * - Loading and error states
 */
class FriendViewModel(
    private val friendRepository: FriendSystemRepository,
    private val webSocketRepository: WebSocketEventRepository
) : ViewModel() {
    
    private val _incomingFriendRequests = MutableLiveData<List<FriendRequest>>(emptyList())
    val incomingFriendRequests: LiveData<List<FriendRequest>> = _incomingFriendRequests
    
    private val _outgoingFriendRequests = MutableLiveData<List<FriendRequest>>(emptyList())
    val outgoingFriendRequests: LiveData<List<FriendRequest>> = _outgoingFriendRequests
    
    private val _friends = MutableLiveData<List<Friend>>(emptyList())
    val friends: LiveData<List<Friend>> = _friends
    
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage
    
    init {
        setupWebSocketListeners()
    }
    
    fun loadFriendRequests() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val incoming = friendRepository.getIncomingRequests()
                val outgoing = friendRepository.getOutgoingRequests()
                val friendsList = friendRepository.getFriends()
                
                _incomingFriendRequests.value = incoming
                _outgoingFriendRequests.value = outgoing
                _friends.value = friendsList
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load requests: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun acceptFriendRequest(requestId: String) {
        viewModelScope.launch {
            try {
                friendRepository.acceptFriendRequest(requestId)
                loadFriendRequests()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to accept request: ${e.message}"
            }
        }
    }
    
    fun rejectFriendRequest(requestId: String) {
        viewModelScope.launch {
            try {
                friendRepository.rejectFriendRequest(requestId)
                loadFriendRequests()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to reject request: ${e.message}"
            }
        }
    }
    
    fun cancelFriendRequest(requestId: String) {
        viewModelScope.launch {
            try {
                friendRepository.cancelFriendRequest(requestId)
                loadFriendRequests()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to cancel request: ${e.message}"
            }
        }
    }
    
    private fun setupWebSocketListeners() {
        viewModelScope.launch {
            // Listen for friend request received events
            webSocketRepository.friendRequestReceived.observeForever { _event ->
                loadFriendRequests()
            }
            
            // Listen for friend accepted events
            webSocketRepository.friendRequestAccepted.observeForever { _event ->
                loadFriendRequests()
            }
            
            // Listen for friend rejected events
            webSocketRepository.friendRequestRejected.observeForever { _event ->
                loadFriendRequests()
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}

/**
 * GroupVotingViewModel - Manages group voting UI state
 * 
 * Wires:
 * - Active and completed votes
 * - Vote casting with real-time updates
 * - Vote history tracking
 * - WebSocket live vote count updates
 */
class GroupVotingViewModel(
    private val votingRepository: GroupVotingRepository,
    private val webSocketRepository: WebSocketEventRepository
) : ViewModel() {
    
    private val _activeVotes = MutableLiveData<List<GroupVote>>(emptyList())
    val activeVotes: LiveData<List<GroupVote>> = _activeVotes
    
    private val _completedVotes = MutableLiveData<List<GroupVote>>(emptyList())
    val completedVotes: LiveData<List<GroupVote>> = _completedVotes
    
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage
    
    init {
        setupWebSocketListeners()
    }
    
    fun loadGroupVotes(groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val active = votingRepository.getActiveVotes(groupId)
                val completed = votingRepository.getCompletedVotes(groupId)
                
                _activeVotes.value = active
                _completedVotes.value = completed
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load votes: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun castVote(voteId: String, optionId: String) {
        viewModelScope.launch {
            try {
                votingRepository.castVote(voteId, optionId)
                // Reload to refresh counts
                val groupId = _activeVotes.value?.firstOrNull()?.groupId ?: return@launch
                loadGroupVotes(groupId)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to cast vote: ${e.message}"
            }
        }
    }
    
    private fun setupWebSocketListeners() {
        viewModelScope.launch {
            // Listen for vote initiated events
            webSocketRepository.groupVoteInitiated.observeForever { _event ->
                // Refresh votes when new voting session starts
                val groupId = _activeVotes.value?.firstOrNull()?.groupId
                if (groupId != null) {
                    loadGroupVotes(groupId)
                }
            }
            
            // Listen for vote cast events (real-time vote count updates)
            webSocketRepository.voteCast.observeForever { _event ->
                val groupId = _activeVotes.value?.firstOrNull()?.groupId
                if (groupId != null) {
                    loadGroupVotes(groupId)
                }
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}

/**
 * ChannelViewModel - Manages channel admin UI state
 * 
 * Wires:
 * - Channel details and settings
 * - Member list with real-time updates
 * - Promotion/demotion operations
 * - Muting and removal operations
 * - WebSocket member status updates
 */
class ChannelViewModel(
    private val channelRepository: ChannelRepository,
    private val webSocketRepository: WebSocketEventRepository
) : ViewModel() {
    
    private val _currentChannel = MutableLiveData<Channel>()
    val currentChannel: LiveData<Channel> = _currentChannel
    
    private val _channelMembers = MutableLiveData<List<ChannelMember>>(emptyList())
    val channelMembers: LiveData<List<ChannelMember>> = _channelMembers
    
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage
    
    init {
        setupWebSocketListeners()
    }
    
    fun loadChannel(channelId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val channel = channelRepository.getChannel(channelId)
                _currentChannel.value = channel
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load channel: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun loadChannelMembers(channelId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val members = channelRepository.getChannelMembers(channelId)
                _channelMembers.value = members
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load members: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun promoteMember(channelId: String, userId: String) {
        viewModelScope.launch {
            try {
                channelRepository.promoteMember(channelId, userId)
                loadChannelMembers(channelId)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to promote member: ${e.message}"
            }
        }
    }
    
    fun demoteMember(channelId: String, userId: String) {
        viewModelScope.launch {
            try {
                channelRepository.demoteMember(channelId, userId)
                loadChannelMembers(channelId)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to demote member: ${e.message}"
            }
        }
    }
    
    fun muteMember(channelId: String, userId: String) {
        viewModelScope.launch {
            try {
                channelRepository.muteMember(channelId, userId)
                loadChannelMembers(channelId)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to mute member: ${e.message}"
            }
        }
    }
    
    fun removeMember(channelId: String, userId: String) {
        viewModelScope.launch {
            try {
                channelRepository.removeMember(channelId, userId)
                loadChannelMembers(channelId)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to remove member: ${e.message}"
            }
        }
    }
    
    fun updateChannelPrivacy(channelId: String, isPrivate: Boolean) {
        viewModelScope.launch {
            try {
                channelRepository.updateChannelPrivacy(channelId, isPrivate)
                loadChannel(channelId)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update privacy: ${e.message}"
            }
        }
    }
    
    private fun setupWebSocketListeners() {
        viewModelScope.launch {
            // Listen for member promoted events
            webSocketRepository.memberPromoted.observeForever { _event ->
                val channelId = _currentChannel.value?.id ?: return@observeForever
                loadChannelMembers(channelId)
            }
            
            // Listen for member demoted events
            webSocketRepository.memberDemoted.observeForever { _event ->
                val channelId = _currentChannel.value?.id ?: return@observeForever
                loadChannelMembers(channelId)
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}
