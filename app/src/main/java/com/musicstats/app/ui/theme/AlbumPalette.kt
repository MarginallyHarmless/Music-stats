package com.musicstats.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.musicstats.app.data.model.Song

data class AlbumPalette(
    val dominant: Color = Color(0xFF8A8A95),
    val vibrant: Color = Color(0xFF9A9AA5),
    val muted: Color = Color(0xFF5A5A65),
    val darkVibrant: Color = Color(0xFF3A3A45),
    val darkMuted: Color = Color(0xFF2A2A35),
    val lightVibrant: Color = Color(0xFFB0B0BB)
) {
    val accent: Color
        get() = if (vibrant != Color(0xFF9A9AA5)) vibrant else dominant

    val surfaceTint: Color
        get() = accent.copy(alpha = 0.08f)

    val glowPrimary: Color
        get() = if (vibrant != Color(0xFF9A9AA5)) vibrant else dominant

    val glowSecondary: Color
        get() = if (muted != Color(0xFF5A5A65)) muted else darkVibrant

    val glassBackground: Color
        get() = Color.White.copy(alpha = 0.06f)

    val glassBorder: Color
        get() = Color.White.copy(alpha = 0.08f)
}

val LocalAlbumPalette = compositionLocalOf { AlbumPalette() }

fun Song.toAlbumPalette(): AlbumPalette {
    if (paletteDominant == null && paletteVibrant == null) return AlbumPalette()
    return AlbumPalette(
        dominant = paletteDominant?.let { Color(it) } ?: AlbumPalette().dominant,
        vibrant = paletteVibrant?.let { Color(it) } ?: AlbumPalette().vibrant,
        muted = paletteMuted?.let { Color(it) } ?: AlbumPalette().muted,
        darkVibrant = paletteDarkVibrant?.let { Color(it) } ?: AlbumPalette().darkVibrant,
        darkMuted = paletteDarkMuted?.let { Color(it) } ?: AlbumPalette().darkMuted,
        lightVibrant = paletteLightVibrant?.let { Color(it) } ?: AlbumPalette().lightVibrant
    )
}
