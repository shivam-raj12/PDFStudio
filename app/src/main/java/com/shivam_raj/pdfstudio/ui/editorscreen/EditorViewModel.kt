package com.shivam_raj.pdfstudio.ui.editorscreen // Ensure this matches your package

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer // Still needed for previews
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.core.graphics.createBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.shivam_raj.pdfstudio.MainApplication
import com.shivam_raj.pdfstudio.data.*
import com.shivam_raj.pdfstudio.repository.PdfRepository
import com.shivam_raj.pdfstudio.navigation.AppDestinations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID

data class EditorUiState(
    val workspaceId: String? = null,
    val workspaceName: String = "New Workspace",
    val pages: List<PdfPageItem> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val processingMessage: String? = null,
    val snackbarMessage: String? = null
)

class EditorViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository: PdfRepository = (application as MainApplication).repository
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _editorPageItems = mutableStateListOf<PdfPageItem>()
    val editorPageItems: List<PdfPageItem> get() = _editorPageItems

    private var currentWorkspaceId: String? = savedStateHandle[AppDestinations.WORKSPACE_ID_ARG]
    private var isNewWorkspace = currentWorkspaceId == null || currentWorkspaceId == "new"

    private var pageLoadingJob: Job? = null

    private val _navigateToHomeEvent = MutableSharedFlow<Unit>()
    val navigateToHomeEvent: SharedFlow<Unit> = _navigateToHomeEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            if (isNewWorkspace) {
                val newId = repository.createNewWorkspace()
                currentWorkspaceId = newId
                _uiState.update { it.copy(workspaceId = newId, workspaceName = "New Workspace", isLoading = false) }
                savedStateHandle[AppDestinations.WORKSPACE_ID_ARG] = newId
            } else {
                currentWorkspaceId?.let { wsId ->
                    repository.getWorkspaceById(wsId)?.let { workspace ->
                        _uiState.update { it.copy(workspaceId = wsId, workspaceName = workspace.name) }
                        loadPagesForWorkspace(wsId)
                    } ?: run {
                        val newId = repository.createNewWorkspace("Workspace (Restored)")
                        currentWorkspaceId = newId
                        _uiState.update {
                            it.copy(
                                workspaceId = newId,
                                workspaceName = "Workspace (Restored)",
                                isLoading = false,
                                snackbarMessage = "Original workspace not found. Created new."
                            )
                        }
                        savedStateHandle[AppDestinations.WORKSPACE_ID_ARG] = newId
                    }
                }
            }
        }
    }

    private fun loadPagesForWorkspace(workspaceId: String) {
        pageLoadingJob?.cancel()
        pageLoadingJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getPagesForWorkspace(workspaceId).collectLatest { arrangements ->
                val newPageItems = arrangements.map { pa ->
                    val editorItemId = "pa_${pa.id}" // STABLE ID
                    val existingEditorItemInMemory = _editorPageItems.find { it.id == editorItemId }
                    PdfPageItem(
                        id = editorItemId,
                        originalFileUriString = pa.originalFileUriString,
                        originalPageIndex = pa.originalPageIndex,
                        previewBitmap = existingEditorItemInMemory?.previewBitmap,
                        width = pa.originalWidth,
                        height = pa.originalHeight,
                        currentArrangementOrder = pa.displayOrder
                    )
                }
                _editorPageItems.clear()
                _editorPageItems.addAll(newPageItems.sortedBy { it.currentArrangementOrder })
                _uiState.update { it.copy(pages = _editorPageItems.toList(), isLoading = false) }
            }
        }
    }

    fun addPdfsToWorkspace(uris: List<Uri>) {
        val wsId = currentWorkspaceId ?: return
        _uiState.update { it.copy(isLoading = true, processingMessage = "Adding PDFs...") }
        viewModelScope.launch(Dispatchers.IO) {
            val newArrangements = mutableListOf<PageArrangement>()
            var currentMaxOrder = _editorPageItems.maxOfOrNull { it.currentArrangementOrder } ?: -1

            uris.forEach { uri ->
                try {
                    getApplication<Application>().applicationContext.contentResolver
                        .openFileDescriptor(uri, "r")?.use { pfd ->
                            PdfRenderer(pfd).use { renderer ->
                                for (i in 0 until renderer.pageCount) {
                                    renderer.openPage(i).use { page ->
                                        currentMaxOrder++
                                        newArrangements.add(
                                            PageArrangement(
                                                workspaceId = wsId,
                                                originalFileUriString = uri.toString(),
                                                originalPageIndex = i,
                                                displayOrder = currentMaxOrder,
                                                originalWidth = page.width,
                                                originalHeight = page.height
                                            )
                                        )
                                    }
                                }
                            }
                        }
                } catch (e: Exception) {
                    Log.e("EditorVM", "Error loading PDF ${uri.lastPathSegment} during add", e)
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(snackbarMessage = "Error adding ${uri.lastPathSegment}") }
                    }
                }
            }

            if (newArrangements.isNotEmpty()) {
                val existingArrangements = repository.getPagesForWorkspace(wsId).first()
                val allArrangementsToSave = existingArrangements + newArrangements
                repository.savePageArrangementForWorkspace(wsId, allArrangementsToSave)
                // The flow in loadPagesForWorkspace will pick this up.
                // For faster UI update:
                withContext(Dispatchers.Main) {
                    val updatedEditorItems = allArrangementsToSave
                        .sortedBy { it.displayOrder }
                        .map { pa ->
                            val editorItemId = "pa_${pa.id} + ${UUID.randomUUID()}"
                            _editorPageItems.find { it.id == editorItemId }?.copy(currentArrangementOrder = pa.displayOrder) ?:
                            PdfPageItem(
                                id = editorItemId, // Use stable ID if available from DB, else temp for newly added
                                originalFileUriString = pa.originalFileUriString,
                                originalPageIndex = pa.originalPageIndex,
                                width = pa.originalWidth,
                                height = pa.originalHeight,
                                currentArrangementOrder = pa.displayOrder
                            )
                        }
                    _editorPageItems.clear()
                    _editorPageItems.addAll(updatedEditorItems)
                    _uiState.update { it.copy(pages = _editorPageItems.toList())}
                }
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isLoading = false, processingMessage = null) }
            }
        }
    }

    fun reorderPages(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex < 0 || toIndex < 0 || fromIndex >= _editorPageItems.size || toIndex >= _editorPageItems.size) return
        val item = _editorPageItems.removeAt(fromIndex)
        _editorPageItems.add(toIndex, item)
        updatePageOrderAndPersist()
    }

    fun removePage(pageItem: PdfPageItem) {
        _editorPageItems.remove(pageItem)
        updatePageOrderAndPersist()
    }

    private fun updatePageOrderAndPersist() {
        val wsId = currentWorkspaceId ?: return
        val updatedArrangements = _editorPageItems.mapIndexed { index, item ->
            item.currentArrangementOrder = index
            PageArrangement(
                workspaceId = wsId,
                originalFileUriString = item.originalFileUriString,
                originalPageIndex = item.originalPageIndex,
                displayOrder = index,
                originalWidth = item.width,
                originalHeight = item.height,
                // If PageArrangement has an ID and you want to update existing DB entries:
                // id = if (item.id.startsWith("pa_")) item.id.removePrefix("pa_").toLong() else 0L
                // This part depends on how you handle PageArrangement IDs. Assuming Room's replace strategy.
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.savePageArrangementForWorkspace(wsId, updatedArrangements)
        }
        _uiState.update { it.copy(pages = _editorPageItems.toList()) }
    }

    fun renameWorkspace(newName: String) {
        currentWorkspaceId?.let { wsId ->
            if (newName.isNotBlank() && newName != _uiState.value.workspaceName) {
                viewModelScope.launch {
                    repository.getWorkspaceById(wsId)?.let {
                        repository.updateWorkspace(it.copy(name = newName, lastModified = System.currentTimeMillis()))
                        _uiState.update { state -> state.copy(workspaceName = newName) }
                    }
                }
            }
        }
    }

    suspend fun loadPagePreview(pageItem: PdfPageItem, highRes: Boolean = false): Bitmap? {
        if (!highRes && pageItem.previewBitmap != null) {
            return pageItem.previewBitmap
        }
        return withContext(Dispatchers.IO) {
            try {
                val previewWidth = if (highRes) pageItem.width / 2 else pageItem.width / 6 // Smaller for faster list preview
                val previewHeight = if (highRes) pageItem.height / 2 else pageItem.height / 6

                getApplication<Application>().applicationContext.contentResolver
                    .openFileDescriptor(pageItem.getOriginalFileUri(), "r")?.use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            renderer.openPage(pageItem.originalPageIndex).use { page ->
                                val bitmap = createBitmap(
                                    if (previewWidth > 0) previewWidth else 1,
                                    if (previewHeight > 0) previewHeight else 1,
                                    Bitmap.Config.ARGB_8888
                                )
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                if (!highRes) {
                                    pageItem.previewBitmap = bitmap // Cache thumbnail
                                }
                                bitmap
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("EditorVM_Preview", "Error loading preview for ${pageItem.id} (${pageItem.originalFileUriString})", e)
                null
            }
        }
    }

    // --- NEW: PDF Merging & Saving using Python ---
    fun createAndSaveMergedPdf(context: Context, workspaceNameFromUi: String) {
        if (_editorPageItems.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "No pages to merge.") }
            return
        }

        val finalOutputFileName = "${workspaceNameFromUi.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        _uiState.update { it.copy(isSaving = true, processingMessage = "Preparing PDF via Python...") }

        viewModelScope.launch {
            var pythonSuccess = false
            var tempMergedPdfPathFromPython: String? = null
            var processingErrorMessage: String? = null
            val tempFilesToCleanUp = mutableListOf<File>()

            try {
                val orderedPageSourcesForPython = mutableListOf<Map<String, Any>>()
                // --- Step 1: Copy input PDFs from URIs to temporary files in app's cache ---
                withContext(Dispatchers.IO) {
                    _editorPageItems.forEachIndexed { index, pageItem ->
                        _uiState.update { it.copy(processingMessage = "Preparing page ${index + 1} for Python...") }
                        val originalUri = pageItem.getOriginalFileUri()
                        val tempInputFile = File.createTempFile(
                            "input_${originalUri.lastPathSegment?.take(20) ?: "page"}_${pageItem.originalPageIndex}_",
                            ".pdf",
                            context.cacheDir
                        )
                        tempFilesToCleanUp.add(tempInputFile)

                        try {
                            context.contentResolver.openInputStream(originalUri)?.use { inputStream ->
                                FileOutputStream(tempInputFile).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            } ?: throw Exception("Failed to open input stream for ${pageItem.originalFileUriString}")

                            orderedPageSourcesForPython.add(
                                mapOf(
                                    "file_path" to tempInputFile.absolutePath,
                                    "original_page_index" to pageItem.originalPageIndex
                                )
                            )
                        } catch (e: Exception) {
                            Log.e("EditorVM_PythonPrep", "Error copying ${pageItem.originalFileUriString} to cache", e)
                            throw Exception("Failed to prepare input: ${originalUri.lastPathSegment}. ${e.message}")
                        }
                    }

                    if (orderedPageSourcesForPython.isEmpty() && _editorPageItems.isNotEmpty()) {
                        throw Exception("Failed to prepare any pages for Python processing.")
                    }
                    if (orderedPageSourcesForPython.size != _editorPageItems.size){
                        Log.w("EditorVM_PythonPrep", "Mismatch in prepared pages (${orderedPageSourcesForPython.size}) and editor items (${_editorPageItems.size})")
                        // Potentially throw error if this is critical
                    }


                    // --- Step 2: Prepare temporary output path for Python ---
                    val tempOutputPythonFile = File(context.cacheDir, "temp_merged_py_${UUID.randomUUID()}.pdf")
                    tempFilesToCleanUp.add(tempOutputPythonFile) // Add for cleanup

                    // --- Step 3: Call Python ---
                    _uiState.update { it.copy(processingMessage = "Python is merging PDF...") }
                    Log.d("EditorVM_Python", "Calling Python with ${orderedPageSourcesForPython.size} sources. Output to: ${tempOutputPythonFile.absolutePath}")

                    if (!Python.isStarted()) {
                        Python.start(AndroidPlatform(context))
                    }
                    val py = Python.getInstance()
                    val pdfProcessorModule = py.getModule("pdf_util") // Your Python file name

                    val result: PyObject? = pdfProcessorModule.callAttr(
                        "merge_reorder_pdfs_to_temporary_file", // Python function name
                        tempOutputPythonFile.absolutePath,
                        orderedPageSourcesForPython
                    )

                    // --- Step 4: Process Python result ---
                    result?.asList()?.let { resultList ->
                        if (resultList.size == 2) {
                            pythonSuccess = resultList[0].toBoolean()
                            if (pythonSuccess) {
                                tempMergedPdfPathFromPython = resultList[1].toString()
                                Log.i("EditorVM_Python", "Python success. Temp output: $tempMergedPdfPathFromPython")
                            } else {
                                processingErrorMessage = resultList[1].toString()
                                Log.e("EditorVM_Python", "Python Error: $processingErrorMessage")
                            }
                        } else {
                            processingErrorMessage = "Invalid result list size from Python: ${resultList.size}"
                            Log.e("EditorVM_Python", processingErrorMessage!!)
                        }
                    } ?: run {
                        processingErrorMessage = "Python function returned null or unexpected result type."
                        Log.e("EditorVM_Python", processingErrorMessage!!)
                    }
                } // End of withContext(Dispatchers.IO) for Python call and file prep

                // --- Step 5: Save the temporary merged PDF to public storage (Kotlin handles this) ---
                if (pythonSuccess && tempMergedPdfPathFromPython != null) {
                    _uiState.update { it.copy(processingMessage = "Finalizing saved PDF...") }
                    var finalSaveSuccess = false
                    var finalSavedUriString: String? = null

                    withContext(Dispatchers.IO) {
                        try {
                            val tempMergedFileFromPy = File(tempMergedPdfPathFromPython!!)
                            if (!tempMergedFileFromPy.exists() || tempMergedFileFromPy.length() == 0L) {
                                throw Exception("Python's output temp PDF is missing or empty at $tempMergedPdfPathFromPython")
                            }

                            val resolver = context.contentResolver
                            var outputStreamForSave: OutputStream? = null
                            val finalOutputPublicUri: Uri?

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val contentValues = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalOutputFileName)
                                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + File.separator + "PDFStudio")
                                }
                                finalOutputPublicUri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), contentValues)
                                finalOutputPublicUri?.let { outputStreamForSave = resolver.openOutputStream(it) }
                            } else {
                                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                                val pdfStudioDir = File(documentsDir, "PDFStudio")
                                if (!pdfStudioDir.exists()) { pdfStudioDir.mkdirs() }
                                val finalPublicFile = File(pdfStudioDir, finalOutputFileName)
                                finalOutputPublicUri = Uri.fromFile(finalPublicFile)
                                outputStreamForSave = FileOutputStream(finalPublicFile)
                            }

                            outputStreamForSave?.use { outS ->
                                FileInputStream(tempMergedFileFromPy).use { inS ->
                                    inS.copyTo(outS)
                                }
                                finalSavedUriString = finalOutputPublicUri?.toString()
                                finalSaveSuccess = true
                                Log.i("EditorVM_Save", "Successfully saved to public: $finalSavedUriString")

                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && finalOutputPublicUri?.scheme == "file") {
                                    finalOutputPublicUri.path?.let { filePath ->
                                        try {
                                            val values = ContentValues().apply {
                                                put(MediaStore.MediaColumns.DISPLAY_NAME, finalOutputFileName)
                                                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                                                put(MediaStore.MediaColumns.DATA, filePath)
                                            }
                                            resolver.insert(MediaStore.Files.getContentUri("external"), values)
                                        } catch (e: Exception) { Log.w("EditorVM_Save", "Failed to add legacy file to MediaStore: $e") }
                                    }
                                }
                            } ?: throw Exception("Failed to get output stream for final save to public storage.")
                        } catch (e: Exception) {
                            Log.e("EditorVM_Save", "Error saving final PDF from Python's output to public storage", e)
                            processingErrorMessage = "Kotlin Save Error: ${e.message?.take(100)}"
                            finalSaveSuccess = false
                        }
                    } // End of withContext(Dispatchers.IO) for Kotlin save

                    if (finalSaveSuccess && finalSavedUriString != null) {
                        repository.addMergedPdf(
                            MergedPdf(
                                name = finalOutputFileName,
                                filePathUriString = finalSavedUriString,
                                sourceWorkspaceId = currentWorkspaceId
                            )
                        )
                        currentWorkspaceId?.let { wsIdToDelete -> // Delete workspace as per previous requirement
                            repository.getWorkspaceById(wsIdToDelete)?.let { repository.deleteWorkspace(it) }
                        }
                        _uiState.update { it.copy(isSaving = false, processingMessage = null, snackbarMessage = "$finalOutputFileName saved (Python)! Workspace closed.") }
                        _navigateToHomeEvent.emit(Unit)
                    } else {
                        _uiState.update { it.copy(isSaving = false, processingMessage = null, snackbarMessage = processingErrorMessage ?: "Failed to save PDF via Python.") }
                    }
                } else { // Python processing itself failed
                    _uiState.update { it.copy(isSaving = false, processingMessage = null, snackbarMessage = "Python Error: ${processingErrorMessage ?: "Unknown Python processing error"}") }
                }
            } catch (e: Exception) { // Catch errors from initial Kotlin file prep or other unhandled issues
                Log.e("EditorVM_PythonMerge", "Overall error in mergeAndSavePdfViaPython", e)
                _uiState.update { it.copy(isSaving = false, processingMessage = null, snackbarMessage = "Error: ${e.message?.take(100)}") }
            } finally {
                // --- Step 6: Clean up ALL temporary files ---
                withContext(Dispatchers.IO) {
                    tempFilesToCleanUp.forEach { file ->
                        if (file.exists()) {
                            if(file.delete()) {
                                Log.d("EditorVM_Cleanup", "Deleted temp file: ${file.name}")
                            } else {
                                Log.w("EditorVM_Cleanup", "FAILED to delete temp file: ${file.name}")
                            }
                        }
                    }
                }
                // Ensure loading state is reset, regardless of snackbar message
                _uiState.update { it.copy(isSaving = false, processingMessage = null) }
            }
        }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        pageLoadingJob?.cancel()
    }
}
