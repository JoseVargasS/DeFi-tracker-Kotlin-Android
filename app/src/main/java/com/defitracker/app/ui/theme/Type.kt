package com.defitracker.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.defitracker.app.R

// texto UI estilo cripto + mono tabular para precios (no tiemblan)
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

// precios principales, condensada estilo exchange
val Rajdhani = FontFamily(
    Font(R.font.rajdhani_regular, FontWeight.Normal),
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold)
)

// titulos, precios y numeros, ancha y legible sin abuso de bold
val Lato = FontFamily(
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_semibold, FontWeight.SemiBold),
    Font(R.font.lato_bold, FontWeight.Bold)
)

// stack base de la app: Geist + fallback del sistema (equivale al
// Geist, PingFang SC, Microsoft Yahei, Helvetica, Arial, sans-serif de la referencia)
val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold)
)

private val base = Typography()
val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Geist),
    displayMedium = base.displayMedium.copy(fontFamily = Geist),
    displaySmall = base.displaySmall.copy(fontFamily = Geist),
    headlineLarge = base.headlineLarge.copy(fontFamily = Geist),
    headlineMedium = base.headlineMedium.copy(fontFamily = Geist),
    headlineSmall = base.headlineSmall.copy(fontFamily = Geist),
    titleLarge = base.titleLarge.copy(fontFamily = Geist),
    titleMedium = base.titleMedium.copy(fontFamily = Geist),
    titleSmall = base.titleSmall.copy(fontFamily = Geist),
    bodyLarge = base.bodyLarge.copy(fontFamily = Geist),
    bodyMedium = base.bodyMedium.copy(fontFamily = Geist),
    bodySmall = base.bodySmall.copy(fontFamily = Geist),
    labelLarge = base.labelLarge.copy(fontFamily = Geist),
    labelMedium = base.labelMedium.copy(fontFamily = Geist),
    labelSmall = base.labelSmall.copy(fontFamily = Geist)
)

// numeros de precio/balance en Geist tabular para que no tiemble el layout
val NumeralStyle = TextStyle(fontFamily = Geist, fontFeatureSettings = "tnum")

// atajo para Text() con cifras tabulares (fontFeatureSettings no es parametro directo de Text)
val GeistTnum = TextStyle(fontFamily = Geist, fontFeatureSettings = "tnum")
