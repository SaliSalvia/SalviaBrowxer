package com.salvia.salviabrowxer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Salvia's dark canvas, mixed from the brand mark itself: the logo is a chip of deep space wrapped
 * in iridescent ribbon, so every surface below is a step of that same near-black indigo sky, and
 * every accent is a colour the ribbon, the starfield or the gold pins already wear. Decoration
 * stays low-contrast on purpose: it supports hierarchy instead of competing with the page.
 */
val MatteCharcoal = Color(0xFF000000)
val DeepCharcoal = Color(0xFF0A0716)
val CharcoalSurface = Color(0xFF130E24)
val CharcoalElevated = Color(0xFF1C1533)
val CharcoalBorder = Color(0xFF362A5E)

// Cool pearl foregrounds with the faint iridescent tint the ribbon leaves on light.
val PearlWhite = Color(0xFFF4F2FF)
val PearlWarm = Color(0xFFECEAF6)
val PearlIridescentPink = Color(0xFFF6E7F4)
val PearlIridescentTeal = Color(0xFFE6EDF6)
val PearlIridescentLavender = Color(0xFFECE8FA)
val PearlIridescentBlue = Color(0xFFE4ECF8)
val OnPearl = MatteCharcoal

val SilverLight = Color(0xFFDDE2F0)
val SilverMid = Color(0xFFA6ACC4)
val SilverDeep = Color(0xFF7F86A3)
val SilverShine = Color(0xFFFBFAFF)
val SilverMetal = Color(0xFFB4BAD0)

// Iris violet is the primary action color: the hue of the ribbon at its most saturated turn.
val IrisViolet = Color(0xFF8F6BFF)
val IrisVioletLight = Color(0xFFC3AEFF)
val IrisVioletDeep = Color(0xFF5A34D9)
val IrisVioletContainer = Color(0xFF2A1655)
val OnIrisViolet = MatteCharcoal

// Aurora cyan is the companion accent: the blue-white of the starfield inside the chip, kept for
// private browsing and secondary media cues.
val AuroraCyan = Color(0xFF4CC4FF)
val AuroraCyanLight = Color(0xFFA8E6FF)
val AuroraCyanDeep = Color(0xFF1786C4)
val AuroraCyanContainer = Color(0xFF0B2C44)
val OnAuroraCyan = MatteCharcoal

// Pin gold is the third voice of the mark: the connector pins and the signature. Used sparingly,
// for the moments that mean "saved" or "yours".
val PinGold = Color(0xFFD9AE54)
val PinGoldLight = Color(0xFFF2D795)
val PinGoldDeep = Color(0xFF9A7222)
val PinGoldContainer = Color(0xFF33260B)
val OnPinGold = MatteCharcoal

// Legacy aliases kept for existing feature code and older resources.
val Gold = PinGold
val Violet = IrisViolet
val ElectricIndigo = IrisVioletDeep

// Legacy mark aliases now follow the iris-and-cyan identity.
val OrbitGold = PinGold
val OrbitGoldLight = PinGoldLight
val OrbitBlue = AuroraCyan
val OrbitBlueLight = AuroraCyanLight

// The magenta sparkle the starfield scatters between its blue stars.
val BlushPink = Color(0xFFEF7BF0)
val BlushPinkLight = Color(0xFFFFB8FB)
val BlushPinkDeep = Color(0xFFB036B8)
val BlushPinkContainer = Color(0xFF3C1140)

val NebulaMist = Color(0xFF171129)
val NebulaEdge = Color(0xFF43307A)

// Surface micro-contrast, for fields and nested rows.
val SurfaceField = Color(0xFF0A0716)
val SurfaceRow = Color(0xFF100C1E)
val SurfaceInk = SilverMid

val Primary = IrisViolet
val PrimaryContainer = IrisVioletContainer
val OnPrimary = OnIrisViolet
val OnPrimaryContainer = IrisVioletLight

val Secondary = AuroraCyan
val SecondaryContainer = AuroraCyanContainer
val OnSecondary = OnAuroraCyan
val OnSecondaryContainer = AuroraCyanLight

val Background = MatteCharcoal
val OnBackground = PearlWhite
val Surface = DeepCharcoal
val OnSurface = PearlWhite
val SurfaceVariant = CharcoalSurface
val OnSurfaceVariant = SilverMid

val Error = Color(0xFFFF8AA1)
val OnError = Color.White

val MediaDetectedIndicator = IrisViolet
val DownloadButtonActive = IrisViolet
val DownloadButtonInactive = Color(0xFF362A5E)
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

val IrisBrush = Brush.linearGradient(
    colors = listOf(IrisVioletLight, IrisViolet, IrisVioletDeep)
)

val AuroraCyanBrush = Brush.linearGradient(
    colors = listOf(AuroraCyanLight, AuroraCyan, AuroraCyanDeep)
)

/** The three voices of the mark in one sweep: ribbon, starfield, pins. */
val LogoIridescenceBrush = Brush.linearGradient(
    colors = listOf(IrisViolet, AuroraCyan, PinGold)
)

val CharcoalPearlBrush = Brush.linearGradient(
    colors = listOf(MatteCharcoal, DeepCharcoal, CharcoalSurface)
)

val FabActiveBrush = Brush.radialGradient(
    colors = listOf(IrisVioletLight, IrisViolet, IrisVioletDeep)
)

val FabInactiveBrush = Brush.radialGradient(
    colors = listOf(CharcoalBorder, CharcoalElevated)
)

val NebulaSurfaceBrush = Brush.verticalGradient(
    colors = listOf(CharcoalSurface, DeepCharcoal, MatteCharcoal)
)

val TopBarBrush = Brush.verticalGradient(
    colors = listOf(Color(0xFF120C22), DeepCharcoal)
)

val BottomBarBrush = Brush.verticalGradient(
    colors = listOf(DeepCharcoal, MatteCharcoal)
)

val AddressBarBrush = Brush.linearGradient(
    colors = listOf(CharcoalSurface, DeepCharcoal)
)

/** A single quiet brand accent for separators and focus states: the ribbon's shimmer, edge on. */
val PearlEdgeBrush = Brush.horizontalGradient(
    colors = listOf(
        IrisViolet.copy(alpha = 0.18f),
        AuroraCyan.copy(alpha = 0.22f),
        PinGold.copy(alpha = 0.18f)
    )
)

val NebulaBlushBrush = Brush.linearGradient(
    colors = listOf(IrisViolet, BlushPink, AuroraCyanLight)
)

val AccentIndicatorBrush = Brush.horizontalGradient(
    colors = listOf(IrisVioletLight, IrisViolet, AuroraCyan)
)

// Deep enough that pearl-white labels keep a 4.5:1 ride across the whole sweep, vivid enough to
// still read as the ribbon: the download CTA and the dock's home ring share it.
val DownloadCtaBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF7A52F5), IrisVioletDeep, Color(0xFF45219F))
)

val FabActiveBrushNebula = Brush.radialGradient(
    colors = listOf(IrisVioletLight, IrisViolet, IrisVioletDeep)
)

val FabInactiveBrushNebula = Brush.radialGradient(
    colors = listOf(CharcoalBorder, CharcoalElevated, DeepCharcoal)
)

val SplashNebulaBrush = Brush.radialGradient(
    colors = listOf(Color(0xFF1A1038), DeepCharcoal, MatteCharcoal),
    radius = 900f
)

val IrisVioletFieldFocus = IrisViolet.copy(alpha = 0.82f)
val IrisVioletFieldRest = IrisViolet.copy(alpha = 0.22f)
val IrisVioletSurface = IrisViolet.copy(alpha = 0.10f)
val IrisVioletRing = IrisViolet.copy(alpha = 0.40f)
val AuroraCyanSurface = AuroraCyan.copy(alpha = 0.10f)
val PearlFieldHint = SilverDeep.copy(alpha = 0.95f)
val EmptySurface = CharcoalSurface.copy(alpha = 0.72f)
