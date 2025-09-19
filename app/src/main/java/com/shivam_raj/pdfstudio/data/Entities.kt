package com.shivam_raj.pdfstudio.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "workspaces")
data class Workspace(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    var name: String = "New Workspace", // User can rename
    val createdAt: Long = System.currentTimeMillis(),
    var lastModified: Long = System.currentTimeMillis()
)

// Represents a single page within a workspace's arrangement
@Entity(
    tableName = "page_arrangements",
    foreignKeys = [ForeignKey(
        entity = Workspace::class,
        parentColumns = ["id"],
        childColumns = ["workspaceId"],
        onDelete = ForeignKey.CASCADE // If workspace is deleted, its pages are deleted
    )],
    indices = [Index(value = ["workspaceId"])]
)
data class PageArrangement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workspaceId: String,
    val originalFileUriString: String, // URI of the source PDF
    val originalPageIndex: Int,        // Page index in the source PDF
    val displayOrder: Int,             // Order of this page in the workspace
    val originalWidth: Int,
    val originalHeight: Int
)

@Entity(tableName = "merged_pdfs")
data class MergedPdf(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String, // Filename of the merged PDF
    val filePathUriString: String, // URI string pointing to the saved merged PDF
    val createdAt: Long = System.currentTimeMillis(),
    val sourceWorkspaceId: String? = null // Optional: link to the workspace it was created from
)

