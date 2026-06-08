package org.bepass.oblivion.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.bepass.oblivion.R

@Composable
fun InfoScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  Column(Modifier.fillMaxSize()) {
    ScreenHeader(title = stringResource(R.string.aboutApp), onBack = onBack)
    Column(
      modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = stringResource(R.string.slogan),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
      )
      Text(
        text = stringResource(R.string.aboutAppDesc),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Text(
        text = stringResource(R.string.splashText),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      SettingsRow(
        title = stringResource(R.string.github_title),
        description = stringResource(R.string.bepass_repo),
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/bepass-org/oblivion"))) },
      )
      SettingsRow(
        title = stringResource(R.string.privacy_policy),
        description = "PRIVACY.md",
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/bepass-org/oblivion/blob/main/PRIVACY.md"))) },
      )
      Text(
        text = stringResource(R.string.appVersion),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}
