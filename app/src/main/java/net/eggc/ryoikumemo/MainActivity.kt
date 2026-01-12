package net.eggc.ryoikumemo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import net.eggc.ryoikumemo.data.AppPreferences
import net.eggc.ryoikumemo.data.CsvExportManager
import net.eggc.ryoikumemo.data.CsvImportManager
import net.eggc.ryoikumemo.data.FirestoreNoteRepository
import net.eggc.ryoikumemo.data.Note
import net.eggc.ryoikumemo.data.NoteRepository
import net.eggc.ryoikumemo.data.SharedPreferencesNoteRepository
import net.eggc.ryoikumemo.data.StampType
import net.eggc.ryoikumemo.ui.theme.RyoikumemoTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RyoikumemoTheme {
                RyoikumemoApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreenSizes
@Composable
fun RyoikumemoApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.TIMELINE) }
    var editingStampId by rememberSaveable { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUser = Firebase.auth.currentUser
    val noteRepository = remember<NoteRepository> {
        if (currentUser != null) {
            FirestoreNoteRepository()
        } else {
            SharedPreferencesNoteRepository(context)
        }
    }
    var currentNote by remember { mutableStateOf<Note?>(null) }
    var allNotes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var refreshNotesTrigger by remember { mutableStateOf(0) }
    var selectedMonth by remember { mutableStateOf(LocalDate.now()) }
    val appPreferences = remember { AppPreferences(context) }

    var noteToExport by remember { mutableStateOf<Note?>(null) }
    var noteToImport by remember { mutableStateOf<Note?>(null) }

    // Managers
    val csvImportManager = remember(noteRepository) { CsvImportManager(context, noteRepository) }
    val csvExportManager = remember(noteRepository) { CsvExportManager(context, noteRepository) }

    // CSV Export Handling
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null && noteToExport != null) {
            coroutineScope.launch {
                val result = csvExportManager.exportCsv(uri, noteToExport!!)
                if (result.isSuccess) {
                    Toast.makeText(context, "エクスポートが完了しました", Toast.LENGTH_SHORT).show()
                } else {
                    val error = result.exceptionOrNull()
                    Toast.makeText(context, "エラーが発生しました: ${error?.message}", Toast.LENGTH_LONG).show()
                }
                noteToExport = null
            }
        } else {
            noteToExport = null
        }
    }

    // CSV Import Handling
    val importContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && noteToImport != null) {
            coroutineScope.launch {
                val result = csvImportManager.importCsv(uri, noteToImport!!)
                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    Toast.makeText(context, "${count}件のデータをインポートしました", Toast.LENGTH_SHORT).show()
                } else {
                    val error = result.exceptionOrNull()
                    Toast.makeText(context, "エラーが発生しました: ${error?.message}", Toast.LENGTH_LONG).show()
                }
                noteToImport = null
            }
        } else {
            noteToImport = null
        }
    }

    LaunchedEffect(noteRepository, currentUser, refreshNotesTrigger) {
        coroutineScope.launch {
            // Load own notes only to reduce API calls
            val ownNotes = noteRepository.getNotes()
            allNotes = ownNotes

            // For current note validation, we only need own notes or subscribed IDs (1 API call)
            val subscribedIds = if (currentUser != null) noteRepository.getSubscribedNoteIds() else emptyList()

            // Try to load the last selected note from preferences
            val lastNote = appPreferences.getLastSelectedNote()
            if (lastNote != null) {
                // Validate if the note is still accessible (owned or subscribed)
                val isValid = if (lastNote.sharedId == null) {
                    ownNotes.any { it.id == lastNote.id }
                } else {
                    subscribedIds.contains(lastNote.sharedId)
                }

                if (isValid) {
                    currentNote = lastNote
                } else {
                    currentNote = ownNotes.firstOrNull() ?: noteRepository.createNote("ノート1")
                    if (ownNotes.isEmpty()) allNotes = listOf(currentNote!!)
                    currentNote?.let { appPreferences.saveLastSelectedNote(it) }
                }
            } else {
                currentNote = ownNotes.firstOrNull() ?: noteRepository.createNote("ノート1")
                if (ownNotes.isEmpty()) allNotes = listOf(currentNote!!)
                currentNote?.let { appPreferences.saveLastSelectedNote(it) }
            }
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.filter { it.icon != null }.forEach { dest ->
                item(
                    icon = {
                        Icon(
                            dest.icon!!,
                            contentDescription = dest.label
                        )
                    },
                    label = { Text(dest.label) },
                    selected = dest == currentDestination,
                    onClick = {
                        currentDestination = dest
                        editingStampId = null
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.clickable { currentDestination = AppDestinations.NOTE },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Book,
                                contentDescription = "ノート選択"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(currentNote?.name ?: "")
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = "ノートを切り替え"
                            )
                        }
                    },
                    actions = {
                        if (currentUser != null) {
                            AsyncImage(
                                model = currentUser.photoUrl,
                                contentDescription = "User profile picture",
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { currentDestination = AppDestinations.SETTINGS },
                                contentScale = ContentScale.Crop,
                                error = painterResource(id = R.drawable.ic_launcher_foreground),
                                placeholder = painterResource(id = R.drawable.ic_launcher_foreground)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { innerPadding ->
            if (currentNote != null) {
                when (currentDestination) {
                    AppDestinations.TIMELINE -> TimelineScreen(
                        modifier = Modifier.padding(innerPadding),
                        noteRepository = noteRepository,
                        note = currentNote!!,
                        currentMonth = selectedMonth,
                        onMonthChange = { selectedMonth = it },
                        onEditStampClick = { stampId ->
                            editingStampId = stampId
                            currentDestination = AppDestinations.EDIT_STAMP
                        }
                    )

                    AppDestinations.REVIEW -> ReviewScreen(
                        modifier = Modifier.padding(innerPadding),
                        noteRepository = noteRepository,
                        note = currentNote!!,
                        currentMonth = selectedMonth,
                        onMonthChange = { selectedMonth = it }
                    )

                    AppDestinations.STAMP -> StampScreen(
                        modifier = Modifier.padding(innerPadding),
                        noteRepository = noteRepository,
                        note = currentNote!!,
                        onStampSaved = { currentDestination = AppDestinations.TIMELINE }
                    )

                    AppDestinations.EDIT_STAMP -> EditStampScreen(
                        modifier = Modifier.padding(innerPadding),
                        stampId = editingStampId!!,
                        noteRepository = noteRepository,
                        note = currentNote!!,
                        onStampUpdated = {
                            currentDestination = AppDestinations.TIMELINE
                            editingStampId = null
                        }
                    )

                    AppDestinations.NOTE -> NoteScreen(
                        modifier = Modifier.padding(innerPadding),
                        noteRepository = noteRepository,
                        currentUser = currentUser,
                        currentNoteId = currentNote!!.id,
                        onNoteSelected = {
                            currentNote = it
                            appPreferences.saveLastSelectedNote(it)
                            currentDestination = AppDestinations.TIMELINE
                        },
                        onNoteUpdated = { updatedNote ->
                            currentNote = updatedNote
                            appPreferences.saveLastSelectedNote(updatedNote)
                        },
                        onNotesChanged = {
                            refreshNotesTrigger++
                        }
                    )

                    AppDestinations.SETTINGS -> SettingsScreen(
                        modifier = Modifier.padding(innerPadding),
                        currentUser = currentUser,
                        notes = allNotes,
                        onLogoutClick = {
                            Firebase.auth.signOut()
                            appPreferences.clearLastSelectedNote()
                            val intent = Intent(context, AuthActivity::class.java)
                            (context as? Activity)?.startActivity(intent)
                            (context as? Activity)?.finish()
                        },
                        onLoginClick = {
                            val intent = Intent(context, AuthActivity::class.java)
                            (context as? Activity)?.startActivity(intent)
                            (context as? Activity)?.finish()
                        },
                        onTermsClick = { currentDestination = AppDestinations.TERMS },
                        onPrivacyPolicyClick = { currentDestination = AppDestinations.PRIVACY_POLICY },
                        onCsvExportClick = { note ->
                            noteToExport = note
                            val fileName = "ryoiku_memo_${note.name}.csv"
                            createDocumentLauncher.launch(fileName)
                        },
                        onCsvImportClick = { note ->
                            noteToImport = note
                            importContentLauncher.launch("text/*")
                        },
                        onRefreshNotes = {
                            refreshNotesTrigger++
                        }
                    )

                    AppDestinations.TERMS -> TermsScreen(
                        modifier = Modifier.padding(innerPadding),
                        onNavigateUp = { currentDestination = AppDestinations.SETTINGS }
                    )

                    AppDestinations.PRIVACY_POLICY -> PrivacyPolicyScreen(
                        modifier = Modifier.padding(innerPadding),
                        onNavigateUp = { currentDestination = AppDestinations.SETTINGS }
                    )

                    AppDestinations.GRAPH -> {}
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector?,
) {
    TIMELINE("タイムライン", Icons.Default.Timeline),
    STAMP("スタンプ", Icons.Default.AccessTime),
    REVIEW("ふりかえり", Icons.Default.AutoStories),
    NOTE("ノート", null),
    SETTINGS("設定", Icons.Default.Settings),
    EDIT_STAMP("スタンプ編集", null),
    TERMS("利用規約", null),
    PRIVACY_POLICY("プライバシーポリシー", null),
    GRAPH("グラフ", null)
}
