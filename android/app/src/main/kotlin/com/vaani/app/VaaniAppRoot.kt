package com.vaani.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vaani.core.designsystem.component.VaaniBottomNav
import com.vaani.core.designsystem.component.VaaniNavSlot
import com.vaani.core.designsystem.theme.VaaniTheme
import com.vaani.feature.chat.ChatScreen
import com.vaani.feature.device.DeviceScreen
import com.vaani.feature.library.LibraryScreen
import com.vaani.feature.note.NOTE_ID_ARG
import com.vaani.feature.note.NoteDetailScreen
import com.vaani.feature.onboarding.OnboardingScreen
import com.vaani.feature.search.SearchScreen
import com.vaani.feature.settings.SettingsScreen
import com.vaani.feature.tasks.TasksScreen

/** Route constants for the single-activity nav graph. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val TASKS = "tasks"
    const val SETTINGS = "settings"
    const val DEVICE = "device"
    const val CHAT = "chat"
    const val NOTE = "note/{$NOTE_ID_ARG}"
    fun note(id: String) = "note/$id"
}

/** Top-level (tab) destinations that show the bottom nav. */
private val TAB_ROUTES = setOf(Routes.LIBRARY, Routes.SEARCH, Routes.TASKS, Routes.SETTINGS)

@Composable
fun VaaniAppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomNav = currentRoute in TAB_ROUTES

    Scaffold(
        containerColor = VaaniTheme.colors.background,
        bottomBar = {
            if (showBottomNav) {
                VaaniBottomNav(
                    selected = currentRoute.toSlot(),
                    onSelect = { slot -> navController.switchTab(slot.toRoute()) },
                    onRec = { /* REC capture flow lands with the recording milestone */ },
                )
            }
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding(),
                ),
        ) {
            VaaniNavHost(
                startDestination = Routes.ONBOARDING,
                navController = navController,
                builder = {
                    composable(Routes.ONBOARDING) {
                        OnboardingScreen(
                            onPair = { navController.switchTab(Routes.LIBRARY) },
                            onSkip = { navController.switchTab(Routes.LIBRARY) },
                        )
                    }
                    composable(Routes.LIBRARY) {
                        LibraryScreen(
                            onNoteClick = { id -> navController.navigate(Routes.note(id)) },
                            onDeviceClick = { navController.navigate(Routes.DEVICE) },
                            onChatClick = { navController.navigate(Routes.CHAT) },
                        )
                    }
                    composable(Routes.SEARCH) { SearchScreen() }
                    composable(Routes.TASKS) { TasksScreen() }
                    composable(Routes.SETTINGS) { SettingsScreen() }
                    composable(Routes.DEVICE) {
                        DeviceScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.CHAT) {
                        ChatScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        Routes.NOTE,
                        arguments = listOf(navArgument(NOTE_ID_ARG) { type = NavType.StringType }),
                    ) {
                        NoteDetailScreen(onBack = { navController.popBackStack() })
                    }
                },
            )
        }
    }
}

/** Switch between top-level tabs: single-top, pop back to the start tab, restore state. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(Routes.LIBRARY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun String?.toSlot(): VaaniNavSlot = when (this) {
    Routes.SEARCH -> VaaniNavSlot.Search
    Routes.TASKS -> VaaniNavSlot.Tasks
    Routes.SETTINGS -> VaaniNavSlot.Settings
    else -> VaaniNavSlot.Library
}

private fun VaaniNavSlot.toRoute(): String = when (this) {
    VaaniNavSlot.Library -> Routes.LIBRARY
    VaaniNavSlot.Search -> Routes.SEARCH
    VaaniNavSlot.Tasks -> Routes.TASKS
    VaaniNavSlot.Settings -> Routes.SETTINGS
    VaaniNavSlot.Rec -> Routes.LIBRARY
}

@Composable
private fun VaaniNavHost(
    startDestination: String,
    navController: NavHostController,
    builder: NavGraphBuilder.() -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        builder = builder,
    )
}
