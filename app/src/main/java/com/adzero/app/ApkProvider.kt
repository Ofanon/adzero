package com.adzero.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/**
 * Hands AdZero's own installer file to whatever app the user picked to send it.
 *
 * Android has refused plain file:// links between apps since Nougat, so the
 * file has to arrive as a content:// URI from a provider. The usual answer is
 * androidx's FileProvider, and pulling in the whole of androidx.core to serve
 * one file would be the only dependency in the project. It is about sixty
 * lines to do properly, so it is done properly here.
 *
 * Not exported: nothing can reach it except the app the user chose in the
 * share sheet, and only for as long as that one grant lasts.
 */
class ApkProvider : ContentProvider() {

    companion object {
        private const val AUTHORITY = "com.adzero.app.share"
        const val APK_MIME = "application/vnd.android.package-archive"

        /** Where the copy lives. Its own folder, so nothing else is reachable. */
        fun shareDir(ctx: Context): File =
            File(ctx.cacheDir, "share").apply { mkdirs() }

        fun uriFor(file: File): Uri =
            Uri.parse("content://$AUTHORITY/" + Uri.encode(file.name))
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String =
        // It serves two kinds of file now: the installer, and the stats card.
        // Answering "package archive" for a PNG would make every share target
        // refuse it, or offer to install a picture.
        if (uri.lastPathSegment?.endsWith(".png") == true) "image/png" else APK_MIME

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val file = resolve(uri) ?: return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /**
     * Share targets ask for the name and the size before they will show the
     * attachment. Answering nothing here is why a hand-rolled provider usually
     * ends up looking broken in the share sheet.
     */
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val file = resolve(uri) ?: return null
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns)
        cursor.addRow(columns.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> file.name
                OpenableColumns.SIZE -> file.length()
                else -> null
            }
        }.toTypedArray())
        return cursor
    }

    /**
     * Resolves a URI to a file, refusing anything that is not directly inside
     * the share folder. Without the canonical-path check, a name containing
     * ".." would walk out of it and serve any file the app can read.
     */
    private fun resolve(uri: Uri): File? {
        val ctx = context ?: return null
        val name = uri.lastPathSegment ?: return null
        val dir = shareDir(ctx).canonicalFile
        val file = File(dir, name).canonicalFile
        if (file.parentFile != dir || !file.isFile) return null
        return file
    }

    // Read-only by design: nothing outside this app has any business writing here.
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?,
    ): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
