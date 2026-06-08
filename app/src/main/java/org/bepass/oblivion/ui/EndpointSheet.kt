package org.bepass.oblivion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.bepass.oblivion.R
import org.bepass.oblivion.utils.FileManager

data class EndpointEntry(val title: String, val content: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndpointSheet(onDismiss: () -> Unit, onSelected: (String) -> Unit) {
  val endpoints =
    remember {
      mutableStateListOf<EndpointEntry>().apply {
        addAll(loadEndpoints())
      }
    }
  var title by remember { mutableStateOf("") }
  var content by remember { mutableStateOf("") }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(stringResource(R.string.saved_endpoints))
      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text(stringResource(R.string.title)) },
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = content,
        onValueChange = { content = it },
        label = { Text(stringResource(R.string.content)) },
        modifier = Modifier.fillMaxWidth(),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = {
            if (title.isNotBlank() && content.isNotBlank()) {
              endpoints.add(EndpointEntry(title.trim(), content.trim()))
              saveEndpoints(endpoints)
              title = ""
              content = ""
            }
          }
        ) {
          Text(stringResource(R.string.save))
        }
        TextButton(
          onClick = {
            endpoints.clear()
            endpoints.add(EndpointEntry("Default", "engage.cloudflareclient.com:2408"))
            saveEndpoints(endpoints)
          }
        ) {
          Text(stringResource(R.string.reset_to_default_endpoint))
        }
      }
      LazyColumn {
        itemsIndexed(endpoints) { index, endpoint ->
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable { onSelected(endpoint.content) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Column(Modifier.weight(1f)) {
              Text(endpoint.title)
              Text(endpoint.content)
            }
            TextButton(onClick = {
              endpoints.removeAt(index)
              saveEndpoints(endpoints)
            }) {
              Text(stringResource(R.string.delete))
            }
          }
        }
      }
    }
  }
}

private fun loadEndpoints(): List<EndpointEntry> =
  buildList {
    add(EndpointEntry("Suggested Endpoint (Best for Iran)", ""))
    add(EndpointEntry("Masque (HTTP/3) Endpoint", "engage.cloudflareclient.com:443"))
    add(EndpointEntry("IPv6 Endpoint", "[2606:4700:d0::a29f:c001]:2408"))
    val saved = FileManager.getStringSet("saved_endpoints", emptySet())
    if (saved.isEmpty()) add(EndpointEntry("Default", "engage.cloudflareclient.com:2408"))
    saved.forEach { entry ->
      val parts = entry.split("::")
      if (parts.size == 2) add(EndpointEntry(parts[0], parts[1]))
    }
  }

private fun saveEndpoints(endpoints: List<EndpointEntry>) {
  FileManager.set("saved_endpoints", endpoints.map { "${it.title}::${it.content}" }.toSet())
}
