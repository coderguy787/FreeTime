# 💻 SecureChat Development Guide

**Last Updated:** February 2026 | **Status:** ✅ Ready for Integration

---

## 🚀 Quick Start

### Prerequisites

```bash
# Required
- Android Studio (Jellyfish or later)
- JDK 17+
- Android SDK 31+ (API level 31)
- Git
- Node.js 18+ (for server)
```

### Setup (5 minutes)

```bash
# 1. Clone repository
git clone https://github.com/yourname/securechat.git
cd securechat

# 2. Configure gradle.properties
cp gradle.properties.template gradle.properties
# Edit gradle.properties with your settings

# 3. Open in Android Studio
# File > Open > Select SecureChatApp folder

# 4. Build project
./gradlew build

# 5. Run on emulator/device
./gradlew installDebug
```

---

## 📁 Project Structure

```
SecureChatApp/
├── app/
│   ├── src/main/
│   │   ├── java/com/freetime/app/
│   │   │   ├── ui/                    ← UI Layer
│   │   │   │   ├── screens/
│   │   │   │   ├── components/
│   │   │   │   └── theme/
│   │   │   ├── data/                  ← Data Layer
│   │   │   │   ├── repository/        ← Repositories
│   │   │   │   ├── local/             ← Room DB
│   │   │   │   └── remote/            ← APIs
│   │   │   ├── viewmodel/             ← ViewModels
│   │   │   ├── security/              ← Encryption
│   │   │   ├── network/               ← WebSocket
│   │   │   └── utils/                 ← Utilities
│   │   └── res/
│   │       ├── layout/
│   │       ├── drawable/
│   │       └── values/
│   └── build.gradle
├── master-server/                     ← Backend
│   ├── api/
│   ├── websocket/
│   ├── database/
│   └── config/
└── README.md
```

---

## 🏗️ Architecture Layers

### 1. UI Layer (Jetpack Compose)

```kotlin
// screens/ChatScreen.kt
@Composable
fun ChatScreen(
    chatId: String,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState(emptyList())
    
    Column {
        ChatMessages(messages)
        MessageInput(
            onSend = { viewModel.sendMessage(it) }
        )
    }
}
```

### 2. ViewModel Layer (State Management)

```kotlin
// viewmodel/ChatViewModel.kt
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {
    
    val messages: StateFlow<List<Message>> = 
        chatRepository.getMessages(chatId)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    fun sendMessage(text: String) {
        viewModelScope.launch {
            chatRepository.sendMessage(Message(text))
        }
    }
}
```

### 3. Repository Layer (Data Access)

```kotlin
// repository/ChatRepository.kt
@Singleton
class ChatRepository @Inject constructor(
    private val database: AppDatabase,
    private val apiService: ApiService,
    private val encryptionManager: EncryptionManager
) {
    
    suspend fun sendMessage(message: Message) {
        val encrypted = encryptionManager.encrypt(message.text)
        database.messageDao().insert(MessageEntity(encrypted))
        apiService.sendMessage(SendMessageRequest(encrypted))
    }
    
    fun getMessages(chatId: String): Flow<List<Message>> {
        return database.messageDao().getMessages(chatId)
            .map { entities -> entities.map { it.toMessage() } }
    }
}
```

### 4. Data Layer (Local & Remote)

```kotlin
// local/dao/MessageDao.kt
@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: MessageEntity)
    
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC")
    fun getMessages(chatId: String): Flow<List<MessageEntity>>
    
    @Update
    suspend fun update(message: MessageEntity)
    
    @Delete
    suspend fun delete(message: MessageEntity)
}

// remote/ApiService.kt
interface ApiService {
    @POST("api/messages/send")
    suspend fun sendMessage(@Body request: SendMessageRequest): ApiResponse
    
    @GET("api/messages/{chatId}")
    suspend fun getMessages(@Path("chatId") chatId: String): List<Message>
}
```

---

## 🛠️ Development Workflow

### Adding a New Feature

#### 1. Create Data Model

```kotlin
// data/model/Chat.kt
data class Chat(
    val chatId: String,
    val userId: String,
    val recipientId: String,
    val lastMessage: String,
    val timestamp: Long
)

// data/local/entity/ChatEntity.kt
@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val chatId: String,
    val userId: String,
    val recipientId: String,
    val lastMessage: String,
    val timestamp: Long
)
```

#### 2. Create DAO

```kotlin
// data/local/dao/ChatDao.kt
@Dao
interface ChatDao {
    @Insert
    suspend fun insert(chat: ChatEntity)
    
    @Query("SELECT * FROM chats WHERE userId = :userId")
    fun getUserChats(userId: String): Flow<List<ChatEntity>>
    
    @Update
    suspend fun update(chat: ChatEntity)
    
    @Delete
    suspend fun delete(chat: ChatEntity)
}
```

#### 3. Create Repository

```kotlin
// data/repository/ChatRepository.kt
@Singleton
class ChatRepository @Inject constructor(
    private val database: AppDatabase,
    private val apiService: ApiService
) {
    
    suspend fun createChat(chat: Chat) {
        database.chatDao().insert(chat.toEntity())
        apiService.createChat(CreateChatRequest(chat))
    }
    
    fun getUserChats(userId: String): Flow<List<Chat>> {
        return database.chatDao().getUserChats(userId)
            .map { entities -> entities.map { it.toChat() } }
    }
}
```

#### 4. Create ViewModel

```kotlin
// viewmodel/ChatListViewModel.kt
@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    
    val chats: StateFlow<List<Chat>> = authRepository.currentUser
        .flatMapLatest { user ->
            chatRepository.getUserChats(user.userId)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
```

#### 5. Create UI Screen

```kotlin
// ui/screens/ChatListScreen.kt
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel = hiltViewModel(),
    onChatSelected: (String) -> Unit
) {
    val chats by viewModel.chats.collectAsState()
    
    LazyColumn {
        items(chats) { chat ->
            ChatItem(
                chat = chat,
                onClick = { onChatSelected(chat.chatId) }
            )
        }
    }
}
```

#### 6. Register in DI Container

```kotlin
// di/RepositoryModule.kt
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Singleton
    @Provides
    fun provideChatRepository(
        database: AppDatabase,
        apiService: ApiService
    ): ChatRepository {
        return ChatRepository(database, apiService)
    }
}
```

#### 7. Add to Navigation

```kotlin
// ui/navigation/Navigation.kt
@Composable
fun Navigation() {
    NavHost(navController, startDestination = "chat_list") {
        composable("chat_list") {
            ChatListScreen(
                onChatSelected = { chatId ->
                    navController.navigate("chat/$chatId")
                }
            )
        }
        composable("chat/{chatId}") { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
            ChatScreen(chatId = chatId)
        }
    }
}
```

---

## 🧪 Testing

### Unit Tests

```kotlin
// test/ChatRepositoryTest.kt
class ChatRepositoryTest {
    
    private lateinit var chatRepository: ChatRepository
    private val mockDatabase = mockk<AppDatabase>()
    private val mockApiService = mockk<ApiService>()
    
    @Before
    fun setup() {
        chatRepository = ChatRepository(mockDatabase, mockApiService)
    }
    
    @Test
    fun testSendMessage() = runTest {
        val message = Message("Hello")
        chatRepository.sendMessage(message)
        
        coVerify { mockApiService.sendMessage(any()) }
    }
}
```

### Integration Tests

```kotlin
// androidTest/ChatScreenTest.kt
class ChatScreenTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun testMessageDisplayed() {
        composeTestRule.setContent {
            ChatScreen(chatId = "test123")
        }
        
        composeTestRule.onNodeWithText("Hello").assertIsDisplayed()
    }
}
```

### UI Tests

```bash
# Run on emulator/device
./gradlew connectedAndroidTest
```

---

## 🐛 Debugging

### Enable Debug Logging

```kotlin
// In Application class
if (BuildConfig.DEBUG) {
    Timber.plant(Timber.DebugTree())
}

// Log everywhere
Timber.d("Debug message")
Timber.w("Warning: %s", message)
Timber.e(exception, "Error occurred")
```

### Android Studio Debugger

```
1. Set breakpoint (click line number)
2. Run in debug mode: Shift+F9
3. Program pauses at breakpoint
4. Inspect variables in Variables panel
5. Step through code (F10 = step over, F11 = step in)
```

### View Database (Room)

```
1. Device File Explorer: data/data/com.freetime.app/databases/
2. Download .db file
3. Use DB Browser for SQLite to inspect
```

### View Logs

```bash
# In Android Studio: Logcat tab
# Or via command line:
adb logcat | grep "freetime"

# Clear logs:
adb logcat -c
```

---

## 📦 Build Variants

### Debug Build

```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
# Use for development and testing
```

### Release Build

```bash
# Requires signing key
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
# Use for production deployment
```

### Custom Flavor

```gradle
// build.gradle
flavorDimensions "server"

productFlavors {
    dev {
        dimension "server"
        buildConfigField "String", "SERVER_HOST", "\"10.0.2.2\""
    }
    prod {
        dimension "server"
        buildConfigField "String", "SERVER_HOST", "\"freetime.com\""
    }
}

// Build: ./gradlew assembleDevDebug
```

---

## 📚 Code Style

### Kotlin Conventions

```kotlin
// ✅ Good
class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {
    
    val messages = repository.getMessages()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    fun sendMessage(text: String) {
        viewModelScope.launch {
            repository.sendMessage(Message(text))
        }
    }
}

// ❌ Bad
class ChatViewModel : ViewModel {
    var messages = null
    
    fun sendMessage(text) {
        launch {
            sendMsg(text)
        }
    }
}
```

### Naming Conventions

```kotlin
// Classes and Interfaces
class ChatViewModel         // PascalCase
interface ChatRepository    // PascalCase

// Functions and variables
fun sendMessage()           // camelCase
val messageCount = 0        // camelCase
private val _messages       // Prefix private mutable with _

// Constants
const val MAX_MESSAGES = 100  // UPPER_CASE

// UI Components
@Composable
fun ChatScreen()            // PascalCase

// Resources
R.id.send_button            // snake_case
R.string.welcome_message    // snake_case
```

---

## 🚀 Performance Tips

### Memory Optimization

```kotlin
// ✅ Use LazyColumn instead of Column
LazyColumn {
    items(messages) { message ->
        MessageItem(message)
    }
}

// ❌ Avoid loading all items at once
Column {
    messages.forEach { message ->
        MessageItem(message)  // All rendered immediately
    }
}
```

### Recomposition Optimization

```kotlin
// ✅ Use remember to avoid recomposition
@Composable
fun MessageItem(message: Message) {
    val expanded = remember { mutableStateOf(false) }
    // Only this composable recomposes when state changes
}

// ❌ Don't create state at parent level if not needed
@Composable
fun MessageList(messages: List<Message>) {
    val expanded = remember { mutableStateOf(false) }
    // Causes entire list to recompose
    LazyColumn {
        items(messages) { MessageItem(it) }
    }
}
```

### Database Query Optimization

```kotlin
// ✅ Use indexes for common queries
@Entity(
    tableName = "messages",
    indices = [Index(value = ["chatId", "timestamp"])]
)
data class MessageEntity(...)

// ✅ Limit results
@Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 50")
fun getRecentMessages(chatId: String): Flow<List<Message>>

// ❌ Don't load all messages
@Query("SELECT * FROM messages")
fun getAllMessages(): Flow<List<Message>>
```

---

## ✅ Pre-Commit Checklist

Before committing code:

```
□ Code compiles without errors
□ All tests pass: ./gradlew test
□ No lint warnings: ./gradlew lint
□ Code formatted properly
□ No hardcoded strings
□ No secrets/keys in code
□ Comments explain why, not what
□ Functions are small (<30 lines)
□ Error handling included
□ Null safety checked
□ Performance optimized
□ UI tested on multiple devices
```

---

## 📖 Learning Resources

- [Android Developer Docs](https://developer.android.com)
- [Jetpack Compose Docs](https://developer.android.com/jetpack/compose)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [Coroutines Guide](https://kotlinlang.org/docs/coroutines-overview.html)
- [MVVM Architecture](https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93viewmodel)

---

**Status:** ✅ Ready for Development | **Date:** February 2026
