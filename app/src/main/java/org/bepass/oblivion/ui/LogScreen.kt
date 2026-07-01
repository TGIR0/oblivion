package org.bepass.oblivion.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.bepass.oblivion.R
import org.bepass.oblivion.ui.theme.OblivionV7Tokens

@Composable
fun LogScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  var logs by remember { mutableStateOf("") }
  val fontScale =
    org.bepass.oblivion.utils.FontSizeHelper.fontSizeFlow.collectAsStateWithLifecycle().value.scale

  LaunchedEffect(Unit) {
    while (true) {
      logs =
        withContext(Dispatchers.IO) {
          File(context.filesDir, "logs.txt").takeIf { it.exists() }?.readText().orEmpty()
        }
      delay(2_000L)
    }
  }

  Column(Modifier.fillMaxSize().testTag(UiTestTags.LOGS)) {
    ScreenHeader(title = stringResource(R.string.logApp), onBack = onBack)

    // Log ScrollView
    Text(
      text = logs.ifBlank { stringResource(R.string.start_logging_here) },
      modifier =
        Modifier.weight(1f)
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 16.dp)
          .verticalScroll(rememberScrollState()),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodyMedium.copy(fontSize = (11 * fontScale).sp),
    )

    // Copy to clipboard Button
    androidx.compose.material3.Button(
      onClick = { copyLogs(context, logs) },
      modifier = Modifier.fillMaxWidth().padding(0.dp),
      shape = androidx.compose.ui.graphics.RectangleShape,
      colors =
        androidx.compose.material3.ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = OblivionV7Tokens.FixedWhite,
        ),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
      Text(
        text = stringResource(R.string.copytoclip),
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = (18 * fontScale).sp),
        color = OblivionV7Tokens.FixedWhite,
      )
    }
  }
}

private fun copyLogs(context: Context, logs: String) {
  val last100 = logs.lines().takeLast(100).joinToString("\n")
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboard.setPrimaryClip(ClipData.newPlainText("Log", last100))
  Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
}
