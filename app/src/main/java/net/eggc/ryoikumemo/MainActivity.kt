package net.eggc.ryoikumemo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import net.eggc.ryoikumemo.data.FirestoreTimelineRepository
import net.eggc.ryoikumemo.data.Note
import net.eggc.ryoikumemo.data.SharedPreferencesTimelineRepository
import net.eggc.ryoikumemo.ui.theme.RyoikumemoTheme
import java.time.LocalDate
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
    var editingDiaryDate by rememberSaveable { mutableStateOf<String?>(null) }
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

    LaunchedEffect(timelineRepository) {
        coroutineScope.launch {
            val notes = timelineRepository.getNotes()
            if (currentNote == null) {
                currentNote = if (notes.isEmpty()) {
                    timelineRepository.createNote("ノート1")
                } else {
                    notes.first()
                }
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
                        if (dest == AppDestinations.DIARY) {
                            editingDiaryDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        }
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
                        Text(
                            currentNote?.name ?: "",
                            modifier = Modifier.clickable { currentDestination = AppDestinations.NOTE }
                        )
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
                    }
                )
            }
        ) { innerPadding ->
            if (currentNote != null) {
                when (currentDestination) {
                    AppDestinations.TIMELINE -> TimelineScreen(
                        modifier = Modifier.padding(innerPadding),
                        timelineRepository = timelineRepository,
                        noteId = currentNote!!.id,
                        onEditDiaryClick = { date ->
                            editingDiaryDate = date
                            currentDestination = AppDestinations.DIARY
                        },
                        onEditStampClick = { stampId ->
                            editingStampId = stampId
                            currentDestination = AppDestinations.EDIT_STAMP
                        }
                    )

                    AppDestinations.DIARY -> DiaryScreen(
                        modifier = Modifier.padding(innerPadding),
                        date = editingDiaryDate!!,
                        timelineRepository = timelineRepository,
                        noteId = currentNote!!.id,
                        onDiarySaved = {
                            currentDestination = AppDestinations.TIMELINE
                            editingDiaryDate = null
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
                        onNoteSelected = {
                            currentNote = it
                            currentDestination = AppDestinations.TIMELINE
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
    DIARY("日記", Icons.Default.Book),
    STAMP("スタンプ", Icons.Default.AccessTime),
    NOTE("ノート", null),
    SETTINGS("設定", Icons.Default.Settings),
    EDIT_STAMP("スタンプ編集", null)
}
