package com.itantra.app.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Permissions : Screen("permissions")
    object Home : Screen("home")
    object Conversation : Screen("conversation")
    object LanguagePacks : Screen("language_packs")
    object Diagnostics : Screen("diagnostics")
    object Settings : Screen("settings")
}
