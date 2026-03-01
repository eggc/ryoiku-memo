package net.eggc.ryoikumemo.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirestoreNoteRepositoryTest {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var repository: FirestoreNoteRepository

    private val host = if (System.getProperty("java.runtime.name")?.contains("Android") == true) {
        "10.0.2.2"
    } else {
        "127.0.0.1"
    }

    @Before
    fun setUp() {
        db = Firebase.firestore
        try {
            db.useEmulator(host, 8080)
        } catch (_: IllegalStateException) {
            println("Firestore emulator already configured.")
        }

        val memoryCacheSettings = MemoryCacheSettings.newBuilder().build()
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(memoryCacheSettings)
            .build()
        db.firestoreSettings = settings

        auth = Firebase.auth
        try {
            auth.useEmulator(host, 9099)
        } catch (_: IllegalStateException) {
            println("Auth emulator already configured.")
        }

        repository = FirestoreNoteRepository(db, auth)
    }

    @Test
    fun getNotes_returnsEmptyList_whenNoNotesExist() = runBlocking {
        val notes = repository.getNotes()
        assertTrue("Initially notes should be empty", notes.isEmpty())
    }
}
