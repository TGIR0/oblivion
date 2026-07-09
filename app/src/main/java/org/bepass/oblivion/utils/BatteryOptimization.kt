package org.bepass.oblivion.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import org.bepass.oblivion.R
import org.bepass.oblivion.logging.SecureLog as Log

private const val TAG = "BatteryOptimization"

/**
 * Checks if the app is subject to battery optimizations (Background restrictions).
 *
 * @return true if the app is optimized (restricted), false if it is whitelisted (unrestricted).
 */
fun isBatteryOptimizationEnabled(context: Context): Boolean {
  val powerManager = ContextCompat.getSystemService(context, PowerManager::class.java)
  // isIgnoringBatteryOptimizations returns TRUE if the app is on the whitelist.
  // We return TRUE if optimization is ENABLED (meaning NOT on the whitelist).
  return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false
}

/**
 * Requests the system to ignore battery optimizations for this app. Implements a fallback mechanism
 * for devices that block the direct request intent.
 */
@SuppressLint("BatteryLife")
fun requestIgnoreBatteryOptimizations(context: Context) {
  val packageName = context.packageName
  val powerManager = ContextCompat.getSystemService(context, PowerManager::class.java)
  if (powerManager == null || powerManager.isIgnoringBatteryOptimizations(packageName)) return

  val directIntent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
      data = "package:$packageName".toUri()
      addNewTaskFlagWhenNeeded(context)
    }
  if (!startBatterySettings(context, directIntent, "Direct battery optimization request failed")) {
    val fallbackIntent =
      Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        addNewTaskFlagWhenNeeded(context)
      }
    startBatterySettings(context, fallbackIntent, "Battery optimization settings not found")
  }
}

private fun Intent.addNewTaskFlagWhenNeeded(context: Context) {
  if (context !is Activity) flags = Intent.FLAG_ACTIVITY_NEW_TASK
}

private fun startBatterySettings(
  context: Context,
  intent: Intent,
  failureMessage: String,
): Boolean {
  return try {
    context.startActivity(intent)
    true
  } catch (notFound: ActivityNotFoundException) {
    Log.e(TAG, failureMessage, notFound)
    false
  } catch (securityFailure: SecurityException) {
    Log.e(TAG, failureMessage, securityFailure)
    false
  }
}

/**
 * Shows a Material Design dialog explaining why the user needs to disable battery optimization.
 * Handles activity lifecycle states to prevent window leaks.
 */
fun showBatteryOptimizationDialog(context: Context) {
  // Prevent dialog showing if context is a finishing activity (Memory Leak Protection)
  if (context is Activity && (context.isFinishing || context.isDestroyed)) {
    return
  }

  try {
    val dialog =
      AlertDialog.Builder(context)
        .setTitle(R.string.batteryOpL)
        .setMessage(R.string.dialBtText)
        .setPositiveButton(android.R.string.ok) { _, _ ->
          requestIgnoreBatteryOptimizations(context)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .setCancelable(true)
        .create()

    dialog.show()
  } catch (dialogFailure: RuntimeException) {
    Log.e(TAG, "Error showing battery optimization dialog", dialogFailure)
  }
}
