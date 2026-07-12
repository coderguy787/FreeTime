package com.freetime.app.ui.utils

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    ITALIAN("it", "Italiano"),
    CHINESE("zh", "中文"),
    RUSSIAN("ru", "Русский"),
    GERMAN("de", "Deutsch")
}

object LocalizationManager {
    private val currentLanguage = mutableStateOf(AppLanguage.ENGLISH)
    
    fun setLanguage(context: Context, language: AppLanguage) {
        currentLanguage.value = language
        val prefs = context.getSharedPreferences("freetime_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_language", language.code).apply()
        
        // Update system locale
        val locale = Locale(language.code)
        Locale.setDefault(locale)
        val config = Configuration()
        config.locale = locale
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
    
    fun getLanguage(context: Context): AppLanguage {
        val prefs = context.getSharedPreferences("freetime_prefs", Context.MODE_PRIVATE)
        val code = prefs.getString("app_language", "en") ?: "en"
        return AppLanguage.values().find { it.code == code } ?: AppLanguage.ENGLISH
    }
    
    fun getCurrentLanguage(): AppLanguage = currentLanguage.value
    
    fun loadLanguage(context: Context) {
        val saved = getLanguage(context)
        currentLanguage.value = saved
    }
    
    fun getAvailableLanguages(): List<AppLanguage> = AppLanguage.values().toList()
}

// Extension function to get localized strings
fun Context.getString(key: String): String {
    val language = LocalizationManager.getLanguage(this)
    return when (language) {
        AppLanguage.ENGLISH -> getStringEN(key)
        AppLanguage.ITALIAN -> getStringIT(key)
        AppLanguage.CHINESE -> getStringZH(key)
        AppLanguage.RUSSIAN -> getStringRU(key)
        AppLanguage.GERMAN -> getStringDE(key)
    }
}

// English strings
private fun getStringEN(key: String): String = when (key) {
    // Navigation & General
    "app_name" -> "FreeTime"
    "back" -> "Back"
    "settings" -> "Settings"
    "cancel" -> "Cancel"
    "save" -> "Save"
    "ok" -> "OK"
    "error" -> "Error"
    "loading" -> "Loading..."
    
    // Friend Requests
    "friend_requests" -> "Friend Requests"
    "friend_request_notifications" -> "Friend Request Notifications"
    "enable_notifications" -> "Enable Notifications"
    "no_pending_requests" -> "No pending requests"
    "when_friends_send_requests" -> "When friends send you requests, they'll appear here"
    "accept" -> "Accept"
    "decline" -> "Decline"
    "send_friend_request" -> "Send Friend Request"
    "request_sent" -> "Request sent"
    "notification_settings" -> "Notification Settings"
    
    // Settings Tabs
    "personalize" -> "Personalize"
    "account" -> "Account"
    "notifications" -> "Notifications"
    "security" -> "Security"
    "about" -> "About"
    "share" -> "Share"
    "language" -> "Language"
    
    // Notifications
    "message_notifications" -> "Message Notifications"
    "notification_sound" -> "Notification Sound"
    "play_sound_for_notifications" -> "Play sound for notifications"
    "notifications_enabled" -> "Notifications Enabled"
    "notifications_disabled" -> "Notifications Disabled"
    
    // Other
    "logout" -> "Logout"
    "dark_mode" -> "Dark Mode"
    else -> key
}

// Italian strings
private fun getStringIT(key: String): String = when (key) {
    "app_name" -> "FreeTime"
    "back" -> "Indietro"
    "settings" -> "Impostazioni"
    "cancel" -> "Annulla"
    "save" -> "Salva"
    "ok" -> "OK"
    "error" -> "Errore"
    "loading" -> "Caricamento..."
    
    "friend_requests" -> "Richieste di Amicizia"
    "friend_request_notifications" -> "Notifiche Richieste di Amicizia"
    "enable_notifications" -> "Abilita Notifiche"
    "no_pending_requests" -> "Nessuna richiesta in sospeso"
    "when_friends_send_requests" -> "Quando i tuoi amici ti inviano richieste, appariranno qui"
    "accept" -> "Accetta"
    "decline" -> "Rifiuta"
    "send_friend_request" -> "Invia Richiesta di Amicizia"
    "request_sent" -> "Richiesta inviata"
    "notification_settings" -> "Impostazioni Notifiche"
    
    "personalize" -> "Personalizza"
    "account" -> "Account"
    "notifications" -> "Notifiche"
    "security" -> "Sicurezza"
    "about" -> "Informazioni"
    "share" -> "Condividi"
    "language" -> "Lingua"
    
    "message_notifications" -> "Notifiche Messaggi"
    "notification_sound" -> "Suono Notifica"
    "play_sound_for_notifications" -> "Riproduci suono per le notifiche"
    "notifications_enabled" -> "Notifiche Abilitate"
    "notifications_disabled" -> "Notifiche Disabilitate"
    
    "logout" -> "Esci"
    "dark_mode" -> "Modalità Scura"
    else -> key
}

// Chinese strings
private fun getStringZH(key: String): String = when (key) {
    "app_name" -> "FreeTime"
    "back" -> "返回"
    "settings" -> "设置"
    "cancel" -> "取消"
    "save" -> "保存"
    "ok" -> "确定"
    "error" -> "错误"
    "loading" -> "加载中..."
    
    "friend_requests" -> "好友请求"
    "friend_request_notifications" -> "好友请求通知"
    "enable_notifications" -> "启用通知"
    "no_pending_requests" -> "没有待处理的请求"
    "when_friends_send_requests" -> "当您的朋友向您发送请求时，它们会出现在这里"
    "accept" -> "接受"
    "decline" -> "拒绝"
    "send_friend_request" -> "发送好友请求"
    "request_sent" -> "请求已发送"
    "notification_settings" -> "通知设置"
    
    "personalize" -> "个性化"
    "account" -> "账户"
    "notifications" -> "通知"
    "security" -> "安全"
    "about" -> "关于"
    "share" -> "分享"
    "language" -> "语言"
    
    "message_notifications" -> "消息通知"
    "notification_sound" -> "通知声音"
    "play_sound_for_notifications" -> "通知时播放声音"
    "notifications_enabled" -> "已启用通知"
    "notifications_disabled" -> "已禁用通知"
    
    "logout" -> "登出"
    "dark_mode" -> "深色模式"
    else -> key
}

// Russian strings
private fun getStringRU(key: String): String = when (key) {
    "app_name" -> "FreeTime"
    "back" -> "Назад"
    "settings" -> "Настройки"
    "cancel" -> "Отмена"
    "save" -> "Сохранить"
    "ok" -> "OK"
    "error" -> "Ошибка"
    "loading" -> "Загрузка..."
    
    "friend_requests" -> "Запросы в друзья"
    "friend_request_notifications" -> "Уведомления о запросах в друзья"
    "enable_notifications" -> "Включить уведомления"
    "no_pending_requests" -> "Нет ожидающих запросов"
    "when_friends_send_requests" -> "Когда ваши друзья отправляют вам запросы, они появятся здесь"
    "accept" -> "Принять"
    "decline" -> "Отклонить"
    "send_friend_request" -> "Отправить запрос в друзья"
    "request_sent" -> "Запрос отправлен"
    "notification_settings" -> "Параметры уведомлений"
    
    "personalize" -> "Персонализация"
    "account" -> "Аккаунт"
    "notifications" -> "Уведомления"
    "security" -> "Безопасность"
    "about" -> "О приложении"
    "share" -> "Поделиться"
    "language" -> "Язык"
    
    "message_notifications" -> "Уведомления о сообщениях"
    "notification_sound" -> "Звук уведомления"
    "play_sound_for_notifications" -> "Воспроизводить звук для уведомлений"
    "notifications_enabled" -> "Уведомления включены"
    "notifications_disabled" -> "Уведомления отключены"
    
    "logout" -> "Выход"
    "dark_mode" -> "Тёмный режим"
    else -> key
}

// German strings
private fun getStringDE(key: String): String = when (key) {
    "app_name" -> "FreeTime"
    "back" -> "Zurück"
    "settings" -> "Einstellungen"
    "cancel" -> "Abbrechen"
    "save" -> "Speichern"
    "ok" -> "OK"
    "error" -> "Fehler"
    "loading" -> "Wird geladen..."
    
    "friend_requests" -> "Freundschaftsanfragen"
    "friend_request_notifications" -> "Benachrichtigungen zu Freundschaftsanfragen"
    "enable_notifications" -> "Benachrichtigungen aktivieren"
    "no_pending_requests" -> "Keine ausstehenden Anfragen"
    "when_friends_send_requests" -> "Wenn deine Freunde dir Anfragen senden, erscheinen sie hier"
    "accept" -> "Akzeptieren"
    "decline" -> "Ablehnen"
    "send_friend_request" -> "Freundschaftsanfrage senden"
    "request_sent" -> "Anfrage gesendet"
    "notification_settings" -> "Benachrichtigungseinstellungen"
    
    "personalize" -> "Personalisieren"
    "account" -> "Konto"
    "notifications" -> "Benachrichtigungen"
    "security" -> "Sicherheit"
    "about" -> "Über"
    "share" -> "Teilen"
    "language" -> "Sprache"
    
    "message_notifications" -> "Nachrichtenbenachrichtigungen"
    "notification_sound" -> "Benachrichtigungston"
    "play_sound_for_notifications" -> "Ton für Benachrichtigungen abspielen"
    "notifications_enabled" -> "Benachrichtigungen aktiviert"
    "notifications_disabled" -> "Benachrichtigungen deaktiviert"
    
    "logout" -> "Abmelden"
    "dark_mode" -> "Dunkler Modus"
    else -> key
}
