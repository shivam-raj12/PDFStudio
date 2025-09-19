package com.shivam_raj.pdfstudio.ui.homescreen

 import android.net.Uri
 import androidx.compose.animation.core.tween
 import androidx.compose.foundation.BorderStroke
 import androidx.compose.foundation.ExperimentalFoundationApi
 import androidx.compose.foundation.clickable
 import androidx.compose.foundation.layout.Arrangement
 import androidx.compose.foundation.layout.Box
 import androidx.compose.foundation.layout.Column
 import androidx.compose.foundation.layout.PaddingValues
 import androidx.compose.foundation.layout.Row
 import androidx.compose.foundation.layout.Spacer
 import androidx.compose.foundation.layout.fillMaxSize
 import androidx.compose.foundation.layout.fillMaxWidth
 import androidx.compose.foundation.layout.height
 import androidx.compose.foundation.layout.padding
 import androidx.compose.foundation.layout.size
 import androidx.compose.foundation.layout.width
 import androidx.compose.foundation.lazy.LazyColumn
 import androidx.compose.foundation.lazy.items
 import androidx.compose.foundation.shape.RoundedCornerShape
 import androidx.compose.material.icons.Icons
 import androidx.compose.material.icons.automirrored.filled.Launch
 import androidx.compose.material.icons.automirrored.filled.LibraryBooks
 import androidx.compose.material.icons.filled.Add
 import androidx.compose.material.icons.filled.DeleteOutline
 import androidx.compose.material.icons.filled.DriveFileRenameOutline
 import androidx.compose.material.icons.filled.EditNote
 import androidx.compose.material.icons.filled.MoreVert
 import androidx.compose.material.icons.filled.PictureAsPdf
 import androidx.compose.material.icons.filled.PostAdd
 import androidx.compose.material.icons.filled.Print
 import androidx.compose.material.icons.filled.Share
 import androidx.compose.material.icons.filled.Workspaces
 import androidx.compose.material3.Button
 import androidx.compose.material3.Card
 import androidx.compose.material3.CardDefaults
 import androidx.compose.material3.CenterAlignedTopAppBar
 import androidx.compose.material3.CircularProgressIndicator
 import androidx.compose.material3.DropdownMenu
 import androidx.compose.material3.ExperimentalMaterial3Api
 import androidx.compose.material3.ExtendedFloatingActionButton
 import androidx.compose.material3.FabPosition
 import androidx.compose.material3.Icon
 import androidx.compose.material3.IconButton
 import androidx.compose.material3.MaterialTheme
 import androidx.compose.material3.OutlinedCard
 import androidx.compose.material3.OutlinedTextField
 import androidx.compose.material3.Scaffold
 import androidx.compose.material3.SnackbarDuration
 import androidx.compose.material3.SnackbarHost
 import androidx.compose.material3.SnackbarHostState
 import androidx.compose.material3.Text
 import androidx.compose.material3.TextButton
 import androidx.compose.material3.TopAppBarDefaults
 import androidx.compose.material3.surfaceColorAtElevation
 import androidx.compose.runtime.Composable
 import androidx.compose.runtime.collectAsState
 import androidx.compose.runtime.getValue
 import androidx.compose.runtime.mutableStateOf
 import androidx.compose.runtime.remember
 import androidx.compose.runtime.rememberCoroutineScope
 import androidx.compose.runtime.setValue
 import androidx.compose.ui.Alignment
 import androidx.compose.ui.Modifier
 import androidx.compose.ui.platform.LocalContext
 import androidx.compose.ui.text.font.FontWeight
 import androidx.compose.ui.text.style.TextOverflow
 import androidx.compose.ui.unit.dp
 import androidx.compose.ui.window.Dialog
 import androidx.lifecycle.viewmodel.compose.viewModel
 import com.shivam_raj.pdfstudio.data.MergedPdf
 import com.shivam_raj.pdfstudio.data.Workspace
 import com.shivam_raj.pdfstudio.ui.common.EmptyStateView
 import com.shivam_raj.pdfstudio.ui.common.MoreActionsEntry
 import com.shivam_raj.pdfstudio.ui.common.ThemedSectionCard
 import com.shivam_raj.pdfstudio.ui.common.formatTimestamp
 import kotlinx.coroutines.launch
 import androidx.core.net.toUri
 import com.shivam_raj.pdfstudio.utils.openPdfWithIntent
 import com.shivam_raj.pdfstudio.utils.sharePdf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = viewModel(),
    onNavigateToEditor: (workspaceId: String) -> Unit,
    onNavigateToNewWorkspaceEditor: () -> Unit
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showRenameDialogFor by remember { mutableStateOf<Workspace?>(null) }


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("PDF Studio", fontWeight = FontWeight.Medium) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToNewWorkspaceEditor,
                icon = { Icon(Icons.Filled.Add, "Create new workspace") },
                text = { Text("New Workspace") },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.workspaces.isEmpty() && uiState.mergedPdfs.isEmpty()) {
            EmptyStateView(
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                title = "Welcome to PDF Studio",
                subtitle = "Create a new workspace to start combining and reordering your PDF pages.",
                actionText = "Create Workspace",
                onActionClick = onNavigateToNewWorkspaceEditor
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues), // Apply padding from Scaffold
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Workspaces Section
                item {
                    Text(
                        "Recent Workspaces",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (uiState.workspaces.isNotEmpty()) {
                    items(
                        uiState.workspaces,
                        key = { it.id }) { workspace ->
                        WorkspaceCard(
                            workspace = workspace,
                            onClick = { onNavigateToEditor(workspace.id) },
                            onRename = { showRenameDialogFor = workspace },
                            onDelete = {
                                homeViewModel.deleteWorkspace(workspace)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        "${workspace.name} deleted",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            modifier = Modifier.animateItem(
                                tween(durationMillis = 300)
                            )
                        )
                    }
                } else {
                    item {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onNavigateToNewWorkspaceEditor),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 32.dp, horizontal = 16.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Filled.PostAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No active workspaces", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Tap to create one!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // Merged PDFs Section
                if (uiState.mergedPdfs.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp)) // Extra space before next section
                        Text(
                            "Saved Documents",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(
                        uiState.mergedPdfs,
                        key = { it.id }) { mergedPdf ->
                        MergedPdfCard(
                            mergedPdf = mergedPdf,
                            onDelete = {
                                homeViewModel.deleteMergedPdf(mergedPdf)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        "${mergedPdf.name} deleted",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            onOpen = { pdfUri ->
                                openPdfWithIntent(context, pdfUri)
                            },
                            onOpenInWorkspace = { sourceWorkspaceId ->
                                if (sourceWorkspaceId != null) {
                                    // We need to create a NEW workspace that is a COPY of the source.
                                    // Or, if the source workspace still exists, maybe navigate to it?
                                    // For "re-edit", creating a new copy is safer to avoid altering a "master" workspace.
                                    homeViewModel.createWorkspaceFromExisting(sourceWorkspaceId) { newClonedWorkspaceId ->
                                        onNavigateToEditor(newClonedWorkspaceId)
                                    }
                                } else {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Cannot re-edit: Original workspace info not found.", duration = SnackbarDuration.Short)
                                    }
                                }
                            },
                            onShare = { pdfUri, pdfName ->
                                sharePdf(context, pdfUri, pdfName)
                            },
                            modifier = Modifier.animateItem(
                                tween(durationMillis = 300)
                            )
                        )
                    }
                }
            }
        }
    }

    if (showRenameDialogFor != null) {
        RenameWorkspaceDialog(
            workspace = showRenameDialogFor!!,
            onDismiss = { showRenameDialogFor = null },
            onConfirm = { workspace, newName ->
                homeViewModel.renameWorkspace(workspace, newName)
                showRenameDialogFor = null
            }
        )
    }
}


@Composable
fun WorkspaceCard(
    workspace: Workspace,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    ThemedSectionCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Workspaces,
                contentDescription = "Workspace",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(36.dp)
                    .padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workspace.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Last modified: ${workspace.lastModified.formatTimestamp()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    MoreActionsEntry("Rename", Icons.Filled.DriveFileRenameOutline, onClick = { onRename(); showMoreMenu = false })
                    MoreActionsEntry("Delete", Icons.Filled.DeleteOutline, onClick = { onDelete(); showMoreMenu = false })
                }
            }
        }
    }
}

@Composable
fun MergedPdfCard(
    mergedPdf: MergedPdf,
    onDelete: () -> Unit,
    onShare: (Uri, String) -> Unit,
    onOpen: (Uri) -> Unit,
    onOpenInWorkspace: (sourceWorkspaceId: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    ThemedSectionCard(modifier = modifier) { // Not clickable itself, actions via menu
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.PictureAsPdf,
                contentDescription = "PDF Document",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .size(36.dp)
                    .padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mergedPdf.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Created: ${mergedPdf.createdAt.formatTimestamp()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    if (mergedPdf.sourceWorkspaceId != null) { // Only show if we know the source
                        MoreActionsEntry("Re-edit in Workspace", Icons.Filled.EditNote, onClick = {
                            onOpenInWorkspace(mergedPdf.sourceWorkspaceId)
                            showMoreMenu = false
                        })
                    }
                    MoreActionsEntry("Open", Icons.AutoMirrored.Filled.Launch, onClick = {
                        onOpen(mergedPdf.filePathUriString.toUri())
                        showMoreMenu = false
                    })
                    MoreActionsEntry("Share", Icons.Filled.Share, onClick = {
                        onShare(mergedPdf.filePathUriString.toUri(), mergedPdf.name)
                        showMoreMenu = false
                    })
                    MoreActionsEntry("Delete", Icons.Filled.DeleteOutline, onClick = { onDelete(); showMoreMenu = false })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameWorkspaceDialog(
    workspace: Workspace,
    onDismiss: () -> Unit,
    onConfirm: (Workspace, String) -> Unit
) {
    var newName by remember(workspace.name) { mutableStateOf(workspace.name) }
    var nameError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Rename Workspace", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = {
                        newName = it
                        nameError = if (it.isBlank()) "Name cannot be empty" else null
                    },
                    label = { Text("Workspace Name") },
                    singleLine = true,
                    isError = nameError != null,
                    modifier = Modifier.fillMaxWidth()
                )
                nameError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
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
                            if (newName.isNotBlank()) {
                                onConfirm(workspace, newName)
                            } else {
                                nameError = "Name cannot be empty"
                            }
                        },
                        enabled = newName.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

