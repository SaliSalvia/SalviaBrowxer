package com.salvia.salviabrowxer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Core Neutrals ──
val MatteCharcoal = Color(0xFF0A0A0C)       // مشکی زغالی عمیق
val DeepCharcoal = Color(0xFF141418)       // ذغال نیمه‌روشن
val CharcoalSurface = Color(0xFF1C1C20)    // سطح کارت
val CharcoalElevated = Color(0xFF26262C)   // سطح برجسته
val CharcoalBorder = Color(0xFF2E2E34)     // مرزبندی

// ── Pearl White (سفید صدفی ۷ رنگ) ──
val PearlWhite = Color(0xFFF8F7F4)
val PearlWarm = Color(0xFFF1EFE9)
val PearlIridescentPink = Color(0xFFFFE4EC)
val PearlIridescentTeal = Color(0xFFD6FFF8)
val PearlIridescentLavender = Color(0xFFEDE9FF)
val PearlIridescentBlue = Color(0xFFE0F2FF)
val OnPearl = MatteCharcoal

// ── Shiny Silver (نقره‌ای براق) ──
val SilverLight = Color(0xFFE8EAED)
val SilverMid = Color(0xFFC0C5CE)
val SilverDeep = Color(0xFF9AA0AE)
val SilverShine = Color(0xFFF5F7FA)
val SilverMetal = Color(0xFFB8BEC9)

// ── Aurora Teal (انتخاب ۱ - فیروزه شفق) ──
val AuroraTeal = Color(0xFF00DAC6)
val AuroraTealLight = Color(0xFF5EF0DD)
val AuroraTealDeep = Color(0xFF009688)
val AuroraTealContainer = Color(0xFF00332E)
val OnAuroraTeal = MatteCharcoal

// ── Nebula Violet (انتخاب ۲ - بنفش سحابی) ──
val NebulaViolet = Color(0xFF8B5CF6)
val NebulaVioletLight = Color(0xFFB794FF)
val NebulaVioletDeep = Color(0xFF5B21B6)
val NebulaVioletContainer = Color(0xFF1E1042)
val OnNebulaViolet = Color.White

// Legacy aliases for backward compatibility (mapped to new palette)
val Gold = AuroraTeal
val Violet = NebulaViolet
val ElectricIndigo = NebulaVioletDeep

// ── Semantic Mappings ──
val Primary = AuroraTeal
val PrimaryContainer = AuroraTealContainer
val OnPrimary = OnAuroraTeal
val OnPrimaryContainer = AuroraTealLight

val Secondary = NebulaViolet
val SecondaryContainer = NebulaVioletContainer
val OnSecondary = OnNebulaViolet
val OnSecondaryContainer = NebulaVioletLight

val Background = MatteCharcoal
val OnBackground = PearlWhite
val Surface = DeepCharcoal
val OnSurface = PearlWhite
val SurfaceVariant = CharcoalSurface
val OnSurfaceVariant = SilverMid

val Error = Color(0xFFFF5252)
val OnError = Color.White

val MediaDetectedIndicator = AuroraTeal
val DownloadButtonActive = AuroraTeal
val DownloadButtonInactive = Color(0xFF3A3A42)
val FloatingButtonBackground = CharcoalElevated
val FloatingButtonForeground = PearlWhite

// ── Gradients (Pearlescent 7-color effect) ──
val PearlIridescentBrush = Brush.linearGradient(
    colors = listOf(
        PearlWhite,
        PearlIridescentPink,
        PearlIridescentLavender,
        PearlIridescentBlue,
        PearlIridescentTeal,
        PearlWhite
    )
)

val SilverMetallicBrush = Brush.linearGradient(
    colors = listOf(
        SilverShine,
        SilverLight,
        SilverMid,
        SilverLight,
        SilverShine
    )
)

val AuroraBrush = Brush.linearGradient(
    colors = listOf(AuroraTealLight, AuroraTeal, AuroraTealDeep)
)

val NebulaBrush = Brush.linearGradient(
    colors = listOf(NebulaVioletLight, NebulaViolet, NebulaVioletDeep)
)

val CharcoalPearlBrush = Brush.linearGradient(
    colors = listOf(MatteCharcoal, DeepCharcoal, CharcoalSurface)
)

val FabActiveBrush = Brush.radialGradient(
    colors = listOf(AuroraTealLight, AuroraTeal, AuroraTealDeep)
)

val FabInactiveBrush = Brush.radialGradient(
    colors = listOf(Color(0xFF3A3A42), Color(0xFF2E2E34))
)
