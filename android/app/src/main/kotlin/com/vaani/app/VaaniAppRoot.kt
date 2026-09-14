package com.vaani.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vaani.core.designsystem.component.VaaniBottomNav
import com.vaani.core.designsystem.component.VaaniNavSlot
import com.vaani.core.designsystem.theme.VaaniTheme
import com.vaani.feature.library.LibraryScreen
import com.vaani.feature.note.NOTE_ID_ARG
import com.vaani.feature.note.NoteDetailScreen

/** Route constants for the single-activity nav graph. */
object Routes {
    const val LIBRARY = "library"
    const val NOTE = "note/{$NOTE_ID_ARG}"
    fun note(id: String) = "note/$id"
}

@Composable
fun VaaniAppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Bottom nav is shown on top-level destinations only.
    val showBottomNav = currentRoute == Routes.LIBRARY

    Scaffold(
        containerColor = VaaniTheme.colors.background,
        bottomBar = {
            if (showBottomNav) {
                VaaniBottomNav(
                    selected = VaaniNavSlot.Library,
                    onSelect = { /* other tabs land in later milestones */ },
                    onRec = { /* REC action lands with the capture flow */ },
                )
            }
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        Box(
            Modifier.padding(
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                bottom = if (showBottomNav) innerPadding.calculateBottomPadding() else 0.dp,
            ),
        ) {
            VaaniNavHost(
                startDestination = Routes.LIBRARY,
                builder = {
                    composable(Routes.LIBRARY) {
                        LibraryScreen(
                            onNoteClick = { id -> navController.navigate(Routes.note(id)) },
                        )
                    }
                    composable(Routes.NOTE) {
                        NoteDetailScreen(onBack = { navController.popBackStack() })
                    }
                },
                navController = navController,
            )
        }
    }
}

@Composable
private fun VaaniNavHost(
    startDestination: String,
    navController: androidx.navigation.NavHostController,
    builder: NavGraphBuilder.() -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        builder = builder,
    )
}
