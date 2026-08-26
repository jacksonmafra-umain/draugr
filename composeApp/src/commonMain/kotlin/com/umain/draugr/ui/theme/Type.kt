package com.umain.draugr.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.umain.draugr.resources.Res
import com.umain.draugr.resources.courierprime_bold
import com.umain.draugr.resources.courierprime_bolditalic
import com.umain.draugr.resources.courierprime_italic
import com.umain.draugr.resources.courierprime_regular
import org.jetbrains.compose.resources.Font

@Composable
fun courierPrime(): FontFamily = FontFamily(
    Font(Res.font.courierprime_regular, FontWeight.Normal, FontStyle.Normal),
    Font(Res.font.courierprime_italic, FontWeight.Normal, FontStyle.Italic),
    Font(Res.font.courierprime_bold, FontWeight.Bold, FontStyle.Normal),
    Font(Res.font.courierprime_bolditalic, FontWeight.Bold, FontStyle.Italic),
)

@Composable
fun draugrTypography(): Typography {
    val mono = courierPrime()
    return Typography(
        displayLarge = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = 4.sp,
        ),
        displayMedium = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            letterSpacing = 3.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = 2.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 1.5.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.8.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.6.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 2.sp,
        ),
    )
}
