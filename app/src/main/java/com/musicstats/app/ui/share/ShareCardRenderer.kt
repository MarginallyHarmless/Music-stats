package com.musicstats.app.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.io.File

object ShareCardRenderer {

    fun shareBitmap(context: Context, bitmap: Bitmap) {
        val file = File(context.cacheDir, "share_card.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share your stats"))
    }

    fun renderComposable(
        context: Context,
        width: Int,
        height: Int,
        content: @Composable () -> Unit,
        onBitmapReady: (Bitmap) -> Unit
    ) {
        val activity = context as? androidx.activity.ComponentActivity ?: return

        val composeView = ComposeView(context).apply {
            setContent { content() }
        }

        composeView.setViewTreeLifecycleOwner(activity)
        composeView.setViewTreeSavedStateRegistryOwner(activity)

        // Measure and layout off-screen
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)

        // Add to window temporarily (invisible)
        val decorView = activity.window.decorView as ViewGroup
        composeView.layoutParams = ViewGroup.LayoutParams(width, height)
        composeView.translationX = -width.toFloat() * 2 // off screen
        decorView.addView(composeView)

        composeView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                composeView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                composeView.draw(canvas)
                decorView.removeView(composeView)
                onBitmapReady(bitmap)
            }
        })
    }
}
