# 🎨 SecureChat UI/UX Design System

**Last Updated:** February 2026 | **Theme:** Dark Cyberpunk | **Status:** ✅ Production Ready

---

## 🌗 Dark Theme Specification

### Color Palette

#### Primary Colors
```kotlin
// Dark backgrounds
val DarkBg1 = Color(0xFF121212)    // Darkest (OLED black)
val DarkBg2 = Color(0xFF1E1E1E)    // Cards background
val DarkBg3 = Color(0xFF2C2C2C)    // Subtle elevation

// Neon accents (Cyberpunk)
val NeonPurple = Color(0xFFBB00FF)  // Primary accent
val NeonBlue = Color(0xFF00D4FF)    // Secondary accent
val NeonPink = Color(0xFFFF1493)    // Tertiary accent
val NeonGreen = Color(0xFF00FF41)   // Success state
val NeonRed = Color(0xFFFF0000)     // Error state

// Text colors
val TextPrimary = Color(0xFFFFFFFF)    // White
val TextSecondary = Color(0xFFB0B0B0)  // Gray
val TextTertiary = Color(0xFF808080)   // Dark gray

// UI Elements
val SurfaceVariant = Color(0xFF2A2A2E)
val OnSurfaceVariant = Color(0xFFCAC4D0)
```

### Color Usage Guidelines

```
Background:      DarkBg1 (primary), DarkBg2 (cards)
Text:            TextPrimary (main), TextSecondary (secondary)
Accents:         NeonPurple (primary), NeonBlue (secondary)
Success:         NeonGreen
Error:           NeonRed
Warning:         NeonPink
Disabled:        TextTertiary
```

---

## 🎬 Animation System

### Smooth Transitions

```kotlin
// All animations use 200-400ms duration
const val ANIMATION_DURATION_FAST = 200      // Quick actions
const val ANIMATION_DURATION_NORMAL = 300    // Standard
const val ANIMATION_DURATION_SLOW = 400      // Complex animations

// Easing functions
val EaseInOutCubic = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
```

### Message Slide-In Animation

```kotlin
@Composable
fun MessageBubble(message: Message) {
    val offsetX by animateDpAsState(
        targetValue = 0.dp,
        animationSpec = tween(ANIMATION_DURATION_NORMAL)
    )
    
    Box(
        modifier = Modifier
            .offset(x = offsetX)
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        // Message content
    }
}
```

### Fade-In Effects

```kotlin
@Composable
fun ChatListItem(chat: Chat) {
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(ANIMATION_DURATION_NORMAL)
    )
    
    Surface(
        modifier = Modifier.alpha(alpha),
        color = DarkBg2
    ) {
        // Chat item content
    }
}
```

### No Flashing or Excessive Effects

```kotlin
// ❌ AVOID - Jarring animations
fadeInOut = when (state) {
    LOADING -> repeat(Infinite) { 1f -> 0f -> 1f }  // Flashing
    READY -> 1f
}

// ✅ DO - Smooth subtle effects
alpha = animateFloatAsState(0f, 1f)  // Smooth fade
scaleX = animateFloatAsState(0.95f, 1f)  // Subtle scale
```

---

## 🎯 Component Library

### Message Bubble

```kotlin
@Composable
fun MessageBubble(
    message: Message,
    isSent: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .widthIn(max = 300.dp),
            color = if (isSent) NeonPurple.copy(alpha = 0.3f) else DarkBg2,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.text,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = formatTime(message.timestamp),
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
```

### Action Button

```kotlin
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    isDangerous: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isDangerous) NeonRed else NeonPurple
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
        } else {
            Text(
                text = text,
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
```

### Chat List Item

```kotlin
@Composable
fun ChatListItem(
    chat: Chat,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        color = DarkBg2,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = NeonPurple
            ) {
                // Avatar image
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = chat.recipientName,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = chat.lastMessage,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = formatTime(chat.timestamp),
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
```

### Input Field

```kotlin
@Composable
fun SecureTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        label = { Text(label, color = TextSecondary) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = DarkBg2,
            unfocusedContainerColor = DarkBg3,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextSecondary,
            focusedIndicatorColor = NeonPurple,
            unfocusedIndicatorColor = Color.Transparent
        ),
        shape = RoundedCornerShape(8.dp),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true
    )
}
```

---

## 🎭 Screen Templates

### Chat Screen Layout

```kotlin
@Composable
fun ChatScreen(
    chatId: String,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg1)
    ) {
        // Header
        ChatHeader(
            title = "Chat Title",
            onBackClick = { /* Navigate back */ }
        )
        
        Divider(color = DarkBg2)
        
        // Messages List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = true
        ) {
            items(messages) { message ->
                MessageBubble(message)
            }
        }
        
        Divider(color = DarkBg2)
        
        // Input Area
        ChatInputArea(
            onSendClick = { text ->
                viewModel.sendMessage(text)
            }
        )
    }
}
```

### Chat Input Area

```kotlin
@Composable
fun ChatInputArea(
    onSendClick: (String) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBg2)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Input field
        TextField(
            value = messageText,
            onValueChange = { messageText = it },
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            placeholder = { Text("Type message...", color = TextSecondary) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = DarkBg1,
                unfocusedContainerColor = DarkBg1
            ),
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Send button
        IconButton(
            onClick = {
                if (messageText.isNotBlank()) {
                    onSendClick(messageText)
                    messageText = ""
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Send",
                tint = NeonPurple,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
```

---

## 🌈 Typography

### Font Specifications

```kotlin
val Typography = Typography(
    displayLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    ),
    displayMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = TextPrimary
    ),
    labelSmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = TextSecondary
    )
)
```

---

## 📐 Spacing & Layout

### Spacing Guidelines

```kotlin
// Standard spacing values
const val SPACING_EXTRA_SMALL = 2.dp
const val SPACING_SMALL = 4.dp
const val SPACING_NORMAL = 8.dp
const val SPACING_MEDIUM = 12.dp
const val SPACING_LARGE = 16.dp
const val SPACING_EXTRA_LARGE = 24.dp

// Usage
Spacer(modifier = Modifier.height(SPACING_NORMAL))
Box(modifier = Modifier.padding(SPACING_LARGE))
```

### Elevation & Shadows

```kotlin
// Card elevation (subtle, not harsh)
Surface(
    modifier = Modifier
        .fillMaxWidth()
        .shadow(
            elevation = 4.dp,
            shape = RoundedCornerShape(8.dp),
            spotColor = NeonPurple.copy(alpha = 0.1f)  // Subtle glow
        ),
    color = DarkBg2
) { }
```

---

## ♿ Accessibility

### Text Contrast

```
✅ Good contrast (WCAG AA standard):
   - White text on dark background: 4.5:1+
   - NeonPurple text on dark: Good
   - Gray text: 3:1+ minimum

❌ Poor contrast:
   - Dark gray on dark background
   - Low saturation colors
```

### Touch Targets

```kotlin
// Minimum 48dp for touch targets (Material Design)
IconButton(
    modifier = Modifier
        .size(48.dp)  // Proper size for touch
)

// Don't make targets too small
Button(
    modifier = Modifier
        .height(48.dp)  // Good
        .fillMaxWidth()  // Accessible width
)
```

### Content Descriptions

```kotlin
Icon(
    imageVector = Icons.Default.Send,
    contentDescription = "Send message",  // Important!
    tint = NeonPurple
)

Image(
    painter = painterResource(id = R.drawable.ic_avatar),
    contentDescription = "User avatar for @username"
)
```

---

## 🎯 Responsive Design

### Device Breakpoints

```kotlin
@Composable
fun AdaptiveLayout() {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    
    when {
        screenWidth < 600 -> {
            // Mobile layout
            MobileLayout()
        }
        screenWidth < 900 -> {
            // Tablet layout
            TabletLayout()
        }
        else -> {
            // Desktop layout
            DesktopLayout()
        }
    }
}
```

---

## 🎬 Dark Mode Implementation

### Theme Configuration

```kotlin
// Theme.kt
@Composable
fun SecureChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = NeonPurple,
            secondary = NeonBlue,
            tertiary = NeonPink,
            background = DarkBg1,
            surface = DarkBg2,
            error = NeonRed
        )
    } else {
        lightColorScheme(/* ... */)  // Light theme (future)
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
```

### Apply Theme

```kotlin
// MainActivity.kt
@Composable
fun SecureChatApp() {
    SecureChatTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Navigation()
        }
    }
}
```

---

## ✅ UI/UX Checklist

- ✅ Dark theme throughout (no bright colors)
- ✅ Smooth animations (no flashing)
- ✅ Responsive design (works on all devices)
- ✅ Accessible (proper contrast, touch targets)
- ✅ Cyberpunk aesthetic (neon accents)
- ✅ 60fps performance (smooth scrolling)
- ✅ Clear navigation (intuitive flow)
- ✅ Error states visible (NeonRed)
- ✅ Loading states animated (no flashing)
- ✅ Consistent spacing & alignment

---

**Status:** ✅ Design System Complete | **Date:** February 2026
