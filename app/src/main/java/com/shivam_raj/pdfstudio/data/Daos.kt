package com.shivam_raj.pdfstudio.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkspace(workspace: Workspace): Long

    @Update
    suspend fun updateWorkspace(workspace: Workspace)

    @Delete
    suspend fun deleteWorkspace(workspace: Workspace)

    @Query("SELECT * FROM workspaces ORDER BY lastModified DESC")
    fun getAllWorkspaces(): Flow<List<Workspace>>

    @Query("SELECT * FROM workspaces WHERE id = :workspaceId")
    suspend fun getWorkspaceById(workspaceId: String): Workspace?

    @Transaction
    @Query("SELECT * FROM workspaces WHERE id = :workspaceId")
    fun getWorkspaceWithPages(workspaceId: String): Flow<WorkspaceWithPages?>
}

@Dao
interface PageArrangementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPageArrangements(pages: List<PageArrangement>)

    @Query("SELECT * FROM page_arrangements WHERE workspaceId = :workspaceId ORDER BY displayOrder ASC")
    fun getPagesForWorkspace(workspaceId: String): Flow<List<PageArrangement>>

    @Query("DELETE FROM page_arrangements WHERE workspaceId = :workspaceId")
    suspend fun deletePagesForWorkspace(workspaceId: String)

    @Transaction
    suspend fun replacePagesForWorkspace(workspaceId: String, newPages: List<PageArrangement>) {
        deletePagesForWorkspace(workspaceId)
        insertPageArrangements(newPages)
    }
}

@Dao
interface MergedPdfDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMergedPdf(mergedPdf: MergedPdf)

    @Delete
    suspend fun deleteMergedPdf(mergedPdf: MergedPdf)

    @Query("SELECT * FROM merged_pdfs ORDER BY createdAt DESC")
    fun getAllMergedPdfs(): Flow<List<MergedPdf>>
}

// Data class for combined query result
data class WorkspaceWithPages(
    @Embedded val workspace: Workspace,
    @Relation(
        parentColumn = "id",
        entityColumn = "workspaceId"
    )
    val pages: List<PageArrangement>
)

