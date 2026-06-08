package org.bepass.oblivion.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.activity.ComponentActivity
import android.content.Context
import android.os.Build
import org.bepass.oblivion.R
import org.bepass.oblivion.utils.LocaleManager

@Composable
fun LanguageDialog(onDismiss: () -> Unit, onLocaleChanged: () -> Unit) {
  val context = LocalContext.current
  val locales = LocaleManager.availableLocales(context)
  val currentTag = LocaleManager.currentLocaleTag()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.select_language)) },
    text = {
      Column {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clickable {
                if (currentTag.isNotBlank()) {
                  LocaleManager.setSystemDefaultLocale(context)
                  recreateForLegacyLocaleChange(context)
                }
                onLocaleChanged()
              }
              .padding(vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(selected = currentTag.isBlank(), onClick = null)
          Text(stringResource(R.string.system_default))
        }
        locales.forEach { option ->
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable {
                  if (option.tag != currentTag) {
                    LocaleManager.setAppLocale(context, option.locale)
                    recreateForLegacyLocaleChange(context)
                  }
                  onLocaleChanged()
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(selected = option.tag == currentTag, onClick = null)
            Text(option.displayName)
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
  )
}

private fun recreateForLegacyLocaleChange(context: Context) {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
    (context as? ComponentActivity)?.recreate()
  }
}
