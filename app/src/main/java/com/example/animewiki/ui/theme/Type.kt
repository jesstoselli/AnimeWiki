package com.example.animewiki.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.example.animewiki.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val quicksand = GoogleFont("Quicksand")

private val QuicksandFamily = FontFamily(
    Font(googleFont = quicksand, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = quicksand, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = quicksand, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = quicksand, fontProvider = provider, weight = FontWeight.Bold)
)

val SakuraTypography = Typography(
    displayLarge = TextStyle(fontFamily = QuicksandFamily, fontWeight = FontWeight.Bold, fontSize = 57.sp),
    headlineLarge = TextStyle(fontFamily = QuicksandFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = QuicksandFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = QuicksandFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = QuicksandFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = QuicksandFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = QuicksandFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(
        fontFamily = QuicksandFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = QuicksandFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = QuicksandFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(fontFamily = QuicksandFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = QuicksandFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = QuicksandFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp)
)
