package com.salvia.salviabrowxer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Salvia's dark canvas: cool graphite instead of pure black, with enough separation between
 * page, chrome and controls to make the interface feel calm without losing contrast.
 */
val MatteCharcoal = Color(0xFF0B1015)
val DeepCharcoal = Color(0xFF121920)
val CharcoalSurface = Color(0xFF19222C)
val CharcoalElevated = Color(0xFF202B36)
val CharcoalBorder = Color(0xFF2B3743)

// Warm, neutral foregrounds keep long sessions comfortable; muted text remains legible on graphite.
val PearlWhite = Color(0xFFF2F5F7)
val PearlWarm = Color(0xFFE8EDF0)
val PearlIridescentPink = Color(0xFFF2E4E9)
val PearlIridescentTeal = Color(0xFFDCEFEA)
val PearlIridescentLavender = Color(0xFFEAE7F3)
val PearlIridescentBlue = Color(0xFFE0EAF1)
val OnPearl = MatteCharcoal

val SilverLight = Color(0xFFD9E1E6)
val SilverMid = Color(0xFFA8B4BE)
val SilverDeep = Color(0xFF84919D)
val SilverShine = Color(0xFFF8FAFB)
val SilverMetal = Color(0xFFAFBAC4)

// Primary: a restrained sage/teal. One clear action color replaces the old multi-neon treatment.
val AuroraTeal = Color(0xFF5DD7C3)
val AuroraTealLight = Color(0xFFA2EBDD)
val AuroraTealDeep = Color(0xFF238A7B)
val AuroraTealContainer = Color(0xFF193B37)
val OnAuroraTeal = MatteCharcoal

// Violet stays as a secondary cue for private browsing and secondary media actions.
val NebulaViolet = Color(0xFF9A83E9)
val NebulaVioletLight = Color(0xFFC3B4F8)
val NebulaVioletDeep = Color(0xFF6852AF)
val NebulaVioletContainer = Color(0xFF2B2442)
val OnNebulaViolet = Color.White

// Legacy aliases kept for existing feature code and older resources.
val Gold = AuroraTeal
val Violet = NebulaViolet
val ElectricIndigo = NebulaVioletDeep

// Orbital mark colors: understated blue and warm brass, used only in the brand mark.
val OrbitGold = Color(0xFFE7B968)
val OrbitGoldLight = Color(0xFFF4D18D)
val OrbitBlue = Color(0xFF4F9CD7)
val OrbitBlueLight = Color(0xFF91C7EF)

val BlushPink = Color(0xFFD28EA7)
val BlushPinkLight = Color(0xFFE9B8C7)
val BlushPinkDeep = Color(0xFFAD607D)
val BlushPinkContainer = Color(0xFF38212A)

val NebulaMist = Color(0xFF201D2A)
val NebulaEdge = Color(0xFF38314B)

// Surface micro-contrast, for fields and nested rows.
val SurfaceField = Color(0xFF131B23)
val SurfaceRow = Color(0xFF161E27)
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

val Error = Color(0xFFFF7777)
val OnError = Color.White

val MediaDetectedIndicator = AuroraTeal
val DownloadButtonActive = AuroraTeal
val DownloadButtonInactive = Color(0xFF34414C)
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
    colors = listOf(Color(0xFF35424D), Color(0xFF202B36))
)

val NebulaSurfaceBrush = Brush.verticalGradient(
    colors = listOf(CharcoalSurface, DeepCharcoal, MatteCharcoal)
)

val TopBarBrush = Brush.verticalGradient(
    colors = listOf(Color(0xFF151E27), DeepCharcoal)
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
    colors = listOf(NebulaViolet, BlushPink, OrbitBlueLight)
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
    colors = listOf(Color(0xFF35424D), CharcoalElevated, DeepCharcoal)
)

val SplashNebulaBrush = Brush.radialGradient(
    colors = listOf(Color(0xFF17262C), DeepCharcoal, MatteCharcoal),
    radius = 900f
)

val AuroraTealFieldFocus = AuroraTeal.copy(alpha = 0.82f)
val AuroraTealFieldRest = AuroraTeal.copy(alpha = 0.22f)
val AuroraTealSurface = AuroraTeal.copy(alpha = 0.10f)
val AuroraTealRing = AuroraTeal.copy(alpha = 0.40f)
val NebulaVioletSurface = NebulaViolet.copy(alpha = 0.10f)
val PearlFieldHint = SilverDeep.copy(alpha = 0.95f)
val EmptySurface = CharcoalSurface.copy(alpha = 0.72f)
