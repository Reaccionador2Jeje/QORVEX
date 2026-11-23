package dev.atdev.qorvex.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

data class InstallationMedia(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?
)

object InstallationMediaReader {
    fun describe(context: Context, uri: Uri): InstallationMedia {
        val resolver: ContentResolver = context.contentResolver
        var name = "Installation media"
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        return InstallationMedia(uri, name, size)
    }
}
