package com.freetime.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.freetime.app.ui.screens.*

/**
 * Navigation Graph for FreeTime App
 */

sealed class Route(val path: String) {
    object Login : Route("login")
    object Home : Route("home")
    object Chat : Route("chat/{chatId}?acceptCall={acceptCall}&callId={callId}") {
        fun createRoute(chatId: String, acceptCall: Boolean = false, callId: String = "") =
            "chat/$chatId?acceptCall=$acceptCall&callId=$callId"
    }
    object Profile : Route("profile")
    object Settings : Route("settings")
    object FriendRequests : Route("friendRequests")
    object SearchFriends : Route("searchFriends")
    object GroupVoting : Route("groupVoting/{groupId}") {
        fun createRoute(groupId: String) = "groupVoting/$groupId"
    }
    object GroupSettings : Route("groupSettings/{groupId}") {
        fun createRoute(groupId: String) = "groupSettings/$groupId"
    }
    object ChannelAdmin : Route("channelAdmin/{channelId}") {
        fun createRoute(channelId: String) = "channelAdmin/$channelId"
    }
    object MediaList : Route("mediaList/{type}") {
        fun createRoute(type: String) = "mediaList/$type"
    }
    object ChannelMessages : Route("channelMessages/{channelId}") {
        fun createRoute(channelId: String) = "channelMessages/$channelId"
    }
    object UserProfile : Route("userProfile/{userId}") {
        fun createRoute(userId: String) = "userProfile/$userId"
    }
    object CreateGroup : Route("createGroup")
    object CreateChannel : Route("createChannel")
    object GroupChat : Route("groupChat/{groupId}") {
        fun createRoute(groupId: String) = "groupChat/$groupId"
    }
    object GroupInvite : Route("groupInvite/{groupId}") {
        fun createRoute(groupId: String) = "groupInvite/$groupId"
    }
    object JoinGroup : Route("join_group/{idOrCode}") {
        fun createRoute(idOrCode: String) = "join_group/$idOrCode"
    }
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    isLoggedIn: Boolean,
    onLoginSuccess: (() -> Unit)? = null,
    startDestination: String = if (isLoggedIn) Route.Home.path else Route.Login.path
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Auth Routes
        composable(Route.Login.path) {
            ModernLoginScreen(
                onLoginSuccess = {
                    onLoginSuccess?.invoke()
                    navController.navigate(Route.Home.path) {
                        popUpTo(Route.Login.path) { inclusive = true }
                    }
                }
            )
        }
        
        // Main App Routes
        composable(Route.Home.path) {
            ModernHomeScreen(
                onNavigateToChat = { chatId ->
                    if (chatId.startsWith("group:")) {
                        val groupId = chatId.removePrefix("group:")
                        navController.navigate(Route.GroupChat.createRoute(groupId))
                    } else {
                        navController.navigate(Route.Chat.createRoute(chatId))
                    }
                },
                onNavigateToSettings = { navController.navigate(Route.Settings.path) },
                onNavigateToSearchFriends = { navController.navigate(Route.SearchFriends.path) },
                onNavigateToFriendRequests = { navController.navigate(Route.FriendRequests.path) },
                onNavigateToCreateGroup = { navController.navigate(Route.CreateGroup.path) },
                onNavigateToCreateChannel = { navController.navigate(Route.CreateChannel.path) },
                onNavigateToProfile = { navController.navigate(Route.Profile.path) }
            )
        }
        
        composable(Route.Chat.path, arguments = listOf(
            androidx.navigation.navArgument("chatId") { type = androidx.navigation.NavType.StringType },
            androidx.navigation.navArgument("acceptCall") { type = androidx.navigation.NavType.BoolType; defaultValue = false },
            androidx.navigation.navArgument("callId") { type = androidx.navigation.NavType.StringType; defaultValue = "" }
        )) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            val acceptCall = backStackEntry.arguments?.getBoolean("acceptCall") ?: false
            val callIdArg = backStackEntry.arguments?.getString("callId") ?: ""
            
            ModernChatScreen(
                recipientId = chatId,
                chatName = "",
                acceptCallOnOpen = acceptCall,
                pendingCallId = callIdArg,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(Route.Home.path) {
                        popUpTo(Route.Home.path) { inclusive = false }
                    }
                },
                onViewProfile = { userId -> navController.navigateToUserProfile(userId) }
            )
        }
        
        composable(Route.Profile.path) {
            ProfileScreen(
                onLogoutClick = {
                    val prefs = com.freetime.app.data.local.SharedPreferencesHelper(navController.context)
                    prefs.clearAuthData()
                    navController.navigate(Route.Login.path) { popUpTo(0) { inclusive = true } }
                },
                onNavigateToSettings = { navController.navigate(Route.Settings.path) }
            )
        }

        composable(Route.Settings.path) {
            SettingsScreenEnhanced(
                onLogoutClick = {
                    val prefs = com.freetime.app.data.local.SharedPreferencesHelper(navController.context)
                    prefs.clearAuthData()
                    navController.navigate(Route.Login.path) { popUpTo(0) { inclusive = true } }
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        
        composable(Route.FriendRequests.path) {
            FriendRequestsScreen(onBackClick = { navController.popBackStack() })
        }
        
        composable(Route.SearchFriends.path) {
            SearchFriendsScreen(
                onNavigateBack = { navController.popBackStack() },
                onFriendAdded = {},
                onNavigateToFriendRequests = { navController.navigate(Route.FriendRequests.path) }
            )
        }
        
        composable(Route.CreateGroup.path) {
            CreateGroupScreen(
                onGroupCreated = { groupId ->
                    navController.popBackStack()
                    navController.navigate(Route.GroupChat.createRoute(groupId))
                },
                onCancel = { navController.popBackStack() }
            )
        }

        // Optimized Group Chat route - Screen handles its own loading
        composable(Route.GroupChat.path, arguments = listOf(
            androidx.navigation.navArgument("groupId") { type = androidx.navigation.NavType.StringType }
        )) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            
            GroupChatScreen(
                groupId = groupId,
                onNavigateBack = { navController.popBackStack() },
                onGroupLeft = { navController.popBackStack(Route.Home.path, inclusive = false) },
                onNavigateToSettings = { navController.navigateToGroupSettings(groupId) },
                onNavigateToInvite = { navController.navigateToGroupInvite(groupId) }
            )
        }

        composable(Route.CreateChannel.path) {
            CreateChannelScreen(
                onChannelCreated = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        
        composable(Route.GroupVoting.path) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            GroupVotingScreen(groupId = groupId, onBackClick = { navController.popBackStack() })
        }

        composable(Route.GroupSettings.path) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            GroupSettingsScreen(
                groupId = groupId,
                onBackClick = { navController.popBackStack() },
                onGroupUpdated = { /* GroupChatScreen will refresh via its own WebSocket/Effect logic */ },
                onGroupDeleted = { navController.popBackStack(Route.Home.path, inclusive = false) },
                onGroupLeft = { navController.popBackStack(Route.Home.path, inclusive = false) },
                onNavigateToVoting = { navController.navigateToGroupVoting(groupId) }
            )
        }
        
        composable(Route.UserProfile.path, arguments = listOf(
            androidx.navigation.navArgument("userId") { type = androidx.navigation.NavType.StringType }
        )) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            PublicProfileScreen(
                userId = userId,
                onBackClick = { navController.popBackStack() },
                onSendFriendRequest = {}
            )
        }

        composable(Route.GroupInvite.path, arguments = listOf(
            androidx.navigation.navArgument("groupId") { type = androidx.navigation.NavType.StringType }
        )) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val context = LocalContext.current
            val apiService = remember { com.freetime.app.api.FreeTimeApiService(context) }
            var groupName by remember { mutableStateOf("") }
            var isAdmin by remember { mutableStateOf(false) }

            LaunchedEffect(groupId) {
                apiService.getGroupDetails(groupId).onSuccess { g ->
                    groupName = g.name
                    isAdmin = g.admins.contains(apiService.getCurrentUserId())
                }
            }

            GroupMemberInviteScreen(
                groupId = groupId,
                groupName = groupName,
                isAdmin = isAdmin,
                onBackClick = { navController.popBackStack() },
                onInviteComplete = { navController.popBackStack() }
            )
        }

        composable(Route.ChannelAdmin.path, arguments = listOf(
            androidx.navigation.navArgument("channelId") { type = androidx.navigation.NavType.StringType }
        )) { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
            ChannelAdminPanelEnhanced(channelId = channelId, onBackClick = { navController.popBackStack() })
        }

        composable(Route.MediaList.path, arguments = listOf(
            androidx.navigation.navArgument("type") { type = androidx.navigation.NavType.StringType }
        )) {
            MediaGalleryScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Route.ChannelMessages.path, arguments = listOf(
            androidx.navigation.navArgument("channelId") { type = androidx.navigation.NavType.StringType }
        )) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Channel messages coming soon", color = Color.White)
            }
        }

        composable(Route.JoinGroup.path, arguments = listOf(
            androidx.navigation.navArgument("idOrCode") { type = androidx.navigation.NavType.StringType }
        )) { backStackEntry ->
            val idOrCode = backStackEntry.arguments?.getString("idOrCode") ?: ""
            JoinGroupRedirectScreen(
                idOrCode = idOrCode,
                onNavigateToGroup = { groupId ->
                    navController.navigate(Route.GroupChat.createRoute(groupId)) {
                        popUpTo(Route.JoinGroup.path) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Navigation Helper Extensions
 */
fun NavHostController.navigateToUserProfile(userId: String) {
    navigate(Route.UserProfile.createRoute(userId))
}

fun NavHostController.navigateToGroupVoting(groupId: String) {
    navigate(Route.GroupVoting.createRoute(groupId))
}

fun NavHostController.navigateToGroupSettings(groupId: String) {
    navigate(Route.GroupSettings.createRoute(groupId))
}

fun NavHostController.navigateToGroupInvite(groupId: String) {
    navigate(Route.GroupInvite.createRoute(groupId))
}
