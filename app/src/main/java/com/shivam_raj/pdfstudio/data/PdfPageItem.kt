package com.shivam_raj.pdfstudio.data

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri

// This data class is primarily for IN-MEMORY representation in the EditorViewModel
data class PdfPageItem(
    val id: String, // Unique ID for this instance in the current editing session
    val originalFileUriString: String, // Store URI as String for easier DB storage
    val originalPageIndex: Int,
    @Transient var previewBitmap: Bitmap? = null, // Loaded on demand, not stored in DB directly
    val width: Int,
    val height: Int,
    var currentArrangementOrder: Int = 0 // Used for sorting in the editor
) {
    fun getOriginalFileUri(): Uri = originalFileUriString.toUri()
}