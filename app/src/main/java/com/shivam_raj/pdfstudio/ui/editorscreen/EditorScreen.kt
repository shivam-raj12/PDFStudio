package com.shivam_raj.pdfstudio.ui.editorscreen

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.copy
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shivam_raj.pdfstudio.ui.common.FullScreenPageView
import com.shivam_raj.pdfstudio.data.PdfPageItem
import com.shivam_raj.pdfstudio.ui.common.EmptyStateView
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    workspaceId: String?, // Null if creating a new workspace
    editorViewModel: EditorViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by editorViewModel.uiState.collectAsState()
    val editorPages = editorViewModel.editorPageItems // Use the mutableStateList for LazyColumn

    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showRenameWorkspaceDialog by remember { mutableStateOf(false) }
    var focusedPageItemForZoom by remember { mutableStateOf<PdfPageItem?>(null) }


    val multipleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            editorViewModel.addPdfsToWorkspace(uris)
        }
    }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                editorViewModel.createAndSaveMergedPdf(context, uiState.workspaceName)
            } else {
                Toast.makeText(context, "Storage permission denied.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (from.index != to.index) {
            editorViewModel.reorderPages(from.index, to.index)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            editorViewModel.clearSnackbarMessage()
        }
    }


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            EditorTopAppBar(
                workspaceName = uiState.workspaceName,
                onNavigateBack = onNavigateBack,
                onRenameClick = { showRenameWorkspaceDialog = true },
                onAddPdfsClick = { multipleFilePickerLauncher.launch(arrayOf("application/pdf")) },
                isLoading = uiState.isLoading || uiState.isSaving
            )
        },
        floatingActionButton = {
            if (editorPages.isNotEmpty() && !uiState.isSaving) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                editorViewModel.createAndSaveMergedPdf(
                                    context,
                                    uiState.workspaceName
                                )
                            } else {
                                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                        } else {
                            editorViewModel.createAndSaveMergedPdf(context, uiState.workspaceName)
                        }
                    },
                    icon = { Icon(Icons.Filled.Save, "Save Merged PDF") },
                    text = { Text("Save & Merge PDF") },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (uiState.isLoading && editorPages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (editorPages.isEmpty() && !uiState.isLoading) {
                EmptyStateView(
                    icon = Icons.AutoMirrored.Filled.NoteAdd,
                    title = "Empty Workspace",
                    subtitle = "Tap the '+' button in the top bar to add PDF files to this workspace.",
                )
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(editorPages, key = { _, item -> item.id }) { index, pageItem ->
                        ReorderableItem(
                            state = reorderableLazyListState,
                            key = pageItem.id
                        ) { isDragging ->
                            val elevation by animateDpAsState(
                                if (isDragging) 12.dp else 4.dp,
                                label = "page_elevation"
                            )
                            val scale by animateFloatAsState(
                                if (isDragging) 1.03f else 1f,
                                label = "page_scale"
                            )

                            EditablePageRow(
                                pageItem = pageItem,
                                viewModel = editorViewModel,
                                isDragging = isDragging,
                                elevation = elevation,
                                scale = scale,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(
                                        tween(
                                            durationMillis = 350,
                                            easing = FastOutSlowInEasing
                                        )
                                    ),
                                dragHandleModifier = Modifier.draggableHandle(),
                                onPageClick = { focusedPageItemForZoom = pageItem },
                                onRemoveClick = { editorViewModel.removePage(pageItem) }
                            )
                        }
                    }
                }
            }

            if (uiState.isSaving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                        .clickable(enabled = false, onClick = {}), // Block interactions
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.surface)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            uiState.processingMessage ?: "Saving...",
                            color = MaterialTheme.colorScheme.surface,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }


    if (showRenameWorkspaceDialog) {
        RenameCurrentWorkspaceDialog(
            currentName = uiState.workspaceName,
            onDismiss = { showRenameWorkspaceDialog = false },
            onConfirm = { newName ->
                editorViewModel.renameWorkspace(newName)
                showRenameWorkspaceDialog = false
            }
        )
    }

    AnimatedVisibility(
        visible = focusedPageItemForZoom != null,
        enter = fadeIn(animationSpec = tween(300)) + scaleIn(
            initialScale = 0.85f,
            animationSpec = tween(300)
        ),
        exit = fadeOut(animationSpec = tween(250)) + scaleOut(
            targetScale = 0.85f,
            animationSpec = tween(250)
        )
    ) {
        focusedPageItemForZoom?.let { item ->
            FullScreenPageView( // Using the same one from HomeScreen for consistency
                pageItem = item,
                viewModel = editorViewModel, // Pass editorViewModel
                onClose = { focusedPageItemForZoom = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopAppBar(
    workspaceName: String,
    onNavigateBack: () -> Unit,
    onRenameClick: () -> Unit,
    onAddPdfsClick: () -> Unit,
    isLoading: Boolean
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onRenameClick, enabled = !isLoading)
            ) {
                Text(
                    text = workspaceName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Rename Workspace",
                    modifier = Modifier
                        .size(18.dp)
                        .padding(start = 4.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack, enabled = !isLoading) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onAddPdfsClick, enabled = !isLoading) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Add PDFs")
            }
            // You can add more actions here (e.g., clear workspace)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        )
    )
}

@Composable
fun EditablePageRow(
    pageItem: PdfPageItem,
    viewModel: EditorViewModel, // Changed from PdfViewModel to EditorViewModel
    isDragging: Boolean,
    elevation: Dp,
    scale: Float,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    onPageClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var imageBitmapForUi by remember(pageItem.id) { // Key primarily on pageItem.id for this state
        mutableStateOf(pageItem.previewBitmap?.asImageBitmap())
    }

    LaunchedEffect(pageItem.id, pageItem.previewBitmap) {
        Log.d(
            "EditablePageRow",
            "LAUNCHED EFFECT for ID: ${pageItem.id}. Has source bitmap: ${pageItem.previewBitmap != null}. UI bitmap exists: ${imageBitmapForUi != null}"
        )

        if (pageItem.previewBitmap == null) {
            // Source Android Bitmap is not loaded in the item.
            // This happens for items newly loaded from DB or if a previous load failed.
            Log.d("EditablePageRow", "ID: ${pageItem.id} - Source bitmap is NULL. Triggering load.")

            //viewModel.loadPagePreview is a suspend function and updates pageItem.previewBitmap internally.
            viewModel.loadPagePreview(pageItem, highRes = false)
            // After loadPagePreview completes, pageItem.previewBitmap should be populated.
            // This change to pageItem.previewBitmap (a key of this LaunchedEffect)
            // will cause this LaunchedEffect to RE-RUN.
            // On the next run, it will enter the 'else' block below.

        } else {
            // Source Android Bitmap IS present in pageItem.previewBitmap.
            // This means it was either:
            //   a) Just loaded by the 'if' block above in a previous run of this effect.
            //   b) Already present when this row was first composed (e.g., for a newly added PDF).
            // We need to ensure our UI state (imageBitmapForUi) reflects this.

            // Check if the current UI bitmap is different from the source bitmap in pageItem.
            // This check avoids unnecessary recompositions if they are already the same.
            // Direct instance comparison of ImageBitmap is tricky; comparing the source Bitmap is better.
            // However, since we're inside LaunchedEffect(pageItem.previewBitmap), if we reach here
            // and pageItem.previewBitmap is not null, it implies either it was just loaded
            // or it was already there. We should update imageBitmapForUi if it's not already reflecting it.

            if (imageBitmapForUi?.asAndroidBitmap() !== pageItem.previewBitmap) {
                Log.d(
                    "EditablePageRow",
                    "ID: ${pageItem.id} - Source bitmap EXISTS. Syncing imageBitmapForUi."
                )
                imageBitmapForUi =
                    pageItem.previewBitmap?.asImageBitmap() // This handles null just in case, though it shouldn't be
            } else {
                Log.d(
                    "EditablePageRow",
                    "ID: ${pageItem.id} - Source bitmap EXISTS and UI is already in sync."
                )
            }
        }
    }

    val shadowColor = if (isDragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    else MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)

    Card(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(
                elevation = elevation,
                spotColor = shadowColor,
                ambientColor = shadowColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                onClick = onPageClick,
                enabled = !isDragging
            ), // Disable click while dragging
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = dragHandleModifier
                    .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                    .padding(end = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.DragIndicator,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Box(
                modifier = Modifier
                    .width(75.dp)
                    .height(105.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLowest,
                        RoundedCornerShape(6.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        RoundedCornerShape(6.dp)
                    )
                    .clip(RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (imageBitmapForUi != null) {
                    Image(
                        bitmap = imageBitmapForUi!!,
                        contentDescription = "Page Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Text(
                    "Page ${pageItem.originalPageIndex + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "From: ${
                        Uri.parse(pageItem.originalFileUriString).lastPathSegment?.takeLast(25)
                            ?.let { if (it.length == 25) "...$it" else it } ?: "Source PDF"
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${pageItem.width}x${pageItem.height} pts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.DeleteOutline, // Changed to outline for less visual weight
                    contentDescription = "Remove Page",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameCurrentWorkspaceDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textFieldValue by remember(currentName) {
        mutableStateOf(TextFieldValue(currentName, TextRange(currentName.length)))
    }
    var nameError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    6.dp
                )
            )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Rename Workspace",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = {
                        textFieldValue = it
                        nameError = if (it.text.isBlank()) "Name cannot be empty" else null
                    },
                    label = { Text("Workspace Name") },
                    singleLine = true,
                    isError = nameError != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (textFieldValue.text.isNotBlank()) {
                            onConfirm(textFieldValue.text)
                        } else {
                            nameError = "Name cannot be empty"
                        }
                        focusManager.clearFocus()
                    })
                )
                nameError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (textFieldValue.text.isNotBlank()) {
                                onConfirm(textFieldValue.text)
                            } else {
                                nameError = "Name cannot be empty"
                            }
                        },
                        enabled = textFieldValue.text.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}