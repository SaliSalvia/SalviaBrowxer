package com.salvia.salviabrowxer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Salvia's dark canvas: cool graphite instead of pure black, with enough separation between
 * page, chrome and controls to make the interface feel calm without losing contrast.
 */
val MatteCharcoal = Color(0xFF000000)
val DeepCharcoal = Color(0xFF100B15)
val CharcoalSurface = Color(0xFF1A1420)
val CharcoalElevated = Color(0xFF261D2E)
val CharcoalBorder = Color(0xFF3A2D46)

// Warm, neutral foregrounds keep long sessions comfortable; muted text remains legible on graphite.
val PearlWhite = Color(0xFFF2F5F7)
val PearlWarm = Color(0xFFE8EDF0)
val PearlIridescentPink = Color(0xFFF2E4E9)
val PearlIridescentTeal = Color(0xFFEAE5EF)
val PearlIridescentLavender = Color(0xFFEAE7F3)
val PearlIridescentBlue = Color(0xFFEAE5EF)
val OnPearl = MatteCharcoal

val SilverLight = Color(0xFFD9E1E6)
val SilverMid = Color(0xFFA8B4BE)
val SilverDeep = Color(0xFF84919D)
val SilverShine = Color(0xFFF8FAFB)
val SilverMetal = Color(0xFFAFBAC4)

// Crimson is the primary action color; violet is the companion accent for private/media states.
val AuroraTeal = Color(0xFFE7284F)
val AuroraTealLight = Color(0xFFFF8197)
val AuroraTealDeep = Color(0xFFBD1737)
val AuroraTealContainer = Color(0xFF471421)
val OnAuroraTeal = MatteCharcoal

// Violet stays as a secondary cue for private browsing and secondary media actions.
val NebulaViolet = Color(0xFFAC52F2)
val NebulaVioletLight = Color(0xFFD19AFF)
val NebulaVioletDeep = Color(0xFF7529BC)
val NebulaVioletContainer = Color(0xFF301642)
val OnNebulaViolet = Color.White

// Legacy aliases kept for existing feature code and older resources.
val Gold = AuroraTeal
val Violet = NebulaViolet
val ElectricIndigo = NebulaVioletDeep

// Legacy mark aliases now follow the red-and-violet identity.
val OrbitGold = AuroraTeal
val OrbitGoldLight = AuroraTealLight
val OrbitBlue = NebulaViolet
val OrbitBlueLight = NebulaVioletLight

val BlushPink = Color(0xFFF05A78)
val BlushPinkLight = Color(0xFFFFA3B5)
val BlushPinkDeep = Color(0xFFB5324D)
val BlushPinkContainer = Color(0xFF451924)

val NebulaMist = Color(0xFF1B1422)
val NebulaEdge = Color(0xFF3E2B52)

// Surface micro-contrast, for fields and nested rows.
val SurfaceField = Color(0xFF100B15)
val SurfaceRow = Color(0xFF15101B)
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

val Error = Color(0xFFFF8197)
val OnError = Color.White

val MediaDetectedIndicator = AuroraTeal
val DownloadButtonActive = AuroraTeal
val DownloadButtonInactive = Color(0xFF3A2D46)
val FloatingButtonBackground = CharcoalElevated
val FloatingButtonForeground = PearlWhite

// Brand and surface gradients are deliberately low-contrast: decoration supports hierarchy instead
// of competing with the page or the action labels.
val PearlIridescentBrush = Brush.linearGradient(
    colors = listOf(PearlWhite, PearlIridescentLavender, PearlIridescentTeal, PearlWhite)
)

val SilverMetallicBrush = Brush.linearGradient(
    colors = listOf(SilverShine, SilverLight, SilverMetal, SilverLight, SilverShine)
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
    colors = listOf(Color(0xFF3A2D46), Color(0xFF261D2E))
)

val NebulaSurfaceBrush = Brush.verticalGradient(
    colors = listOf(CharcoalSurface, DeepCharcoal, MatteCharcoal)
)

val TopBarBrush = Brush.verticalGradient(
    colors = listOf(Color(0xFF160F1D), DeepCharcoal)
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
        AuroraTeal.copy(alpha = 0.16f),
        NebulaViolet.copy(alpha = 0.24f),
        AuroraTeal.copy(alpha = 0.16f)
    )
)

val NebulaBlushBrush = Brush.linearGradient(
    colors = listOf(NebulaViolet, BlushPink, NebulaVioletLight)
)

val AccentIndicatorBrush = Brush.horizontalGradient(
    colors = listOf(AuroraTealLight, AuroraTeal, AuroraTealDeep)
)

val DownloadCtaBrush = Brush.linearGradient(
    colors = listOf(AuroraTeal, AuroraTealDeep)
)

val FabActiveBrushNebula = Brush.radialGradient(
    colors = listOf(AuroraTealLight, AuroraTeal, AuroraTealDeep)
)

val FabInactiveBrushNebula = Brush.radialGradient(
    colors = listOf(Color(0xFF3A2D46), CharcoalElevated, DeepCharcoal)
)

val SplashNebulaBrush = Brush.radialGradient(
    colors = listOf(Color(0xFF251433), DeepCharcoal, MatteCharcoal),
    radius = 900f
)

val AuroraTealFieldFocus = AuroraTeal.copy(alpha = 0.82f)
val AuroraTealFieldRest = AuroraTeal.copy(alpha = 0.22f)
val AuroraTealSurface = AuroraTeal.copy(alpha = 0.10f)
val AuroraTealRing = AuroraTeal.copy(alpha = 0.40f)
val NebulaVioletSurface = NebulaViolet.copy(alpha = 0.10f)
val PearlFieldHint = SilverDeep.copy(alpha = 0.95f)
val EmptySurface = CharcoalSurface.copy(alpha = 0.72f)
