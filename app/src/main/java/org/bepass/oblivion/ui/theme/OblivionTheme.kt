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

private val OblivionOrange = Color(0xffffa200)
private val OblivionBlack = Color(0xff000000)
private val OblivionInk = OblivionBlack
private val OblivionPanel = Color(0xff140d00)
private val OblivionPanelHigh = Color(0xff211500)
private val OblivionOrangeDim = Color(0xff4a2f00)
private val OblivionOrangeText = Color(0xffffd89a)
private val OblivionWhite = Color(0xffffffff)
private val OblivionDarkBackground = Color(0xff0f0a00)
private val OblivionDarkPanel = Color(0xff1c1200)
private val OblivionDarkPanelHigh = Color(0xff2b1b00)
private val OblivionLightBackground = Color(0xfffffbf4)
private val OblivionLightPanel = Color(0xfffff1d6)
private val OblivionLightPanelHigh = Color(0xffffe4ad)
private val OblivionLightText = Color(0xff201a12)
private val OblivionLightMutedText = Color(0xff6b4a12)
private val OblivionLightOutline = Color(0xffffb33a)

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
    primary = OblivionOrange,
    onPrimary = OblivionInk,
    primaryContainer = OblivionOrange,
    onPrimaryContainer = OblivionInk,
    inversePrimary = OblivionOrange,
    secondary = OblivionOrange,
    onSecondary = OblivionInk,
    secondaryContainer = OblivionPanelHigh,
    onSecondaryContainer = OblivionWhite,
    tertiary = OblivionOrange,
    onTertiary = OblivionInk,
    tertiaryContainer = OblivionOrangeDim,
    onTertiaryContainer = OblivionWhite,
    background = OblivionBlack,
    onBackground = OblivionWhite,
    surface = OblivionBlack,
    onSurface = OblivionWhite,
    surfaceVariant = OblivionPanel,
    onSurfaceVariant = OblivionOrangeText,
    surfaceTint = OblivionOrange,
    inverseSurface = OblivionWhite,
    inverseOnSurface = OblivionInk,
    outline = OblivionOrange,
    outlineVariant = OblivionOrangeDim,
    surfaceBright = OblivionPanelHigh,
    surfaceContainer = OblivionPanel,
    surfaceContainerHigh = OblivionPanelHigh,
    surfaceContainerHighest = OblivionPanelHigh,
    surfaceContainerLow = OblivionBlack,
    surfaceContainerLowest = OblivionBlack,
    surfaceDim = OblivionBlack,
  )

private val OblivionDark =
  darkColorScheme(
    primary = OblivionOrange,
    onPrimary = OblivionInk,
    primaryContainer = OblivionOrange,
    onPrimaryContainer = OblivionInk,
    inversePrimary = OblivionOrange,
    secondary = OblivionOrange,
    onSecondary = OblivionInk,
    secondaryContainer = OblivionDarkPanelHigh,
    onSecondaryContainer = OblivionWhite,
    tertiary = OblivionOrange,
    onTertiary = OblivionInk,
    tertiaryContainer = OblivionOrangeDim,
    onTertiaryContainer = OblivionWhite,
    background = OblivionDarkBackground,
    onBackground = OblivionWhite,
    surface = OblivionDarkBackground,
    onSurface = OblivionWhite,
    surfaceVariant = OblivionDarkPanel,
    onSurfaceVariant = OblivionOrangeText,
    surfaceTint = OblivionOrange,
    inverseSurface = OblivionWhite,
    inverseOnSurface = OblivionInk,
    outline = OblivionOrange,
    outlineVariant = OblivionOrangeDim,
    surfaceBright = OblivionDarkPanelHigh,
    surfaceContainer = OblivionDarkPanel,
    surfaceContainerHigh = OblivionDarkPanelHigh,
    surfaceContainerHighest = OblivionDarkPanelHigh,
    surfaceContainerLow = OblivionDarkBackground,
    surfaceContainerLowest = OblivionDarkBackground,
    surfaceDim = OblivionDarkBackground,
  )

private val OblivionLight =
  lightColorScheme(
    primary = OblivionOrange,
    onPrimary = OblivionInk,
    primaryContainer = OblivionOrange,
    onPrimaryContainer = OblivionInk,
    inversePrimary = OblivionOrange,
    secondary = OblivionOrange,
    onSecondary = OblivionInk,
    secondaryContainer = OblivionLightPanelHigh,
    onSecondaryContainer = OblivionLightText,
    tertiary = OblivionOrange,
    onTertiary = OblivionInk,
    tertiaryContainer = OblivionLightPanelHigh,
    onTertiaryContainer = OblivionLightText,
    background = OblivionLightBackground,
    onBackground = OblivionLightText,
    surface = OblivionLightBackground,
    onSurface = OblivionLightText,
    surfaceVariant = OblivionLightPanel,
    onSurfaceVariant = OblivionLightMutedText,
    surfaceTint = OblivionOrange,
    inverseSurface = OblivionLightText,
    inverseOnSurface = OblivionLightBackground,
    outline = OblivionOrange,
    outlineVariant = OblivionLightOutline,
    surfaceBright = OblivionLightBackground,
    surfaceContainer = OblivionLightPanel,
    surfaceContainerHigh = OblivionLightPanelHigh,
    surfaceContainerHighest = OblivionLightPanelHigh,
    surfaceContainerLow = OblivionLightBackground,
    surfaceContainerLowest = OblivionWhite,
    surfaceDim = OblivionLightPanel,
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
