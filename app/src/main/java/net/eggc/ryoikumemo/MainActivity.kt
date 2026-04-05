package net.eggc.ryoikumemo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import net.eggc.ryoikumemo.ui.AppDestinations
import net.eggc.ryoikumemo.ui.MainViewModel
import net.eggc.ryoikumemo.ui.feature.note.NoteScreen
import net.eggc.ryoikumemo.ui.feature.review.ReviewScreen
import net.eggc.ryoikumemo.ui.feature.settings.PrivacyPolicyScreen
import net.eggc.ryoikumemo.ui.feature.settings.SettingsScreen
import net.eggc.ryoikumemo.ui.feature.settings.TermsScreen
import net.eggc.ryoikumemo.ui.feature.stamp.StampAddScreen
import net.eggc.ryoikumemo.ui.feature.stamp.StampEditScreen
import net.eggc.ryoikumemo.ui.feature.task.TaskScreen
import net.eggc.ryoikumemo.ui.feature.timeline.TimelineScreen
import net.eggc.ryoikumemo.ui.theme.RyoikumemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RyoikumemoTheme {
                val context = LocalContext.current
                val viewModel = remember { MainViewModel(context) }
                RyoikumemoApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RyoikumemoApp(viewModel: MainViewModel) {
    val currentDestination by viewModel.currentDestination.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val noteRepository by viewModel.noteRepository.collectAsState()
    val timelineRepository by viewModel.timelineRepository.collectAsState()
    val taskRepository by viewModel.taskRepository.collectAsState()
    val currentNote by viewModel.currentNote.collectAsState()
    val allNotes by viewModel.allNotes.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val editingStamp by viewModel.editingStamp.collectAsState()

    val context = LocalContext.current

    // システムの戻るボタンをハンドリング
    BackHandler(enabled = currentDestination != AppDestinations.TIMELINE) {
        viewModel.popBackStack()
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
                        viewModel.navigateTo(dest)
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (currentDestination != AppDestinations.EDIT_STAMP && currentDestination != AppDestinations.STAMP_ADD) {
                    TopAppBar(
                        title = {
                            Row(
                                modifier = Modifier.clickable { viewModel.navigateTo(AppDestinations.NOTE) },
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
                                    model = currentUser!!.photoUrl,
                                    contentDescription = "User profile picture",
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable { viewModel.navigateTo(AppDestinations.SETTINGS) },
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
            }
        ) { innerPadding ->
            if (currentNote != null) {
                when (currentDestination) {
                    AppDestinations.TIMELINE -> TimelineScreen(
                        modifier = Modifier.padding(innerPadding),
                        timelineRepository = timelineRepository,
                        note = currentNote!!,
                        currentMonth = selectedMonth,
                        onMonthChange = { viewModel.setMonth(it) },
                        onEditStampClick = { stampId ->
                            viewModel.setEditingStampById(stampId)
                        },
                        onAddStampClick = {
                            viewModel.navigateTo(AppDestinations.STAMP_ADD)
                        }
                    )

                    AppDestinations.TASK -> TaskScreen(
                        modifier = Modifier.padding(innerPadding),
                        taskRepository = taskRepository,
                        note = currentNote!!
                    )

                    AppDestinations.REVIEW -> ReviewScreen(
                        modifier = Modifier.padding(innerPadding),
                        timelineRepository = timelineRepository,
                        note = currentNote!!,
                        currentMonth = selectedMonth,
                        onMonthChange = { viewModel.setMonth(it) }
                    )

                    AppDestinations.STAMP_ADD -> StampAddScreen(
                        modifier = Modifier.padding(innerPadding),
                        timelineRepository = timelineRepository,
                        note = currentNote!!,
                        onBack = { viewModel.popBackStack() },
                        onStampSaved = {
                            viewModel.saveStamp(0, "") // dummy call if needed, but normally handled in StampAddScreen
                            viewModel.popBackStack()
                        },
                        onStampSelected = { type ->
                            viewModel.startAddingStamp(type)
                        }
                    )

                    AppDestinations.EDIT_STAMP -> {
                        if (editingStamp != null) {
                            StampEditScreen(
                                stampItem = editingStamp!!,
                                timelineRepository = timelineRepository,
                                note = currentNote!!,
                                onBack = {
                                    viewModel.popBackStack()
                                },
                                onSave = { timestamp, noteText ->
                                    viewModel.saveStamp(timestamp, noteText)
                                    Toast.makeText(context, "保存しました", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    AppDestinations.NOTE -> NoteScreen(
                        modifier = Modifier.padding(innerPadding),
                        noteRepository = noteRepository,
                        currentUser = currentUser,
                        currentNoteId = currentNote!!.id,
                        onNoteSelected = {
                            viewModel.selectNote(it)
                        },
                        onNoteUpdated = { updatedNote ->
                            viewModel.updateCurrentNote(updatedNote)
                        },
                        onNotesChanged = {
                            viewModel.refreshNotes()
                        }
                    )

                    AppDestinations.SETTINGS -> SettingsScreen(
                        modifier = Modifier.padding(innerPadding),
                        currentUser = currentUser,
                        notes = allNotes,
                        noteRepository = noteRepository,
                        timelineRepository = timelineRepository,
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
                        },
                        onTermsClick = { viewModel.navigateTo(AppDestinations.TERMS) },
                        onPrivacyPolicyClick = { viewModel.navigateTo(AppDestinations.PRIVACY_POLICY) },
                        onRefreshNotes = {
                            viewModel.refreshNotes()
                        }
                    )

                    AppDestinations.TERMS -> TermsScreen(
                        modifier = Modifier.padding(innerPadding),
                        onNavigateUp = { viewModel.popBackStack() }
                    )

                    AppDestinations.PRIVACY_POLICY -> PrivacyPolicyScreen(
                        modifier = Modifier.padding(innerPadding),
                        onNavigateUp = { viewModel.popBackStack() }
                    )

                    AppDestinations.GRAPH -> {}
                }
            }
        }
    }
}
