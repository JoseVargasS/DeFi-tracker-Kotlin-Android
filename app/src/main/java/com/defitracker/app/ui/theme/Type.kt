package com.defitracker.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.defitracker.app.R

// texto UI estilo cripto + mono tabular para precios (no tiemblan)
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

// atajo para Text() con cifras tabulares (fontFeatureSettings no es parametro directo de Text)
val GeistTnum = TextStyle(fontFamily = Geist, fontFeatureSettings = "tnum")
