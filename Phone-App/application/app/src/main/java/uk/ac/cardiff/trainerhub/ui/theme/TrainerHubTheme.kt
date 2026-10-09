package uk.ac.cardiff.trainerhub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkColours = darkColorScheme(
    primary = Color(0xFF49DAFF),
    onPrimary = Color(0xFF062031),
    primaryContainer = Color(0xFF122D41),
    onPrimaryContainer = Color(0xFFF4F8FF),
    secondary = Color(0xFFFF9546),
    onSecondary = Color(0xFF291300),
    secondaryContainer = Color(0xFF40291D),
    onSecondaryContainer = Color(0xFFFFDAC1),
    tertiary = Color(0xFFB7C5D9),
    background = Color(0xFF060914),
    onBackground = Color(0xFFF4F8FF),
    surface = Color(0xFF101827),
    onSurface = Color(0xFFF4F8FF),
    surfaceVariant = Color(0xFF172339),
    onSurfaceVariant = Color(0xFFB7C5D9),
    surfaceContainerLowest = Color(0xFF060914),
    surfaceContainerLow = Color(0xFF0C1220),
    surfaceContainer = Color(0xFF101827),
    surfaceContainerHigh = Color(0xFF172339),
    surfaceContainerHighest = Color(0xFF203049),
    outline = Color(0xFF70839E),
    outlineVariant = Color(0xFF2B3B53),
    error = Color(0xFFFFABB5),
    errorContainer = Color(0xFF471824),
    onErrorContainer = Color(0xFFFFD9DE),
)

private val LightColours = lightColorScheme(
    primary = Color(0xFF086987),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F5FC),
    onPrimaryContainer = Color(0xFF112139),
    secondary = Color(0xFFA94A08),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE9D8),
    onSecondaryContainer = Color(0xFF562800),
    tertiary = Color(0xFF475A73),
    background = Color(0xFFF3F6FB),
    onBackground = Color(0xFF112139),
    surface = Color.White,
    onSurface = Color(0xFF112139),
    surfaceVariant = Color(0xFFE9EFF8),
    onSurfaceVariant = Color(0xFF475A73),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F6FB),
    surfaceContainer = Color(0xFFEFF3FA),
    surfaceContainerHigh = Color(0xFFE9EFF8),
    surfaceContainerHighest = Color(0xFFE1E9F5),
    outline = Color(0xFF6B7F99),
    outlineVariant = Color(0xFFCCD7E6),
    error = Color(0xFFB42332),
    errorContainer = Color(0xFFFFE9EC),
    onErrorContainer = Color(0xFF731421),
)

private val DefaultTypography = Typography()

private val TrainerHubTypography = DefaultTypography.copy(
        headlineLarge = DefaultTypography.headlineLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            lineHeight = 40.sp,
        ),
        headlineMedium = DefaultTypography.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 36.sp,
        ),
        headlineSmall = DefaultTypography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 25.sp,
            lineHeight = 31.sp,
        ),
        titleLarge = DefaultTypography.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        ),
        titleMedium = DefaultTypography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
        ),
        labelLarge = DefaultTypography.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
        ),
        bodyLarge = DefaultTypography.bodyLarge.copy(
            fontSize = 17.sp,
            lineHeight = 25.sp,
        ),
        bodyMedium = DefaultTypography.bodyMedium.copy(
            fontSize = 15.sp,
            lineHeight = 22.sp,
        ),
    )

private val TrainerHubShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun TrainerHubTheme(
    content: @Composable () -> Unit,
) {
    val colours = if (isSystemInDarkTheme()) DarkColours else LightColours

    MaterialTheme(
        colorScheme = colours,
        typography = TrainerHubTypography,
        shapes = TrainerHubShapes,
        content = content,
    )
}
