package org.bepass.oblivion.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bepass.oblivion.R

@Composable
fun InfoScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  Column(Modifier.fillMaxSize().testTag(UiTestTags.INFO)) {
    ScreenHeader(title = stringResource(R.string.aboutApp), onBack = onBack)
    Column(
      modifier =
        Modifier.weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 12.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = stringResource(R.string.aboutAppDesc),
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(16.dp),
      )

      Spacer(modifier = Modifier.height(32.dp))

      // Github Layout
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = 4.dp) // Total 16dp since parent has 12dp
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable {
              context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/bepass-org/oblivion"))
              )
            }
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Image(
            painter = painterResource(R.drawable.ic_github),
            contentDescription = "Github",
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.size(24.dp),
          )
          Text(
            text = "Github",
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
          )
        }
        Text(
          text = "Bepass/Oblivion",
          color = MaterialTheme.colorScheme.onSurface,
          style =
            MaterialTheme.typography.bodyMedium.copy(
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
            ),
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Privacy Policy added to match new settings
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable {
              context.startActivity(
                Intent(
                  Intent.ACTION_VIEW,
                  Uri.parse("https://github.com/bepass-org/oblivion/blob/main/PRIVACY.md"),
                )
              )
            }
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = stringResource(R.string.privacy_policy),
          color = MaterialTheme.colorScheme.onSurface,
          style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        )
        Text(
          text = "PRIVACY.md",
          color = MaterialTheme.colorScheme.onSurface,
          style =
            MaterialTheme.typography.bodyMedium.copy(
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
            ),
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = stringResource(R.string.appVersion),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}
