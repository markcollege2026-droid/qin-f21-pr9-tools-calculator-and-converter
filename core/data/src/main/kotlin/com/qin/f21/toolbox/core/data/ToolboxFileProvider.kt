package com.qin.f21.toolbox.core.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/**
 * Minimal file provider used to share exported documents (history JSON / CSV, recordings)
 * with other apps. Implemented directly on the platform so the toolbox stays free of
 * third-party libraries.
 */
class ToolboxFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val file = resolve(uri) ?: return null
        file.parentFile?.mkdirs()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val file = resolve(uri) ?: return null
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val values = arrayOf<Any>(
            columns.map { column ->
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> file.name
                    OpenableColumns.SIZE -> file.length()
                    else -> ""
                }
            }
        )
        return MatrixCursor(columns, 1).apply { addRow(values) }
    }

    override fun getType(uri: Uri): String {
        val file = resolve(uri) ?: return "application/octet-stream"
        return when (file.extension.lowercase()) {
            "json" -> "application/json"
            "csv" -> "text/csv"
            "wav" -> "audio/wav"
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun resolve(uri: Uri): File? {
        val context = context ?: return null
        val name = uri.lastPathSegment ?: return null
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(base, name)
        // never serve anything outside the app specific directory
        if (!file.canonicalPath.startsWith(base.canonicalPath)) return null
        return file
    }
}
