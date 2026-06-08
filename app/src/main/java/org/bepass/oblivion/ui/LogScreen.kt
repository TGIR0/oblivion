package org.bepass.oblivion.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.delay
import org.bepass.oblivion.R
import org.bepass.oblivion.utils.ISPUtils

@Composable
fun LogScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  var logs by remember { mutableStateOf("") }

  LaunchedEffect(Unit) {
    while (true) {
      logs = File(context.filesDir, "logs.txt").takeIf { it.exists() }?.readText().orEmpty()
      delay(2_000L)
    }
  }

  Column(Modifier.fillMaxSize()) {
    ScreenHeader(title = stringResource(R.string.logApp), onBack = onBack)
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Button(onClick = { copyLogs(context, logs) }) { Text(stringResource(R.string.copytoclip)) }
      Text(
        text = logs.ifBlank { stringResource(R.string.start_logging_here) },
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
      )
    }
  }
}

private fun copyLogs(context: Context, logs: String) {
  ISPUtils.fetchISPInfo(
    object : ISPUtils.ISPCallback {
      override fun onISPInfoReceived(isp: String) {
        val last100 = logs.lines().takeLast(100).joinToString("\n")
        val clipText = "$last100\n=====\nISP: ${isp.ifBlank { "Unknown" }}\n"
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Log", clipText))
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
      }

      override fun onError(e: Exception) {
        Toast.makeText(context, "Error fetching ISP information.", Toast.LENGTH_SHORT).show()
      }
    }
  )
}
