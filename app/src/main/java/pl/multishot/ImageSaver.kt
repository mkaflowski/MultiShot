package pl.multishot

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.IOException

object ImageSaver {
    val RELATIVE_DIR = Environment.DIRECTORY_PICTURES + "/Screenshots"

    /**
     * Skleja obrazy pionowo w podanej kolejności (pierwszy na górze). Węższe obrazy są
     * wyśrodkowane, a puste pola – czarne.
     */
    fun stackVertically(bitmaps: List<Bitmap>): Bitmap {
        require(bitmaps.isNotEmpty())
        val width = bitmaps.maxOf { it.width }
        val height = bitmaps.sumOf { it.height }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        var y = 0
        for (b in bitmaps) {
            canvas.drawBitmap(b, ((width - b.width) / 2).toFloat(), y.toFloat(), null)
            y += b.height
        }
        return out
    }

    /** Zapisuje PNG do Pictures/Screenshots przez MediaStore. Zwraca Uri lub null. */
    fun savePng(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val resolver = context.contentResolver
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.Images.Media.DATE_TAKEN, now)
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            val stream = resolver.openOutputStream(uri) ?: throw IOException("Brak strumienia dla $uri")
            stream.use {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Kompresja PNG nieudana")
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: Exception) {
            Log.e("MultiShot", "Zapis zrzutu nieudany", e)
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }
}
