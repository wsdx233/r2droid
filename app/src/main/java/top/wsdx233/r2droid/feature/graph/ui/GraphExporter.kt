package top.wsdx233.r2droid.feature.graph.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.GraphData
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object GraphExporter {

    fun getSanitizedGraphTitle(graphData: GraphData, fallback: String = "flowchart"): String {
        val title = graphData.title.ifBlank {
            graphData.nodes.firstOrNull()?.title?.ifBlank { null }
        } ?: fallback
        return title.replace(Regex("[^a-zA-Z0-9_.-]"), "_").take(40).trim('_').ifBlank { fallback }
    }

    fun renderGraphToBitmap(
        layoutResult: GraphLayoutResult,
        graphBounds: Rect,
        structuredNodeLayout: Boolean,
        density: Float
    ): Bitmap? {
        if (layoutResult.nodes.isEmpty() || graphBounds.width <= 0f || graphBounds.height <= 0f) return null
        val maxDim = 4096f
        var targetScale = min(2.0f, min(maxDim / graphBounds.width, maxDim / graphBounds.height)).coerceAtLeast(0.02f)

        for (attempt in 0..2) {
            try {
                val bmpWidth = (graphBounds.width * targetScale).toInt().coerceIn(1, 4096)
                val bmpHeight = (graphBounds.height * targetScale).toInt().coerceIn(1, 4096)
                val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                val androidCanvas = android.graphics.Canvas(bitmap)
                val composeCanvas = androidx.compose.ui.graphics.Canvas(androidCanvas)
                val canvasDrawScope = CanvasDrawScope()

                val textPaint = android.graphics.Paint().apply {
                    color = "#D4D4D4".toColorInt()
                    textSize = 14f
                    typeface = android.graphics.Typeface.MONOSPACE
                    isAntiAlias = true
                }
                val titlePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 15f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                    isAntiAlias = true
                }
                val addrPaint = android.graphics.Paint().apply {
                    color = "#888888".toColorInt()
                    textSize = 13f
                    typeface = android.graphics.Typeface.MONOSPACE
                    isAntiAlias = true
                }

                canvasDrawScope.draw(
                    density = Density(density),
                    layoutDirection = LayoutDirection.Ltr,
                    canvas = composeCanvas,
                    size = Size(bmpWidth.toFloat(), bmpHeight.toFloat())
                ) {
                    drawRect(Color(0xFF161616))

                    withTransform({
                        translate(-graphBounds.left * targetScale, -graphBounds.top * targetScale)
                        scale(targetScale, targetScale, Offset.Zero)
                    }) {
                        val fullRect = Rect(
                            graphBounds.left - 1000f,
                            graphBounds.top - 1000f,
                            graphBounds.right + 1000f,
                            graphBounds.bottom + 1000f
                        )
                        drawRoutedEdges(layoutResult.edges, fullRect)
                        for (ln in layoutResult.nodes) {
                            drawNode(
                                ln = ln,
                                textPaint = textPaint,
                                titlePaint = titlePaint,
                                addrPaint = addrPaint,
                                density = density,
                                structuredNodeLayout = structuredNodeLayout,
                                isHighlighted = false,
                                cursorAddress = 0L
                            )
                        }
                    }
                }
                return bitmap
            } catch (oom: OutOfMemoryError) {
                targetScale *= 0.5f
                if (attempt == 2) return null
            } catch (e: Throwable) {
                return null
            }
        }
        return null
    }

    suspend fun saveGraphToGallery(
        context: Context,
        bitmap: Bitmap,
        baseName: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "${baseName}_${System.currentTimeMillis()}.png"
            val resolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/R2Droid")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IllegalStateException("Failed to create MediaStore entry")
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                            throw IllegalStateException("Failed to compress bitmap")
                        }
                    } ?: throw IllegalStateException("Failed to open output stream")
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                } catch (e: Throwable) {
                    runCatching { resolver.delete(uri, null, null) }
                    throw e
                }
                uri
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "R2Droid").apply { mkdirs() }
                val file = File(appDir, fileName)
                FileOutputStream(file).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw IllegalStateException("Failed to compress bitmap")
                    }
                }
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
                Uri.fromFile(file)
            }
        }
    }

    suspend fun shareGraph(
        context: Context,
        bitmap: Bitmap,
        baseName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "${baseName}_${System.currentTimeMillis()}.png"
            val cacheDir = File(context.cacheDir, "shared_graphs").apply { mkdirs() }
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw IllegalStateException("Failed to compress bitmap")
                }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, context.getString(R.string.graph_share_title)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(chooser)
            }
        }
    }
}
