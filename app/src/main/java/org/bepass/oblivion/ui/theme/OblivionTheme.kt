package org.bepass.oblivion.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.bepass.oblivion.R
import org.bepass.oblivion.utils.FontSizeHelper
import org.bepass.oblivion.utils.ThemeHelper

private val V7Primary = Color(0xffffa200)
private val V7LightBackground = Color(0xffffffff)
private val V7LightText = Color(0xff1C202C)
private val V7LightSubtitle = Color(0xff7B8D9D)
private val V7LightGray = Color(0xffD7D7D7)

private val V7DarkBackground = Color(0xff1C202C)
private val V7DarkText = Color(0xffffffff)
private val V7DarkSubtitle = Color(0xff7B8D9D)
private val V7DarkDarkGray = Color(0xff383A45)

object OblivionV7Tokens {
  val FixedWhite = Color(0xffffffff)
  val SwitchTrackOff = Color(0xffd7d7d7)
}

private val OblivionFontFamily =
  FontFamily(
    Font(R.font.shabnam, FontWeight.Normal),
    Font(R.font.shabnammedium, FontWeight.Medium),
    Font(R.font.shabnambold, FontWeight.Bold),
  )

private val BaseTypography = Typography()

private fun oblivionTypography(scale: Float): Typography =
  Typography(
    displayLarge = BaseTypography.displayLarge.oblivionText(scale),
    displayMedium = BaseTypography.displayMedium.oblivionText(scale),
    displaySmall = BaseTypography.displaySmall.oblivionText(scale),
    headlineLarge = BaseTypography.headlineLarge.oblivionText(scale),
    headlineMedium = BaseTypography.headlineMedium.oblivionText(scale),
    headlineSmall = BaseTypography.headlineSmall.oblivionText(scale),
    titleLarge = BaseTypography.titleLarge.oblivionText(scale),
    titleMedium = BaseTypography.titleMedium.oblivionText(scale),
    titleSmall = BaseTypography.titleSmall.oblivionText(scale),
    bodyLarge = BaseTypography.bodyLarge.oblivionText(scale),
    bodyMedium = BaseTypography.bodyMedium.oblivionText(scale),
    bodySmall = BaseTypography.bodySmall.oblivionText(scale),
    labelLarge = BaseTypography.labelLarge.oblivionText(scale),
    labelMedium = BaseTypography.labelMedium.oblivionText(scale),
    labelSmall = BaseTypography.labelSmall.oblivionText(scale),
  )

private fun TextStyle.oblivionText(scale: Float): TextStyle =
  copy(fontFamily = OblivionFontFamily, fontSize = fontSize * scale)

private val OblivionOled =
  darkColorScheme(
    primary = V7Primary,
    onPrimary = Color.Black,
    primaryContainer = V7Primary,
    onPrimaryContainer = Color.Black,
    inversePrimary = V7Primary,
    secondary = V7Primary,
    onSecondary = Color.Black,
    secondaryContainer = Color.Black,
    onSecondaryContainer = Color.White,
    tertiary = V7Primary,
    onTertiary = Color.Black,
    tertiaryContainer = V7Primary,
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color.Black,
    onSurfaceVariant = V7DarkSubtitle,
    surfaceTint = V7Primary,
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    outline = V7Primary,
    outlineVariant = Color.DarkGray,
    surfaceBright = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceDim = Color.Black,
  )

private val OblivionDark =
  darkColorScheme(
    primary = V7Primary,
    onPrimary = Color.White,
    primaryContainer = V7Primary,
    onPrimaryContainer = Color.White,
    inversePrimary = V7Primary,
    secondary = V7Primary,
    onSecondary = Color.White,
    secondaryContainer = V7DarkDarkGray,
    onSecondaryContainer = Color.White,
    tertiary = V7Primary,
    onTertiary = Color.White,
    tertiaryContainer = V7DarkDarkGray,
    onTertiaryContainer = Color.White,
    background = V7DarkBackground,
    onBackground = V7DarkText,
    surface = V7DarkBackground,
    onSurface = V7DarkText,
    surfaceVariant = V7DarkDarkGray,
    onSurfaceVariant = V7DarkSubtitle,
    surfaceTint = V7Primary,
    inverseSurface = Color.Black,
    inverseOnSurface = Color.White,
    outline = V7Primary,
    outlineVariant = V7DarkDarkGray,
    surfaceBright = V7DarkBackground,
    surfaceContainer = V7DarkBackground,
    surfaceContainerHigh = V7DarkBackground,
    surfaceContainerHighest = V7DarkDarkGray,
    surfaceContainerLow = V7DarkBackground,
    surfaceContainerLowest = V7DarkBackground,
    surfaceDim = V7DarkBackground,
  )

private val OblivionLight =
  lightColorScheme(
    primary = V7Primary,
    onPrimary = Color.White,
    primaryContainer = V7Primary,
    onPrimaryContainer = Color.White,
    inversePrimary = V7Primary,
    secondary = V7Primary,
    onSecondary = Color.White,
    secondaryContainer = V7LightGray,
    onSecondaryContainer = V7LightText,
    tertiary = V7Primary,
    onTertiary = Color.White,
    tertiaryContainer = V7LightGray,
    onTertiaryContainer = V7LightText,
    background = V7LightBackground,
    onBackground = V7LightText,
    surface = V7LightBackground,
    onSurface = V7LightText,
    surfaceVariant = V7LightBackground,
    onSurfaceVariant = V7LightSubtitle,
    surfaceTint = V7Primary,
    inverseSurface = Color.Black,
    inverseOnSurface = Color.White,
    outline = V7Primary,
    outlineVariant = V7LightGray,
    surfaceBright = V7LightBackground,
    surfaceContainer = V7LightBackground,
    surfaceContainerHigh = V7LightBackground,
    surfaceContainerHighest = V7LightGray,
    surfaceContainerLow = V7LightBackground,
    surfaceContainerLowest = V7LightBackground,
    surfaceDim = V7LightBackground,
  )

@Composable
fun OblivionTheme(content: @Composable () -> Unit) {
  val theme by ThemeHelper.themeFlow.collectAsState()
  val fontSize by FontSizeHelper.fontSizeFlow.collectAsState()
  val scheme: ColorScheme = colorSchemeFor(theme)
  val typography = remember(fontSize) { oblivionTypography(fontSize.scale) }

  MaterialTheme(colorScheme = scheme, typography = typography) {
    Surface(modifier = Modifier.fillMaxSize(), color = scheme.background, content = content)
  }
}

private fun colorSchemeFor(theme: ThemeHelper.Theme): ColorScheme =
  when (theme) {
    ThemeHelper.Theme.OLED -> OblivionOled
    ThemeHelper.Theme.DARK -> OblivionDark
    ThemeHelper.Theme.LIGHT -> OblivionLight
  }
