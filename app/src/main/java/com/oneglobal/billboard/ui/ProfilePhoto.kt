package com.oneglobal.billboard.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oneglobal.billboard.BuildConfig
import com.oneglobal.billboard.model.OneOwner
import com.oneglobal.billboard.ui.theme.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

private val photos = object : LruCache<String, Bitmap>(4 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
}

fun OneOwner.locationLabel(): String {
    return countryLabel(countryCode.uppercase(java.util.Locale.ROOT))
}

/** Decode with a size cap before allocation, respecting orientation and stripping metadata. */
suspend fun prepareProfilePhoto(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    val bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val scale = 384f / maxOf(info.size.width, info.size.height)
            decoder.setTargetSize(maxOf(1, (info.size.width * minOf(1f, scale)).toInt()), maxOf(1, (info.size.height * minOf(1f, scale)).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 512) inSampleSize *= 2
        }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("This photo could not be opened.")
    }
    try {
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val resized = Bitmap.createScaledBitmap(square, 256, 256, true)
        val bytes = ByteArrayOutputStream().use { out -> resized.compress(Bitmap.CompressFormat.JPEG, 82, out); out.toByteArray() }
        if (resized !== bitmap && resized !== square) resized.recycle()
        if (square !== bitmap) square.recycle()
        require(bytes.size <= 75_000) { "This photo is too detailed. Try another photo." }
        bytes
    } finally { bitmap.recycle() }
}

@Composable
fun ProfilePhoto(owner: OneOwner?, accent: Color, modifier: Modifier = Modifier) {
    val key = "${owner?.id}:${owner?.photoVersion}"
    val bitmap by produceState<Bitmap?>(photos.get(key), key) {
        if (owner?.photoVersion == null) { value = null; return@produceState }
        value = withContext(Dispatchers.IO) {
            photos.get(key) ?: runCatching {
                val connection = URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/rpc/get_profile_photo").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 8_000
                    connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    connection.setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(JSONObject().put("p_user_id", owner.id).toString().toByteArray()) }
                    check(connection.responseCode == 200)
                    val payload = connection.inputStream.bufferedReader().use { it.readText() }
                    check(payload.length <= 110_000)
                    val photo = JSONObject(payload).optString("photo")
                    val bytes = Base64.decode(photo, Base64.DEFAULT)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    check(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 512) inSampleSize *= 2
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.also { photos.put(key, it) }
                } finally { connection.disconnect() }
            }.getOrNull()
        }
    }
    Box(modifier.clip(CircleShape).background(Ink).border(1.dp, accent, CircleShape), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = "${owner?.handle} profile photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(owner?.initials ?: "1", color = accent, fontWeight = FontWeight.Black, fontSize = 20.sp)
    }
}
