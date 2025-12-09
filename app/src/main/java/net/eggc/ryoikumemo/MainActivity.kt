package net.eggc.ryoikumemo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Assessment
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
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import net.eggc.ryoikumemo.data.AppPreferences
import net.eggc.ryoikumemo.data.FirestoreNoteRepository
import net.eggc.ryoikumemo.data.Note
import net.eggc.ryoikumemo.data.NoteRepository
import net.eggc.ryoikumemo.data.SharedPreferencesNoteRepository
import net.eggc.ryoikumemo.ui.theme.RyoikumemoTheme

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
    val appPreferences = remember { AppPreferences(context) }

    LaunchedEffect(noteRepository) {
        coroutineScope.launch {
            // Try to load the last selected note from preferences
            val lastNote = appPreferences.getLastSelectedNote()
            if (lastNote != null) {
                currentNote = lastNote
            } else {
                // If no note is in preferences, fall back to the first note from the repository
                val notes = noteRepository.getNotes()
                if (notes.isNotEmpty()) {
                    currentNote = notes.first()
                } else {
                    // If there are no notes at all, create a new one
                    currentNote = noteRepository.createNote("ノート1")
                }
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
                                    .size(32.dp)
                                    .clip(CircleShape),
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
                        onEditStampClick = { stampId ->
                            editingStampId = stampId
                            currentDestination = AppDestinations.EDIT_STAMP
                        }
                    )

                    AppDestinations.GRAPH -> GraphScreen(
                        modifier = Modifier.padding(innerPadding),
                        noteRepository = noteRepository,
                        note = currentNote!!
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
                        currentNoteId = currentNote!!.id,
                        onNoteSelected = {
                            currentNote = it
                            appPreferences.saveLastSelectedNote(it)
                            currentDestination = AppDestinations.TIMELINE
                        },
                        onNoteUpdated = { updatedNote ->
                            currentNote = updatedNote
                            appPreferences.saveLastSelectedNote(updatedNote)
                        }
                    )

                    AppDestinations.SETTINGS -> SettingsScreen(
                        modifier = Modifier.padding(innerPadding),
                        currentUser = currentUser,
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
                        }
                    )
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
    GRAPH("グラフ", Icons.Default.Assessment),
    STAMP("スタンプ", Icons.Default.AccessTime),
    NOTE("ノート", null),
    SETTINGS("設定", Icons.Default.Settings),
    EDIT_STAMP("スタンプ編集", null)
}
