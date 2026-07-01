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
import org.bepass.oblivion.utils.HostPortParser

data class EndpointEntry(val title: String, val content: String, val removable: Boolean = true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndpointSheet(onDismiss: () -> Unit, onSelected: (String) -> Unit) {
  val endpoints = remember {
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
      Text(
        text = stringResource(R.string.endpoint_advanced_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
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
        isError = content.isNotBlank() && !isAllowedWarpEndpoint(content),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = {
            if (title.isNotBlank() && isAllowedWarpEndpoint(content)) {
              endpoints.add(EndpointEntry(title.trim(), content.trim()))
              saveEndpoints(endpoints)
              title = ""
              content = ""
            }
          },
          enabled = title.isNotBlank() && isAllowedWarpEndpoint(content),
        ) {
          Text(stringResource(R.string.save))
        }
        TextButton(
          onClick = {
            endpoints.clear()
            endpoints.addAll(defaultEndpoints())
            FileManager.set("saved_endpoints", emptySet<String>())
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
            if (endpoint.removable) {
              TextButton(
                onClick = {
                  endpoints.removeAt(index)
                  saveEndpoints(endpoints)
                }
              ) {
                Text(stringResource(R.string.delete))
              }
            }
          }
        }
      }
    }
  }
}

private fun defaultEndpoints(): List<EndpointEntry> =
  listOf(
    EndpointEntry("Automatic WARP endpoint", "", removable = false),
    EndpointEntry("Cloudflare WARP 2408", "162.159.192.1:2408", removable = false),
  )

private fun loadEndpoints(): List<EndpointEntry> = buildList {
  addAll(defaultEndpoints())
  val saved = FileManager.getStringSet("saved_endpoints", emptySet())
  saved.forEach { entry ->
    val parts = entry.split("::", limit = 2)
    if (parts.size == 2 && isAllowedWarpEndpoint(parts[1])) {
      add(EndpointEntry(parts[0], parts[1]))
    }
  }
}

private fun saveEndpoints(endpoints: List<EndpointEntry>) {
  FileManager.set(
    "saved_endpoints",
    endpoints.filter(EndpointEntry::removable).map { "${it.title}::${it.content}" }.toSet(),
  )
}

internal fun isAllowedWarpEndpoint(value: String): Boolean {
  val parsed = HostPortParser.parseOrNull(value) ?: return false
  if (parsed.port !in setOf(500, 1701, 2408, 4500)) return false
  val host = parsed.host.lowercase()
  if (host.startsWith("2606:4700:100:")) return true
  val octets = host.split('.')
  if (octets.size != 4 || octets.any { it.toIntOrNull() !in 0..255 }) return false
  return octets[0] == "162" && octets[1] == "159" && (octets[2] == "192" || octets[2] == "193")
}
