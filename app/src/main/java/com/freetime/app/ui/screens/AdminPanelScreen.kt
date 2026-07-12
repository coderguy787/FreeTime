package com.freetime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.data.repository.AdminRepository
import com.freetime.app.ui.components.CyberpunkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    adminRepository: AdminRepository,
    onNavigateBack: () -> Unit
) {
    @Suppress("UNUSED_VARIABLE")
    var users by remember { mutableStateOf(emptyList<String>()) }
    var stats by remember { mutableStateOf(mapOf<String, Any>()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf("overview") }
    var searchQuery by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Check if user is admin
    val isAdmin = remember { adminRepository.isCurrentUserAdmin() }

    // Load admin data
    LaunchedEffect(Unit) {
        try {
            // Load stats
            val statsResult = adminRepository.getAdminStats()
            statsResult.onSuccess { stat ->
                stats = stat
            }
            isLoading = false
        } catch (e: Exception) {
            errorMessage = "Failed to load admin data: ${e.message}"
            isLoading = false
        }
    }

    if (!isAdmin) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Admin Access Required",
                fontSize = 20.sp,
                modifier = Modifier.padding(16.dp)
            )
            Button(onClick = onNavigateBack) {
                Text("Go Back")
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        // Top Bar
        TopAppBar(
            title = { Text("Admin Panel", fontSize = 20.sp) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFFFF00FF)
            )
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Tab Navigation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TabButton(
                        label = "Overview",
                        isSelected = selectedTab == "overview",
                        onClick = { selectedTab = "overview" }
                    )
                    TabButton(
                        label = "Users",
                        isSelected = selectedTab == "users",
                        onClick = { selectedTab = "users" }
                    )
                    TabButton(
                        label = "Settings",
                        isSelected = selectedTab == "settings",
                        onClick = { selectedTab = "settings" }
                    )
                }

                // Content based on selected tab
                when (selectedTab) {
                    "overview" -> AdminOverviewTab(stats)
                    "users" -> AdminUsersTab(adminRepository, searchQuery) { query ->
                        searchQuery = query
                    }
                    "settings" -> AdminSettingsTab()
                }

                // Error Display
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF303030)
                        )
                    ) {
                        Text(
                            errorMessage!!,
                            modifier = Modifier.padding(12.dp),
                            color = Color.Red,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFFFF00FF) else CyberpunkTheme.LightGray
        )
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun AdminOverviewTab(stats: Map<String, Any>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text("Dashboard", fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))

        // Statistics Cards
        LazyColumn {
            items(listOf(
                "Total Users" to (stats["totalUsers"] ?: 0),
                "Active Users" to (stats["activeUsers"] ?: 0),
                "Pending Requests" to (stats["pendingRequests"] ?: 0),
                "Reported Users" to (stats["reportedUsers"] ?: 0),
                "Blocked Users" to (stats["blockedUsers"] ?: 0)
            )) { (label, value) ->
                StatCard(label, value.toString())
            }
        }
    }
}

@Composable
private fun AdminUsersTab(
    adminRepository: AdminRepository,
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    var users by remember { mutableStateOf(emptyList<Map<String, Any>>()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Fetch users on compose
    LaunchedEffect(Unit) {
        try {
            adminRepository.getAllUsersFlow().collect { fetchedUsers ->
                // Parse users - handle both Map and Any types
                val parsedUsers = when {
                    fetchedUsers is List<*> -> {
                        fetchedUsers.mapNotNull { user ->
                            when (user) {
                                is Map<*, *> -> @Suppress("UNCHECKED_CAST") (user as Map<String, Any>)
                                else -> null
                            }
                        }
                    }
                    else -> emptyList()
                }
                users = parsedUsers
                isLoading = false
            }
        } catch (e: Exception) {
            error = "Failed to load users: ${e.message}"
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            label = { Text("Search users") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        Text("Users List (${users.size} total)", fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))

        // Loading/Error Status
        if (isLoading) {
            Text("Loading users...", fontSize = 12.sp, modifier = Modifier.padding(8.dp))
        } else if (error != null) {
            Text(error!!, fontSize = 12.sp, color = Color.Red, modifier = Modifier.padding(8.dp))
        }

        // Users List
        LazyColumn {
            items(users) { user ->
                val userId = user["_id"] as? String ?: user["id"] as? String ?: "Unknown"
                val role = user["role"] as? String ?: "USER"
                val isAdmin = role == "ADMIN"
                
                UserManagementItem(userId, isAdmin) { _ ->
                    // Handle user action
                }
            }
        }
    }
}

@Composable
private fun AdminSettingsTab() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text("System Settings", fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))

        SettingsField("API Base URL", "https://example.com/")
        SettingsField("Max Users", "1000")
        SettingsField("Rate Limit", "100 req/min")
        SettingsField("Session Timeout", "30 min")
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp)
            Text(
                value,
                fontSize = 20.sp,
                color = Color(0xFFFF00FF)
            )
        }
    }
}

@Composable
private fun UserManagementItem(
    userId: String,
    isAdmin: Boolean = false,
    onAction: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAdmin) Color(0xFFFFEBEE) else Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(userId.take(30), fontSize = 12.sp)
                    if (isAdmin) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFD32F2F), RoundedCornerShape(4.dp))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("ADMIN", fontSize = 8.sp, color = Color.White, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
                Text("Last active: 2 hours ago", fontSize = 10.sp, color = Color.Gray)
            }
            IconButton(
                onClick = { onAction(userId) }
            ) {
                Icon(Icons.Filled.Block, "Block")
            }
        }
    }
}

@Composable
private fun SettingsField(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        OutlinedTextField(
            value = value,
            onValueChange = {},
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
    }
}


