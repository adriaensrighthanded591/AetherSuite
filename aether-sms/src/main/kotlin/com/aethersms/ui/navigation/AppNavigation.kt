package com.aethersms.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aethersms.ui.screens.*
import com.aethersms.viewmodel.*

sealed class Screen(val route: String) {
    object ConversationList : Screen("conversations")
    object NewConversation  : Screen("new_conversation")
    object Conversation     : Screen("conversation/{threadId}/{address}") {
        fun build(threadId: Long, address: String) =
            "conversation/$threadId/${android.net.Uri.encode(address)}"
    }
    object Settings : Screen("settings")
}

@Composable
fun AetherNavigation() {
    val navController = rememberNavController()
    val app     = LocalContext.current.applicationContext as Application
    val factory = AetherViewModelFactory(app)

    NavHost(navController = navController, startDestination = Screen.ConversationList.route) {

        composable(Screen.ConversationList.route) {
            val vm: ConversationListViewModel = viewModel(factory = factory)
            ConversationListScreen(
                viewModel          = vm,
                onOpenConversation = { threadId, address ->
                    navController.navigate(Screen.Conversation.build(threadId, address))
                },
                onNewConversation  = { navController.navigate(Screen.NewConversation.route) },
                onSettings         = { navController.navigate(Screen.Settings.route) },
            )
        }

        composable(Screen.NewConversation.route) {
            val vm: ContactSearchViewModel = viewModel(factory = factory)
            NewConversationScreen(
                viewModel  = vm,
                onNavigate = { address ->
                    navController.navigate(Screen.Conversation.build(-1L, address))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.Conversation.route,
            arguments = listOf(
                navArgument("threadId") { type = NavType.LongType },
                navArgument("address")  { type = NavType.StringType },
            ),
        ) { back ->
            val threadId = back.arguments?.getLong("threadId") ?: -1L
            val address  = back.arguments?.getString("address")
                ?.let { android.net.Uri.decode(it) } ?: ""

            // Une instance de VM par conversation (clé stable)
            val vm: ConversationViewModel = viewModel(
                key     = "conv_${threadId}_$address",
                factory = factory,
            )

            // ✅ LaunchedEffect : init déclenché une seule fois, stable et testable
            LaunchedEffect(threadId, address) {
                vm.init(threadId, address)
            }

            ConversationScreen(
                viewModel = vm,
                address   = address,
                onBack    = { navController.popBackStack() },
            )
        }

        composable(Screen.Settings.route) {
            val vm: SettingsViewModel = viewModel(factory = factory)
            SettingsScreen(onBack = { navController.popBackStack() }, vm = vm)
        }
    }
}
