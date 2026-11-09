package com.jaywebtech.autoshorts.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.jaywebtech.autoshorts.AppViewModel
import com.jaywebtech.autoshorts.data.CandidateEntity
import com.jaywebtech.autoshorts.data.LlmProvider
import com.jaywebtech.autoshorts.data.ProjectEntity
import java.util.Locale

private enum class HomeTab(val label: String) {
    STUDIO("Studio"), HISTORY("History"), SETTINGS("Settings")
}

@Composable
fun AutoShortsApp(viewModel: AppViewModel) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsState()
    val project by viewModel.selectedProject.collectAsState()
    val candidates by viewModel.candidates.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val workingCandidateId by viewModel.workingCandidateId.collectAsState()
    val message by viewModel.message.collectAsState()
    var tab by rememberSaveable { mutableStateOf(HomeTab.STUDIO) }
    var previewPosition by remember { mutableStateOf<Long?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importMedia(it) { previewPosition = null } }
    }

    Scaffold(
        containerColor = Paper,
        bottomBar = {
            if (project == null) {
                NavigationBar(containerColor = White) {
                    HomeTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                Icon(
                                    when (item) {
                                        HomeTab.STUDIO -> Icons.Default.Home
                                        HomeTab.HISTORY -> Icons.Default.History
                                        HomeTab.SETTINGS -> Icons.Default.Settings
                                    },
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            if (message != null) {
                MessageBar(message = message.orEmpty(), onDismiss = viewModel::dismissMessage)
            }
            when {
                project != null -> ProjectScreen(
                    project = project!!,
                    candidates = candidates,
                    busy = busy,
                    liveProgress = progress,
                    workingCandidateId = workingCandidateId,
                    previewPosition = previewPosition,
                    onPreview = { previewPosition = it },
                    onBack = viewModel::closeProject,
                    onAnalyze = viewModel::analyze,
                    onRename = viewModel::renameProject,
                    onSelect = viewModel::setSelected,
                    onEdit = viewModel::updateCandidate,
                    onExport = viewModel::export,
                    onShare = { uri, mimeType -> shareFile(context, uri, mimeType) },
                )
                tab == HomeTab.SETTINGS -> SettingsScreen(viewModel)
                else -> ProjectListScreen(
                    projects = projects,
                    history = tab == HomeTab.HISTORY,
                    onOpen = viewModel::openProject,
                    onImport = { picker.launch(arrayOf("video/*", "audio/*")) },
                    onDelete = viewModel::delete,
                )
            }
        }
    }
}

@Composable
private fun ProjectListScreen(
    projects: List<ProjectEntity>,
    history: Boolean,
    onOpen: (String) -> Unit,
    onImport: () -> Unit,
    onDelete: (ProjectEntity) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = Ink, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = Leaf)
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("AUTOSHORTS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MutedInk, letterSpacing = 1.2.sp)
                Text(if (history) "Your projects" else "Clip studio", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Coral)
        }
        Column(Modifier.padding(horizontal = 22.dp)) {
            Text(if (history) "RECENT WORK" else "MAKE THE FIRST CUT", fontSize = 11.sp, color = MutedInk, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(9.dp))
            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import video or audio", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Projects", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${projects.size} total", fontSize = 12.sp, color = MutedInk)
        }
        Spacer(Modifier.height(7.dp))
        if (projects.isEmpty()) {
            EmptyState(Modifier.weight(1f))
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                items(projects, key = ProjectEntity::id) { item ->
                    ProjectRow(item, onClick = { onOpen(item.id) }, onDelete = { onDelete(item) })
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(project: ProjectEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = Leaf.copy(alpha = 0.35f), shape = RoundedCornerShape(9.dp), modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Movie, contentDescription = null, tint = Ink) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(project.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("${formatDuration(project.durationMs)}  ·  ${project.stage}", fontSize = 12.sp, color = MutedInk, maxLines = 1)
            if (project.status == "Processing") {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { project.progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${project.name}", tint = MutedInk) }
    }
    HorizontalDivider(color = Line, modifier = Modifier.padding(start = 78.dp, end = 22.dp))
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 30.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(color = White, shape = CircleShape, modifier = Modifier.size(82.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Movie, contentDescription = null, tint = Coral, modifier = Modifier.size(34.dp)) }
        }
        Spacer(Modifier.height(17.dp))
        Text("No projects yet", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Your imported media and generated clips will appear here.", color = MutedInk, fontSize = 14.sp)
    }
}

@Composable
@OptIn(markerClass = [UnstableApi::class])
private fun ProjectScreen(
    project: ProjectEntity,
    candidates: List<CandidateEntity>,
    busy: Boolean,
    liveProgress: com.jaywebtech.autoshorts.PipelineProgress?,
    workingCandidateId: String?,
    previewPosition: Long?,
    onPreview: (Long) -> Unit,
    onBack: () -> Unit,
    onAnalyze: () -> Unit,
    onRename: (String, String) -> Unit,
    onSelect: (CandidateEntity, Boolean) -> Unit,
    onEdit: (CandidateEntity) -> Unit,
    onExport: (CandidateEntity) -> Unit,
    onShare: (Uri, String) -> Unit,
) {
    var renameDialog by rememberSaveable(project.id) { mutableStateOf(false) }
    var projectName by remember(project.id, project.name) { mutableStateOf(project.name) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(project.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                Text("${formatDuration(project.durationMs)} source", color = MutedInk, fontSize = 12.sp)
            }
            IconButton(onClick = { renameDialog = true }) {
                Icon(Icons.Default.Edit, contentDescription = "Rename project", tint = MutedInk)
            }
            Surface(color = Leaf.copy(alpha = 0.38f), shape = RoundedCornerShape(6.dp)) {
                Text(project.status.uppercase(Locale.getDefault()), modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                VideoPreview(Uri.parse(project.sourceUri), previewPosition)
            }
            Spacer(Modifier.height(15.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Moment finder", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(if (candidates.isEmpty()) "Transcribe and rank the strongest moments" else "${candidates.size} moments ranked for review", color = MutedInk, fontSize = 13.sp)
                }
                Button(onClick = onAnalyze, enabled = !busy, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Leaf)
                    Spacer(Modifier.width(7.dp))
                    Text(if (busy) "Working" else if (candidates.isEmpty()) "Analyze" else "Run again")
                }
            }
            if (busy || project.status == "Processing") {
                Spacer(Modifier.height(14.dp))
                val percent = liveProgress?.percent ?: project.progress
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                Text("${liveProgress?.stage ?: project.stage}  ·  $percent%", color = MutedInk, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            }
            Spacer(Modifier.height(17.dp))
            candidates.forEach { candidate ->
                CandidateCard(
                    candidate = candidate,
                    durationMs = project.durationMs,
                    working = workingCandidateId == candidate.id,
                    onToggle = { onSelect(candidate, it) },
                    onEdit = onEdit,
                    onPreview = { onPreview(candidate.startMs) },
                    onExport = { onExport(candidate) },
                    onShare = onShare,
                )
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(18.dp))
        }
    }
    if (renameDialog) {
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text("Rename project") },
            text = {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Project name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(project.id, projectName)
                    renameDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
@UnstableApi
private fun VideoPreview(uri: Uri, seekToMs: Long?) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }
    LaunchedEffect(seekToMs, player) {
        seekToMs?.let {
            player.seekTo(it)
            player.play()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShowSubtitleButton(false)
            }
        },
        update = { it.player = player },
        modifier = Modifier.width(205.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)),
    )
}

@Composable
private fun CandidateCard(
    candidate: CandidateEntity,
    durationMs: Long,
    working: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: (CandidateEntity) -> Unit,
    onPreview: () -> Unit,
    onExport: () -> Unit,
    onShare: (Uri, String) -> Unit,
) {
    var range by remember(candidate.id, candidate.startMs, candidate.endMs) {
        mutableStateOf(candidate.startMs.toFloat()..candidate.endMs.toFloat())
    }
    Card(
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        border = BorderStroke(1.dp, Line),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("MOMENT ${candidate.rank.toString().padStart(2, '0')}  ·  ${(candidate.score * 100).toInt()}%", color = MutedInk, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.6.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(candidate.hook, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp)
                }
                Checkbox(checked = candidate.selected, onCheckedChange = onToggle)
            }
            Text(candidate.rationale, color = MutedInk, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(range.start.toLong()), fontSize = 11.sp, color = MutedInk)
                RangeSlider(
                    value = range,
                    onValueChange = { values ->
                        if (values.endInclusive - values.start >= 1000f) range = values
                    },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.weight(1f),
                )
                Text(formatDuration(range.endInclusive.toLong()), fontSize = 11.sp, color = MutedInk)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${formatDuration(range.endInclusive.toLong() - range.start.toLong())} clip", color = MutedInk, fontSize = 11.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onPreview) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Preview")
                }
                if (candidate.outputUri != null) {
                    IconButton(onClick = { onShare(Uri.parse(candidate.outputUri), "video/mp4") }) {
                        Icon(Icons.Default.DownloadDone, contentDescription = "Share exported clip", tint = Ink)
                    }
                }
                if (candidate.captionUri != null) {
                    IconButton(onClick = { onShare(Uri.parse(candidate.captionUri), "application/x-subrip") }) {
                        Icon(Icons.Default.Subtitles, contentDescription = "Share timed captions", tint = Ink)
                    }
                }
            }
            if (range.start.toLong() != candidate.startMs || range.endInclusive.toLong() != candidate.endMs) {
                TextButton(onClick = { onEdit(candidate.copy(startMs = range.start.toLong(), endMs = range.endInclusive.toLong())) }) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Save trim")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(candidate.renderStatus, color = MutedInk, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Button(
                    onClick = onExport,
                    enabled = !working && candidate.selected,
                    shape = RoundedCornerShape(7.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink),
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(17.dp), tint = Leaf)
                    Spacer(Modifier.width(6.dp))
                    Text(if (working) "Exporting" else "Export MP4")
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(viewModel: AppViewModel) {
    val settings = viewModel.settings
    var deepgram by remember { mutableStateOf(settings.deepgramKey) }
    var provider by remember { mutableStateOf(settings.llmProvider) }
    var apiKey by remember(provider) { mutableStateOf(settings.llmKey) }
    var model by remember { mutableStateOf(settings.llmModel) }
    var baseUrl by remember { mutableStateOf(settings.openAiBaseUrl) }
    var showProviderMenu by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp)) {
        Text("Settings", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Keys are stored encrypted on this device.", fontSize = 13.sp, color = MutedInk)
        Spacer(Modifier.height(22.dp))
        Text("TRANSCRIPTION", fontSize = 11.sp, color = MutedInk, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = deepgram,
            onValueChange = { deepgram = it; saved = false },
            label = { Text("Deepgram API key") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(21.dp))
        Text("MOMENT ANALYSIS", fontSize = 11.sp, color = MutedInk, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(onClick = { showProviderMenu = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Text(provider.label, modifier = Modifier.weight(1f))
                Text("Change")
            }
            DropdownMenu(expanded = showProviderMenu, onDismissRequest = { showProviderMenu = false }) {
                LlmProvider.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { provider = option; apiKey = settingsKeyFor(settings, option); showProviderMenu = false; saved = false },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; saved = false },
            label = { Text("${provider.label} API key") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = model,
            onValueChange = { model = it; saved = false },
            label = { Text("Model (optional)") },
            placeholder = { Text(defaultModel(provider)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (provider == LlmProvider.OPENAI) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; saved = false },
                label = { Text("OpenAI-compatible API base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                settings.deepgramKey = deepgram
                settings.llmProvider = provider
                settings.llmKey = apiKey
                settings.llmModel = model
                settings.openAiBaseUrl = baseUrl
                saved = true
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
        ) {
            Icon(Icons.Default.Save, contentDescription = null, tint = Leaf)
            Spacer(Modifier.width(8.dp))
            Text(if (saved) "Saved securely" else "Save settings")
        }
        Spacer(Modifier.height(24.dp))
        Text("Cloud services receive extracted audio and transcript text when you run analysis.", color = MutedInk, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

private fun settingsKeyFor(settings: com.jaywebtech.autoshorts.data.AppSettings, provider: LlmProvider): String {
    return settings.keyFor(provider)
}

@Composable
private fun MessageBar(message: String, onDismiss: () -> Unit) {
    Surface(color = Ink, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(Modifier.padding(start = 13.dp, end = 4.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = White) }
        }
    }
}

private fun shareFile(context: android.content.Context, uri: Uri, mimeType: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = android.content.ClipData.newUri(context.contentResolver, "AutoShorts export", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share AutoShorts export"))
}

private fun formatDuration(durationMs: Long): String {
    val total = (durationMs.coerceAtLeast(0) / 1000).toInt()
    return "%d:%02d".format(Locale.getDefault(), total / 60, total % 60)
}

private fun defaultModel(provider: LlmProvider): String = when (provider) {
    LlmProvider.DEEPSEEK -> "deepseek-chat"
    LlmProvider.CLAUDE -> "claude-3-5-sonnet-latest"
    LlmProvider.GEMINI -> "gemini-2.0-flash"
    LlmProvider.OPENAI -> "gpt-4o-mini"
}