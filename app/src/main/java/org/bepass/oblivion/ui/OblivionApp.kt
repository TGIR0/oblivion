package org.bepass.oblivion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay

@Composable
fun OblivionApp(
  onRequestVpnStart: () -> Unit,
  onStopVpn: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val navController = rememberNavController()

  NavHost(
    navController = navController,
    startDestination = AppRoute.SPLASH.value,
    modifier = modifier,
  ) {
    composable(AppRoute.SPLASH.value) {
      var leavingSplash by remember { mutableStateOf(false) }
      fun goHome() {
        if (leavingSplash) return
        leavingSplash = true
        navController.navigate(AppRoute.HOME.value) {
          popUpTo(AppRoute.SPLASH.value) { inclusive = true }
        }
      }

      LaunchedEffect(Unit) {
        delay(3_000L)
        goHome()
      }
      SplashScreen(onContinue = ::goHome)
    }
    composable(AppRoute.HOME.value) {
      HomeScreen(
        onRequestVpnStart = onRequestVpnStart,
        onStopVpn = onStopVpn,
        onSettings = { navController.navigate(AppRoute.SETTINGS.value) },
        onInfo = { navController.navigate(AppRoute.INFO.value) },
        onLogs = { navController.navigate(AppRoute.LOGS.value) },
      )
    }
    composable(AppRoute.SETTINGS.value) {
      SettingsScreen(
        onBack = { navController.popBackStack() },
        onSplitTunnel = { navController.navigate(AppRoute.SPLIT_TUNNEL.value) },
      )
    }
    composable(AppRoute.INFO.value) {
      InfoScreen(onBack = { navController.popBackStack() })
    }
    composable(AppRoute.LOGS.value) {
      LogScreen(onBack = { navController.popBackStack() })
    }
    composable(AppRoute.SPLIT_TUNNEL.value) {
      SplitTunnelScreen(onBack = { navController.popBackStack() })
    }
  }
}
