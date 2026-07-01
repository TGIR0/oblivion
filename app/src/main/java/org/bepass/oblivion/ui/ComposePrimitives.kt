package org.bepass.oblivion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.bepass.oblivion.R

@Composable
fun ScreenHeader(
  title: String,
  onBack: (() -> Unit)? = null,
  actions: @Composable () -> Unit = {},
) {
  Row(
    modifier =
      Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (onBack != null) {
      IconButton(onClick = onBack, modifier = Modifier.size(35.dp)) {
        Icon(
          painterResource(R.drawable.ic_back),
          contentDescription = titleBackDescription(),
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
    Spacer(modifier = Modifier.weight(1f))
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Normal,
      color = MaterialTheme.colorScheme.onSurface,
    )
    actions()
  }
}

@Composable
fun SettingsRow(
  title: String,
  description: String,
  value: String? = null,
  onClick: (() -> Unit)? = null,
  leading: @Composable (() -> Unit)? = null,
) {
  val fontScale =
    org.bepass.oblivion.utils.FontSizeHelper.fontSizeFlow.collectAsStateWithLifecycle().value.scale
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .height(80.dp)
          .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
          .padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (leading != null) {
        leading()
      }

      Column(
        modifier = Modifier.weight(1f).padding(start = if (leading != null) 16.dp else 0.dp),
        horizontalAlignment = Alignment.Start,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = (20 * fontScale).sp),
            color = MaterialTheme.colorScheme.onSurface,
          )
          value
            ?.takeIf { it.isNotBlank() }
            ?.let {
              Text(
                text = it,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = (20 * fontScale).sp),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
              )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
          text = description,
          style = MaterialTheme.typography.bodyMedium.copy(fontSize = (16 * fontScale).sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // Divider matching v7 style
    Spacer(modifier = Modifier.height(10.dp))
    Box(
      modifier =
        Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant)
    )
  }
}

@Composable
fun SwitchSettingsRow(
  title: String,
  description: String,
  checked: Boolean,
  enabled: Boolean = true,
  onCheckedChange: (Boolean) -> Unit,
) {
  SettingsRow(
    title = title,
    description = description,
    onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
    leading = {
      Box(
        modifier = Modifier.padding(start = 6.dp, end = 12.dp),
        contentAlignment = Alignment.Center,
      ) {
        androidx.compose.material3.Switch(
          checked = checked,
          onCheckedChange = null,
          enabled = enabled,
        )
      }
    },
  )
}

@Composable private fun titleBackDescription(): String = stringResource(R.string.back)
