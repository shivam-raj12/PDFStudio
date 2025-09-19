package com.shivam_raj.pdfstudio.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shivam_raj.pdfstudio.ui.editorscreen.EditorScreen
import com.shivam_raj.pdfstudio.ui.homescreen.HomeScreen

object AppDestinations {
    const val HOME_ROUTE = "home"
    const val EDITOR_ROUTE = "editor"
    const val WORKSPACE_ID_ARG = "workspaceId"
    const val EDITOR_ROUTE_WITH_ARG = "$EDITOR_ROUTE/{$WORKSPACE_ID_ARG}"
    const val EDITOR_ROUTE_NEW = "$EDITOR_ROUTE/new" // For creating a new workspace implicitly
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = AppDestinations.HOME_ROUTE) {
        composable(AppDestinations.HOME_ROUTE) {
            HomeScreen(
                onNavigateToEditor = { workspaceId ->
                    navController.navigate("${AppDestinations.EDITOR_ROUTE}/$workspaceId")
                },
                onNavigateToNewWorkspaceEditor = {
                    navController.navigate(AppDestinations.EDITOR_ROUTE_NEW)
                }
            )
        }
        composable(
            route = AppDestinations.EDITOR_ROUTE_WITH_ARG,
            arguments = listOf(navArgument(AppDestinations.WORKSPACE_ID_ARG) { type = NavType.StringType })
        ) { backStackEntry ->
            val workspaceId = backStackEntry.arguments?.getString(AppDestinations.WORKSPACE_ID_ARG)
            // If workspaceId is null here, it's an issue, but NavType.StringType should ensure it's present.
            // For robustness, you might handle a null case, though navigation setup should prevent it.
            EditorScreen(
                workspaceId = workspaceId ?: "", // Fallback, though ideally this route guarantees it.
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = AppDestinations.EDITOR_ROUTE_NEW
        ) {
            // When navigating to "new", the EditorViewModel will handle creating a new workspace ID
            EditorScreen(
                workspaceId = null, // Signal to EditorViewModel to create a new one
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
