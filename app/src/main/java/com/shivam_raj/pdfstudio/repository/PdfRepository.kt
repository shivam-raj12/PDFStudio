package com.shivam_raj.pdfstudio.repository

import com.shivam_raj.pdfstudio.data.*
import kotlinx.coroutines.flow.Flow

class PdfRepository(private val database: AppDatabase) {

    // Workspace methods
    fun getAllWorkspaces(): Flow<List<Workspace>> = database.workspaceDao().getAllWorkspaces()
    suspend fun getWorkspaceById(workspaceId: String): Workspace? = database.workspaceDao().getWorkspaceById(workspaceId)
    suspend fun createNewWorkspace(name: String = "New Workspace"): String {
        val workspace = Workspace(name = name)
        database.workspaceDao().insertWorkspace(workspace)
        return workspace.id
    }
    suspend fun updateWorkspace(workspace: Workspace) = database.workspaceDao().updateWorkspace(workspace)
    suspend fun deleteWorkspace(workspace: Workspace) = database.workspaceDao().deleteWorkspace(workspace) // Pages deleted by cascade

    // Page Arrangement methods
    fun getPagesForWorkspace(workspaceId: String): Flow<List<PageArrangement>> =
        database.pageArrangementDao().getPagesForWorkspace(workspaceId)

    suspend fun savePageArrangementForWorkspace(workspaceId: String, pages: List<PageArrangement>) {
        database.pageArrangementDao().replacePagesForWorkspace(workspaceId, pages)
        // Update workspace lastModified timestamp
        database.workspaceDao().getWorkspaceById(workspaceId)?.let {
            database.workspaceDao().updateWorkspace(it.copy(lastModified = System.currentTimeMillis()))
        }
    }

    // Merged PDF methods
    fun getAllMergedPdfs(): Flow<List<MergedPdf>> = database.mergedPdfDao().getAllMergedPdfs()
    suspend fun addMergedPdf(mergedPdf: MergedPdf) = database.mergedPdfDao().insertMergedPdf(mergedPdf)
    suspend fun deleteMergedPdf(mergedPdf: MergedPdf) = database.mergedPdfDao().deleteMergedPdf(mergedPdf)
}
