package com.musicstats.app.util

import android.content.Context
import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ExtractedPalette(
    val dominant: Int?,
    val vibrant: Int?,
    val muted: Int?,
    val darkVibrant: Int?,
    val darkMuted: Int?,
    val lightVibrant: Int?
)

@Singleton
class PaletteExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader
) {
    suspend fun extractFromUrl(url: String): ExtractedPalette? {
        return try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(Size(128, 128))
                .allowHardware(false)
                .build()
            val result = imageLoader.execute(request)
            val bitmap = result.image?.toBitmap() ?: return null
            val palette = Palette.from(bitmap).generate()
            ExtractedPalette(
                dominant = palette.dominantSwatch?.rgb,
                vibrant = palette.vibrantSwatch?.rgb,
                muted = palette.mutedSwatch?.rgb,
                darkVibrant = palette.darkVibrantSwatch?.rgb,
                darkMuted = palette.darkMutedSwatch?.rgb,
                lightVibrant = palette.lightVibrantSwatch?.rgb
            )
        } catch (e: Exception) {
            null
        }
    }
}
