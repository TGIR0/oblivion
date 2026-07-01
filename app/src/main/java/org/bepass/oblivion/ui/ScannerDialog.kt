package org.bepass.oblivion.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.bepass.oblivion.R

@Composable
fun ScannerDialog(onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.scanner_section_title)) },
    text = { Text(stringResource(R.string.scanner_section_desc)) },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
    },
  )
}
