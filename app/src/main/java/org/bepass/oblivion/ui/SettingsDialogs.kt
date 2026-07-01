package org.bepass.oblivion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.bepass.oblivion.R
import org.bepass.oblivion.enums.VpnCoreType

@Composable
fun EditValueDialog(title: String, value: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
  var text by remember(value) { mutableStateOf(value) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
    confirmButton = {
      TextButton(onClick = { onSave(text.trim()) }) { Text(stringResource(R.string.update)) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}

@Composable
fun OptionDialog(
  title: String,
  options: List<String>,
  selectedIndex: Int,
  onDismiss: () -> Unit,
  onSelected: (Int) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column {
        options.forEachIndexed { index, option ->
          Row(
            modifier =
              Modifier.fillMaxWidth().clickable { onSelected(index) }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(selected = index == selectedIndex, onClick = null)
            Text(option)
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}

@Composable
fun CoreSelectionDialog(
  modes: List<VpnCoreType>,
  selectedStorageId: Int,
  onDismiss: () -> Unit,
  onSelected: (VpnCoreType) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.core_title)) },
    text = {
      Column {
        modes.forEach { mode ->
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .then(
                  if (mode.isReady) {
                    Modifier.clickable { onSelected(mode) }
                  } else {
                    Modifier
                  }
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = mode.storageId == selectedStorageId,
              enabled = mode.isReady,
              onClick = null,
            )
            Column(Modifier.padding(start = 8.dp)) {
              Text(stringResource(mode.labelRes))
              Text(
                text =
                  stringResource(
                    if (mode.isReady) mode.descriptionRes else mode.availabilityReasonRes
                  ),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}
