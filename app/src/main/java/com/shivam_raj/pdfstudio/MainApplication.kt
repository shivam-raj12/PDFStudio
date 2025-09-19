package com.shivam_raj.pdfstudio

import android.app.Application
import com.shivam_raj.pdfstudio.data.AppDatabase
import com.shivam_raj.pdfstudio.repository.PdfRepository

class MainApplication : Application() {
    // Using by lazy so the database and repository are only created when they're needed
    // rather than when the application starts
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { PdfRepository(database) }
}