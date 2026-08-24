package com.freetime.app.ui.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// empty placeholder screens
@Composable
fun HomeScreen(
    _onLogoutClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    _onNavigateToChat: (String) -> Unit = {},
    _onNavigateToContacts: () -> Unit = {},
    _onNavigateToSettings: () -> Unit = {}
) {
}

@Composable
fun ChatScreen(
    _conversationId: String = "",
    _participantName: String = "",
    _onBackClick: () -> Unit = {},
    _recipientName: String = ""
) {
}

@Composable
fun ContactsScreen(
    modifier: Modifier = Modifier,
    _onNavigateToChat: (String) -> Unit = {},
    _onContactClick: (String) -> Unit = {}
) {
}

@Composable
fun SettingsScreen(
    _onLogoutClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    _onBackClick: () -> Unit = {}
) {
}

@Composable
fun FriendRequestsScreen(
    modifier: Modifier = Modifier,
    _onFriendRequestsClick: (String) -> Unit = {},
    _onBackClick: () -> Unit = {}
) {
}

@Composable
fun TwoFactorSetupScreen(
    _onSetupComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
}

@Composable
fun ChannelAdminPanel(
    modifier: Modifier = Modifier
) {
}

