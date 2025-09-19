package com.shivam_raj.pdfstudio.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast

fun sharePdf(context: Context, pdfUri: Uri, pdfName: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, pdfUri)
        putExtra(Intent.EXTRA_SUBJECT, "Sharing PDF: $pdfName")
        putExtra(Intent.EXTRA_TEXT, "Here is the PDF document: $pdfName")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Important for content URIs
    }
    try {
        context.startActivity(Intent.createChooser(shareIntent, "Share PDF: $pdfName"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to share PDF.", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share PDF.", Toast.LENGTH_LONG).show()
        Log.e("SharePdf", "Error sharing PDF $pdfUri", e)
    }
}

fun openPdfWithIntent(context: Context, pdfUri: Uri) {
    val openIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(pdfUri, "application/pdf")
        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Crucial for content URIs
    }
    try {
        context.startActivity(openIntent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No PDF viewer app found.", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open PDF.", Toast.LENGTH_LONG).show()
        Log.e("OpenPdf", "Error opening PDF $pdfUri", e)
    }
}