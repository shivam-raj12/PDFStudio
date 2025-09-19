package com.shivam_raj.pdfstudio.ui.homescreen

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shivam_raj.pdfstudio.MainApplication
import com.shivam_raj.pdfstudio.data.MergedPdf
import com.shivam_raj.pdfstudio.data.Workspace
import com.shivam_raj.pdfstudio.repository.PdfRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import java.io.File

data class HomeUiState(
    val workspaces: List<Workspace> = emptyList(),
    val mergedPdfs: List<MergedPdf> = emptyList(),
    val isLoading: Boolean = true // Initially loading
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: PdfRepository = (application as MainApplication).repository

    val uiState: StateFlow<HomeUiState> =
        repository.getAllWorkspaces().combine(repository.getAllMergedPdfs()) { workspaces, mergedPdfs ->
            HomeUiState(workspaces, mergedPdfs, isLoading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState(isLoading = true)
        )

    fun createNewWorkspace(name: String = "New Workspace", onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val newWorkspaceId = repository.createNewWorkspace(name)
            onCreated(newWorkspaceId)
        }
    }

    fun deleteWorkspace(workspace: Workspace) {
        viewModelScope.launch {
            repository.deleteWorkspace(workspace)
        }
    }
    fun renameWorkspace(workspace: Workspace, newName: String) {
        viewModelScope.launch {
            repository.updateWorkspace(workspace.copy(name = newName, lastModified = System.currentTimeMillis()))
        }
    }


    // In HomeViewModel.kt
    fun deleteMergedPdf(mergedPdf: MergedPdf) {
        viewModelScope.launch {
            // 1. Delete the file from the system
            try {
                val pdfUri = mergedPdf.filePathUriString.toUri()
                // For content URIs (especially from MediaStore), use contentResolver.delete
                // For file URIs (older Android versions), you might construct a File object.
                // This assumes pdfUri is a MediaStore content URI primarily.
                val deletedRows = getApplication<Application>().contentResolver.delete(pdfUri, null, null)
                if (deletedRows > 0) {
                    Log.d("HomeVM", "Successfully deleted file from system: ${mergedPdf.filePathUriString}")
                } else {
                    // If contentResolver.delete didn't work (e.g., it was a file URI from older SDK)
                    // or if the URI was invalid/already deleted from system.
                    // Try deleting as a direct file path if it's a file URI.
                    if ("file" == pdfUri.scheme) {
                        pdfUri.path?.let { File(it).delete() }
                    }
                    Log.w("HomeVM", "File system delete might have failed or file already gone for: ${mergedPdf.filePathUriString}")
                }
            } catch (e: SecurityException) {
                // This can happen if you don't have permission to delete the file,
                // e.g. if the file was created by another app or if MediaStore rules prevent it.
                // Or if trying to delete a file URI without direct file permissions.
                Log.e("HomeVM", "SecurityException deleting file: ${mergedPdf.filePathUriString}", e)
                // Proceed to delete from DB anyway, or show error? For now, we proceed.
            } catch (e: Exception) {
                Log.e("HomeVM", "Error deleting file from system: ${mergedPdf.filePathUriString}", e)
                // Proceed to delete from DB, as the file might already be gone.
            }

            // 2. Delete the record from the database
            repository.deleteMergedPdf(mergedPdf)
        }
    }


    // In HomeViewModel.kt
    fun createWorkspaceFromExisting(sourceWorkspaceId: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val sourcePages = repository.getPagesForWorkspace(sourceWorkspaceId).firstOrNull()
            if (sourcePages.isNullOrEmpty()) {
                // Handle case where source workspace has no pages or doesn't exist
                // Fallback to a new empty workspace
                val newEmptyId = repository.createNewWorkspace("Re-edit Workspace (Source Missing)")
                onCreated(newEmptyId)
                return@launch
            }

            val sourceWorkspaceName = repository.getWorkspaceById(sourceWorkspaceId)?.name ?: "Workspace"
            val newClonedWorkspaceId = repository.createNewWorkspace("Copy of $sourceWorkspaceName")

            val newArrangements = sourcePages.map { pa ->
                pa.copy(workspaceId = newClonedWorkspaceId, id = 0) // Reset id for new entry, assign new wsId
            }
            repository.savePageArrangementForWorkspace(newClonedWorkspaceId, newArrangements)
            onCreated(newClonedWorkspaceId)
        }
    }
}

// Helper for Flow.combine with two flows
fun <T1, T2, R> Flow<T1>.combine(
    flow2: Flow<T2>,
    transform: suspend (T1, T2) -> R
): Flow<R> = combine(this, flow2, transform)


