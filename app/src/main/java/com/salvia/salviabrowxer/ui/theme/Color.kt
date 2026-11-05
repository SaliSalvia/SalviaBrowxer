package com.salvia.salviabrowxer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Layered midnight surfaces give the browser chrome, cards and controls clear separation
 * without falling back to flat black.
 */
val MatteCharcoal = Color(0xFF080E19)
val DeepCharcoal = Color(0xFF0C1525)
val CharcoalSurface = Color(0xFF131F32)
val CharcoalElevated = Color(0xFF1C2A40)
val CharcoalBorder = Color(0xFF30445F)

// Cool white and silver keep long reading sessions calm and legible.
val PearlWhite = Color(0xFFF4F7FC)
val PearlWarm = Color(0xFFE9EEF5)
val PearlIridescentPink = Color(0xFFF1EAF0)
val PearlIridescentTeal = Color(0xFFE1EDF3)
val PearlIridescentLavender = Color(0xFFE6EAF5)
val PearlIridescentBlue = Color(0xFFE0EAFA)
val OnPearl = MatteCharcoal

val SilverLight = Color(0xFFD8E1ED)
val SilverMid = Color(0xFFA8B6C8)
val SilverDeep = Color(0xFF7F90A7)
val SilverShine = Color(0xFFFBFCFF)
val SilverMetal = Color(0xFFB8C4D3)

// Sapphire is the primary action color, with white text for accessible button contrast.
val SapphireBlue = Color(0xFF347DCE)
val SapphireBlueLight = Color(0xFF8DBBFA)
val SapphireBlueDeep = Color(0xFF1C559B)
val SapphireBlueContainer = Color(0xFF18385F)
val OnSapphireBlue = MatteCharcoal

// Metallic gold is reserved for secondary emphasis, status details and brand highlights.
val MetallicGold = Color(0xFFD4A84E)
val MetallicGoldLight = Color(0xFFF0D18A)
val MetallicGoldDeep = Color(0xFF966C20)
val MetallicGoldContainer = Color(0xFF40341E)
val OnMetallicGold = Color(0xFF111925)

// Existing feature code keeps its stable names while the visual identity changes underneath.
val AuroraTeal = SapphireBlue
val AuroraTealLight = SapphireBlueLight
val AuroraTealDeep = SapphireBlueDeep
val AuroraTealContainer = SapphireBlueContainer
val OnAuroraTeal = OnSapphireBlue
val NebulaViolet = MetallicGold
val NebulaVioletLight = MetallicGoldLight
val NebulaVioletDeep = MetallicGoldDeep
val NebulaVioletContainer = MetallicGoldContainer
val OnNebulaViolet = OnMetallicGold

// Legacy aliases kept for existing feature code and older resources.
val Gold = MetallicGold
val Violet = NebulaViolet
val ElectricIndigo = SapphireBlueDeep

// Legacy mark aliases now follow the official sapphire-and-gold identity.
val OrbitGold = MetallicGold
val OrbitGoldLight = MetallicGoldLight
val OrbitBlue = SapphireBlue
val OrbitBlueLight = SapphireBlueLight

val BlushPink = Color(0xFFE36C78)
val BlushPinkLight = Color(0xFFFFA3AB)
val BlushPinkDeep = Color(0xFFB5324D)
val BlushPinkContainer = Color(0xFF451F27)

val NebulaMist = Color(0xFF172236)
val NebulaEdge = Color(0xFF324865)

// Surface micro-contrast, for fields and nested rows.
val SurfaceField = Color(0xFF0D1727)
val SurfaceRow = Color(0xFF101A2B)
val SurfaceInk = SilverMid

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

val Error = Color(0xFFFF858C)
val OnError = Color.White

val MediaDetectedIndicator = AuroraTeal
val DownloadButtonActive = AuroraTeal
val DownloadButtonInactive = Color(0xFF30445F)
val FloatingButtonBackground = CharcoalElevated
val FloatingButtonForeground = PearlWhite

// Brand and surface gradients are deliberately low-contrast: decoration supports hierarchy instead
// of competing with the page or the action labels.
val PearlIridescentBrush = Brush.linearGradient(
    colors = listOf(PearlWhite, PearlIridescentBlue, PearlIridescentTeal, PearlWhite)
)

val SilverMetallicBrush = Brush.linearGradient(
    colors = listOf(SilverShine, SilverLight, SilverMetal, SilverLight, SilverShine)
)

val AuroraBrush = Brush.linearGradient(
    colors = listOf(SapphireBlueLight, SapphireBlue, SapphireBlueDeep)
)

val NebulaBrush = Brush.linearGradient(
    colors = listOf(MetallicGoldLight, MetallicGold, MetallicGoldDeep)
)

val CharcoalPearlBrush = Brush.linearGradient(
    colors = listOf(MatteCharcoal, DeepCharcoal, CharcoalSurface)
)

val FabActiveBrush = Brush.radialGradient(
    colors = listOf(SapphireBlueLight, SapphireBlue, SapphireBlueDeep)
)

val FabInactiveBrush = Brush.radialGradient(
    colors = listOf(Color(0xFF30445F), CharcoalElevated)
)

val NebulaSurfaceBrush = Brush.verticalGradient(
    colors = listOf(CharcoalSurface, DeepCharcoal, MatteCharcoal)
)

val TopBarBrush = Brush.verticalGradient(
    colors = listOf(Color(0xFF101C30), DeepCharcoal)
)

val BottomBarBrush = Brush.verticalGradient(
    colors = listOf(DeepCharcoal, MatteCharcoal)
)

val AddressBarBrush = Brush.linearGradient(
    colors = listOf(CharcoalSurface, DeepCharcoal)
)

/** A single quiet brand accent for separators and focus states. */
val PearlEdgeBrush = Brush.horizontalGradient(
    colors = listOf(
        SapphireBlue.copy(alpha = 0.18f),
        MetallicGold.copy(alpha = 0.22f),
        SapphireBlue.copy(alpha = 0.18f)
    )
)

val NebulaBlushBrush = Brush.linearGradient(
    colors = listOf(SapphireBlue, MetallicGold, MetallicGoldLight)
)

val AccentIndicatorBrush = Brush.horizontalGradient(
    colors = listOf(MetallicGoldLight, MetallicGold, MetallicGoldDeep)
)

val DownloadCtaBrush = Brush.linearGradient(
    colors = listOf(SapphireBlue, SapphireBlueDeep)
)

val FabActiveBrushNebula = Brush.radialGradient(
    colors = listOf(SapphireBlueLight, SapphireBlue, SapphireBlueDeep)
)

val FabInactiveBrushNebula = Brush.radialGradient(
    colors = listOf(Color(0xFF30445F), CharcoalElevated, DeepCharcoal)
)

val SplashNebulaBrush = Brush.radialGradient(
    colors = listOf(Color(0xFF192C48), DeepCharcoal, MatteCharcoal),
    radius = 900f
)

val AuroraTealFieldFocus = AuroraTeal.copy(alpha = 0.82f)
val AuroraTealFieldRest = AuroraTeal.copy(alpha = 0.22f)
val AuroraTealSurface = AuroraTeal.copy(alpha = 0.10f)
val AuroraTealRing = AuroraTeal.copy(alpha = 0.40f)
val NebulaVioletSurface = NebulaViolet.copy(alpha = 0.10f)
val PearlFieldHint = SilverDeep.copy(alpha = 0.95f)
val EmptySurface = CharcoalSurface.copy(alpha = 0.72f)
