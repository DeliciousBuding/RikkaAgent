package io.rikka.agent.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.rikka.agent.ui.screen.ChatScreen
import io.rikka.agent.ui.screen.ProfileEditorScreen
import io.rikka.agent.ui.screen.ProfilesScreen
import io.rikka.agent.ui.screen.KnownHostsScreen
import io.rikka.agent.ui.screen.SettingsScreen

@Composable
fun AppNavHost() {
  val navController = rememberNavController()

  NavHost(
    navController = navController,
    startDestination = Screen.Profiles,
    enterTransition = { slideInHorizontally { it } + fadeIn() },
    exitTransition = { slideOutHorizontally { -it / 3 } + fadeOut() },
    popEnterTransition = { slideInHorizontally { -it / 3 } + fadeIn() },
    popExitTransition = { slideOutHorizontally { it } + fadeOut() },
  ) {
    composable<Screen.Profiles> {
      ProfilesScreen(
        onOpenSession = { profileId ->
          navController.navigate(Screen.Session(profileId = profileId))
        },
        onEditProfile = { profileId ->
          navController.navigate(Screen.ProfileEditor(profileId = profileId))
        },
        onOpenSettings = {
          navController.navigate(Screen.Settings)
        },
      )
    }

    composable<Screen.ProfileEditor> { backStackEntry ->
      val route = backStackEntry.toRoute<Screen.ProfileEditor>()
      ProfileEditorScreen(
        profileId = route.profileId,
        onBack = { navController.popBackStack() },
        onSaved = { navController.popBackStack() },
      )
    }

    composable<Screen.Session> { backStackEntry ->
      val route = backStackEntry.toRoute<Screen.Session>()
      ChatScreen(
        profileId = route.profileId,
        onBack = { navController.popBackStack() },
      )
    }

    composable<Screen.Settings> {
      SettingsScreen(
        onBack = { navController.popBackStack() },
        onOpenKnownHosts = { navController.navigate(Screen.KnownHosts) },
      )
    }

    composable<Screen.KnownHosts> {
      KnownHostsScreen(
        onBack = { navController.popBackStack() },
      )
    }
  }
}
