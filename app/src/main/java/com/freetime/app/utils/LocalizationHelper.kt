package com.freetime.app.utils

/**
 * Localization Helper - Manages language settings and translations
 * Supported languages: English, Spanish, French, German
 */
object LocalizationHelper {
    
    // Supported languages
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_SPANISH = "es"
    const val LANGUAGE_FRENCH = "fr"
    const val LANGUAGE_GERMAN = "de"
    
    /**
     * Get language name from language code
     */
    fun getLanguageName(languageCode: String): String = when (languageCode) {
        LANGUAGE_SPANISH -> "Español"
        LANGUAGE_FRENCH -> "Français"
        LANGUAGE_GERMAN -> "Deutsch"
        else -> "English"
    }
    
    /**
     * Get language code from index
     */
    fun getLanguageCodeFromIndex(index: Int): String = when (index) {
        1 -> LANGUAGE_SPANISH
        2 -> LANGUAGE_FRENCH
        3 -> LANGUAGE_GERMAN
        else -> LANGUAGE_ENGLISH
    }
    
    /**
     * Get index from language code
     */
    fun getIndexFromLanguageCode(code: String): Int = when (code) {
        LANGUAGE_SPANISH -> 1
        LANGUAGE_FRENCH -> 2
        LANGUAGE_GERMAN -> 3
        else -> 0
    }
    
    /**
     * Translate common UI strings
     * Future: This can be expanded with a resource file system or remote translations
     */
    fun translate(key: String, languageCode: String): String {
        return when (languageCode) {
            LANGUAGE_SPANISH -> translateSpanish(key)
            LANGUAGE_FRENCH -> translateFrench(key)
            LANGUAGE_GERMAN -> translateGerman(key)
            else -> translateEnglish(key)
        }
    }
    
    private fun translateEnglish(key: String): String = when (key) {
        "messages" -> "Messages"
        "conversations" -> "conversations"
        "settings" -> "Settings"
        "add_friend" -> "Add Friend"
        "friend_requests" -> "Friend Requests"
        "pending_requests" -> "Pending Requests"
        "accept" -> "Accept"
        "decline" -> "Decline"
        "search_friends" -> "Search Friends"
        "profile" -> "Profile"
        "logout" -> "Logout"
        "language" -> "Language"
        "accent_color" -> "Accent Color"
        "font_size" -> "Font Size"
        "animation_speed" -> "Animation Speed"
        "dark_mode" -> "Dark Mode"
        else -> key
    }
    
    private fun translateSpanish(key: String): String = when (key) {
        "messages" -> "Mensajes"
        "conversations" -> "conversaciones"
        "settings" -> "Configuración"
        "add_friend" -> "Añadir Amigo"
        "friend_requests" -> "Solicitudes de Amistad"
        "pending_requests" -> "Solicitudes Pendientes"
        "accept" -> "Aceptar"
        "decline" -> "Rechazar"
        "search_friends" -> "Buscar Amigos"
        "profile" -> "Perfil"
        "logout" -> "Cerrar Sesión"
        "language" -> "Idioma"
        "accent_color" -> "Color de Acento"
        "font_size" -> "Tamaño de Fuente"
        "animation_speed" -> "Velocidad de Animación"
        "dark_mode" -> "Modo Oscuro"
        else -> key
    }
    
    private fun translateFrench(key: String): String = when (key) {
        "messages" -> "Messages"
        "conversations" -> "conversations"
        "settings" -> "Réglages"
        "add_friend" -> "Ajouter un Ami"
        "friend_requests" -> "Demandes d'Amis"
        "pending_requests" -> "Demandes en Attente"
        "accept" -> "Accepter"
        "decline" -> "Refuser"
        "search_friends" -> "Rechercher des Amis"
        "profile" -> "Profil"
        "logout" -> "Déconnexion"
        "language" -> "Langue"
        "accent_color" -> "Couleur d'Accentuation"
        "font_size" -> "Taille de Police"
        "animation_speed" -> "Vitesse d'Animation"
        "dark_mode" -> "Mode Sombre"
        else -> key
    }
    
    private fun translateGerman(key: String): String = when (key) {
        "messages" -> "Nachrichten"
        "conversations" -> "Unterhaltungen"
        "settings" -> "Einstellungen"
        "add_friend" -> "Freund Hinzufügen"
        "friend_requests" -> "Freundschaftsanfragen"
        "pending_requests" -> "Ausstehende Anfragen"
        "accept" -> "Akzeptieren"
        "decline" -> "Ablehnen"
        "search_friends" -> "Freunde Suchen"
        "profile" -> "Profil"
        "logout" -> "Abmelden"
        "language" -> "Sprache"
        "accent_color" -> "Akzentfarbe"
        "font_size" -> "Schriftgröße"
        "animation_speed" -> "Animationsgeschwindigkeit"
        "dark_mode" -> "Dunkler Modus"
        else -> key
    }
}
