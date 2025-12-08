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
import net.eggc.ryoikumemo.data.FirestoreTimelineRepository
import net.eggc.ryoikumemo.data.Note
import net.eggc.ryoikumemo.data.SharedPreferencesTimelineRepository
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
    val timelineRepository = remember {
        if (currentUser != null) {
            FirestoreTimelineRepository()
        } else {
            SharedPreferencesTimelineRepository(context)
        }
    }
    var currentNote by remember { mutableStateOf<Note?>(null) }
    val appPreferences = remember { AppPreferences(context) }

    LaunchedEffect(timelineRepository) {
        coroutineScope.launch {
            val notes = timelineRepository.getNotes()
            if (notes.isNotEmpty()) {
                val lastSelectedNoteId = appPreferences.getLastSelectedNoteId()
                currentNote = notes.find { it.id == lastSelectedNoteId } ?: notes.first()
            } else {
                currentNote = timelineRepository.createNote("ノート1")
            }
            currentNote?.let { appPreferences.saveLastSelectedNoteId(it.id) }
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
                        timelineRepository = timelineRepository,
                        noteId = currentNote!!.id,
                        onEditStampClick = { stampId ->
                            editingStampId = stampId
                            currentDestination = AppDestinations.EDIT_STAMP
                        }
                    )

                    AppDestinations.STAMP -> StampScreen(
                        modifier = Modifier.padding(innerPadding),
                        timelineRepository = timelineRepository,
                        noteId = currentNote!!.id,
                        onStampSaved = { currentDestination = AppDestinations.TIMELINE }
                    )

                    AppDestinations.EDIT_STAMP -> EditStampScreen(
                        modifier = Modifier.padding(innerPadding),
                        stampId = editingStampId!!,
                        timelineRepository = timelineRepository,
                        noteId = currentNote!!.id,
                        onStampUpdated = {
                            currentDestination = AppDestinations.TIMELINE
                            editingStampId = null
                        }
                    )

                    AppDestinations.NOTE -> NoteScreen(
                        modifier = Modifier.padding(innerPadding),
                        timelineRepository = timelineRepository,
                        currentNoteId = currentNote!!.id,
                        onNoteSelected = {
                            currentNote = it
                            appPreferences.saveLastSelectedNoteId(it.id)
                            currentDestination = AppDestinations.TIMELINE
                        },
                        onNoteUpdated = { updatedNote ->
                            currentNote = updatedNote
                            appPreferences.saveLastSelectedNoteId(updatedNote.id)
                        }
                    )

                    AppDestinations.SETTINGS -> SettingsScreen(
                        modifier = Modifier.padding(innerPadding),
                        currentUser = currentUser,
                        onLogoutClick = {
                            Firebase.auth.signOut()
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
    STAMP("スタンプ", Icons.Default.AccessTime),
    NOTE("ノート", null),
    SETTINGS("設定", Icons.Default.Settings),
    EDIT_STAMP("スタンプ編集", null)
}
