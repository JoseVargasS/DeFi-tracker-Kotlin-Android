package com.defitracker.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.defitracker.app.R

// ponytail: texto UI estilo cripto + mono tabular para precios (no tiemblan)
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold)
)

val PlexMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.plex_mono_bold, FontWeight.Bold)
)

// ponytail: precios principales, condensada estilo exchange
val Rajdhani = FontFamily(
    Font(R.font.rajdhani_regular, FontWeight.Normal),
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold)
)

// ponytail: titulos, precios y numeros, ancha y legible sin abuso de bold
val Lato = FontFamily(
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_semibold, FontWeight.SemiBold),
    Font(R.font.lato_bold, FontWeight.Bold)
)

private val base = Typography()
val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = SpaceGrotesk),
    displayMedium = base.displayMedium.copy(fontFamily = SpaceGrotesk),
    displaySmall = base.displaySmall.copy(fontFamily = SpaceGrotesk),
    headlineLarge = base.headlineLarge.copy(fontFamily = SpaceGrotesk),
    headlineMedium = base.headlineMedium.copy(fontFamily = SpaceGrotesk),
    headlineSmall = base.headlineSmall.copy(fontFamily = SpaceGrotesk),
    titleLarge = base.titleLarge.copy(fontFamily = SpaceGrotesk),
    titleMedium = base.titleMedium.copy(fontFamily = SpaceGrotesk),
    titleSmall = base.titleSmall.copy(fontFamily = SpaceGrotesk),
    bodyLarge = base.bodyLarge.copy(fontFamily = SpaceGrotesk),
    bodyMedium = base.bodyMedium.copy(fontFamily = SpaceGrotesk),
    bodySmall = base.bodySmall.copy(fontFamily = SpaceGrotesk),
    labelLarge = base.labelLarge.copy(fontFamily = SpaceGrotesk),
    labelMedium = base.labelMedium.copy(fontFamily = SpaceGrotesk),
    labelSmall = base.labelSmall.copy(fontFamily = SpaceGrotesk)
)

// ponytail: numeros de precio/balance, tabulares para que no tiemble el layout
val NumeralStyle = TextStyle(fontFamily = PlexMono, fontFeatureSettings = "tnum")
