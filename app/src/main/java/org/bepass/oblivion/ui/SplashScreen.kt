package org.bepass.oblivion.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.bepass.oblivion.R

@Composable
fun SplashScreen(onContinue: () -> Unit) {
  BoxWithConstraints(
    modifier =
      Modifier.fillMaxSize()
        .testTag(UiTestTags.SPLASH)
        .clickable(onClick = onContinue)
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(20.dp)
  ) {
    val compact = maxHeight < 620.dp
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = if (compact) 4.dp else 16.dp),
      ) {
        Surface(
          modifier = Modifier.size(if (compact) 112.dp else 144.dp),
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary,
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Image(
              painter = painterResource(R.drawable.segaro),
              contentDescription = stringResource(R.string.segaro),
              modifier = Modifier.size(if (compact) 82.dp else 106.dp),
            )
          }
        }
        Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
        Text(
          text = stringResource(R.string.app_name).uppercase(),
          style =
            if (compact) MaterialTheme.typography.displaySmall
            else MaterialTheme.typography.displayMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
          text = stringResource(R.string.means),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 8.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Image(
          painter = painterResource(R.drawable.yousef),
          contentDescription = stringResource(R.string.yousef),
          modifier = Modifier.size(if (compact) 68.dp else 88.dp).clip(RoundedCornerShape(16.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Text(
          text = stringResource(R.string.splashText),
          style =
            if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f),
          maxLines = if (compact) 4 else 5,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Text(
        text = stringResource(R.string.slogan),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
      )
    }
  }
}
