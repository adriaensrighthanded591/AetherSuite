package com.aethersms.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import com.aethersms.mms.pdu.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Envoi MMS avec pièces jointes (images pleine qualité, PDF, etc.)
 * Utilise des classes PDU vendorisées — aucune dépendance AOSP privée.
 */
class MmsSender(private val context: Context) {

    companion object {
        const val MAX_MMS_BYTES   = 1_400_000L
        const val THRESHOLD_LOW   =   300_000L
        const val THRESHOLD_HIGH  = 1_400_000L
    }

    suspend fun sendMms(
        addresses:      List<String>,
        body:           String,
        attachmentUris: List<Uri>,
        subId:          Int = -1,
    ) = withContext(Dispatchers.IO) {
        val req = SendReq()
        addresses.forEach { req.addTo(it) }

        val pduBody = PduBody()

        if (body.isNotBlank()) {
            pduBody.addPart(PduPart().apply {
                setContentType("text/plain")
                setContentId("<text>")
                setName("text.txt")
                charset = 0x6A
                setData(body.toByteArray(Charsets.UTF_8))
            })
        }

        attachmentUris.forEach { uri ->
            val mime      = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val fileName  = getDisplayName(uri) ?: buildDefaultName(mime)
            val rawBytes  = readUri(uri) ?: return@forEach
            val finalBytes = if (mime.startsWith("image/")) compressImage(rawBytes, mime, rawBytes.size.toLong())
                             else rawBytes
            pduBody.addPart(PduPart().apply {
                setContentType(mime)
                setContentId("<${fileName.replace(" ", "_")}>")
                setName(fileName)
                setData(finalBytes)
            })
        }

        req.body = pduBody

        val pduBytes = PduComposer(context, req).make()
            ?: throw IllegalStateException("Impossible d'encoder le PDU MMS")

        val tmpFile = File(context.cacheDir, "mms_${System.currentTimeMillis()}.pdu")
        FileOutputStream(tmpFile).use { it.write(pduBytes) }
        val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tmpFile)

        getSmsManager(subId).sendMultimediaMessage(context, fileUri, null, null, null)
        tmpFile.deleteOnExit()
    }

    @Suppress("DEPRECATION")
    private fun getSmsManager(subId: Int): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val s = context.getSystemService(SmsManager::class.java)
            if (subId == -1) s else s.createForSubscriptionId(subId)
        } else {
            if (subId == -1) SmsManager.getDefault()
            else SmsManager.getSmsManagerForSubscriptionId(subId)
        }

    private fun readUri(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()

    private fun getDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    private fun buildDefaultName(mime: String) = when {
        mime.startsWith("image/jpeg") -> "photo.jpg"
        mime.startsWith("image/png")  -> "photo.png"
        mime.startsWith("video/")     -> "video.mp4"
        mime.startsWith("audio/")     -> "audio.m4a"
        mime == "application/pdf"     -> "document.pdf"
        else -> "file"
    }

    private fun compressImage(bytes: ByteArray, mime: String, originalSize: Long): ByteArray {
        if (originalSize <= THRESHOLD_LOW) return bytes
        return runCatching {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
            val quality = when {
                originalSize <= 800_000L -> 90
                originalSize <= THRESHOLD_HIGH -> 80
                else -> 70
            }
            val resized = if (bitmap.width > 2048 || bitmap.height > 2048) {
                val scale = 2048f / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else bitmap

            val format = when {
                mime.contains("png") -> Bitmap.CompressFormat.PNG
                else -> Bitmap.CompressFormat.JPEG
            }
            val out = ByteArrayOutputStream()
            resized.compress(format, quality, out)
            val compressed = out.toByteArray()
            if (compressed.size >= bytes.size) bytes else compressed
        }.getOrDefault(bytes)
    }
}
