package com.salvia.salviabrowxer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Core Neutrals ──
val MatteCharcoal = Color(0xFF0B0B0F)       // مشکی زغالی عمیق
val DeepCharcoal = Color(0xFF15151A)       // ذغال نیمه‌روشن
val CharcoalSurface = Color(0xFF1D1D22)    // سطح کارت
val CharcoalElevated = Color(0xFF232329)   // سطح برجسته
val CharcoalBorder = Color(0xFF2D2D34)     // مرزبندی

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

// ── Orbital brand mark (gold ring + electric blue energy) ──
val OrbitGold = Color(0xFFFFB300)
val OrbitGoldLight = Color(0xFFFFD54F)
val OrbitBlue = Color(0xFF2196F3)
val OrbitBlueLight = Color(0xFF4FC3F7)

// ── Blush Pink (صورتی کم‌رنگ) ──
val BlushPink = Color(0xFFFFB3D1)
val BlushPinkLight = Color(0xFFFFD6E8)
val BlushPinkDeep = Color(0xFFE0679E)
val BlushPinkContainer = Color(0xFF2B1521)

// ── Nebula surfaces (سطوح بنفش سحابی روی ذغال) ──
val NebulaMist = Color(0xFF241A3E)
val NebulaEdge = Color(0xFF3B2A66)

// ── Surface micro-contrast (refinement layer, kept subtle) ──
val SurfaceField = Color(0xFF26262D)        // inside paste / search fields
val SurfaceRow = Color(0xFF1A1A20)          // 목록 행 안쪽 면
val SurfaceInk = SilverMid                  // 보조 텍스트의 실제 조도 기준

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

// ── Nebula & Blush gradients (سحابی + صورتی روی ذغال) ──
val NebulaSurfaceBrush = Brush.verticalGradient(
    colors = listOf(CharcoalSurface, NebulaMist.copy(alpha = 0.55f), MatteCharcoal)
)

val TopBarBrush = Brush.verticalGradient(
    colors = listOf(NebulaMist.copy(alpha = 0.9f), CharcoalSurface)
)

val BottomBarBrush = Brush.verticalGradient(
    colors = listOf(CharcoalSurface, MatteCharcoal)
)

val AddressBarBrush = Brush.linearGradient(
    colors = listOf(NebulaMist, DeepCharcoal, NebulaMist.copy(alpha = 0.8f))
)

// Pearl iridescence edge — the 7-color reflection the brand is built on
val PearlEdgeBrush = Brush.horizontalGradient(
    colors = listOf(
        BlushPinkLight.copy(alpha = 0.55f),
        NebulaVioletLight.copy(alpha = 0.55f),
        PearlIridescentTeal.copy(alpha = 0.55f),
        NebulaVioletLight.copy(alpha = 0.55f),
        BlushPinkLight.copy(alpha = 0.55f)
    )
)

// Accent actions: violet → blush → blue, the nebula signature sweep
val NebulaBlushBrush = Brush.linearGradient(
    colors = listOf(NebulaViolet, BlushPink, OrbitBlueLight)
)

val AccentIndicatorBrush = Brush.horizontalGradient(
    colors = listOf(NebulaViolet, BlushPink, AuroraTeal)
)

val DownloadCtaBrush = Brush.linearGradient(
    colors = listOf(NebulaViolet, NebulaVioletDeep)
)

val FabActiveBrushNebula = Brush.radialGradient(
    colors = listOf(NebulaVioletLight, NebulaViolet, NebulaVioletDeep)
)

val FabInactiveBrushNebula = Brush.radialGradient(
    colors = listOf(NebulaMist, CharcoalElevated, DeepCharcoal)
)

val SplashNebulaBrush = Brush.radialGradient(
    colors = listOf(NebulaMist, DeepCharcoal, MatteCharcoal),
    radius = 900f
)

// ── Micro-animations ──
// A field border does not jump between neutral and focused; it breathes there, which is what makes
// focus feel like a surface change instead of a state machine.
val AuroraTealFieldFocus = AuroraTeal.copy(alpha = 0.85f)
val AuroraTealFieldRest = AuroraTeal.copy(alpha = 0.18f)
val AuroraTealSurface = AuroraTeal.copy(alpha = 0.10f)
val AuroraTealRing = AuroraTeal.copy(alpha = 0.40f)
val NebulaVioletSurface = NebulaViolet.copy(alpha = 0.10f)
val PearlFieldHint = SilverDeep.copy(alpha = 0.85f)
val EmptySurface = CharcoalSurface.copy(alpha = 0.75f)

