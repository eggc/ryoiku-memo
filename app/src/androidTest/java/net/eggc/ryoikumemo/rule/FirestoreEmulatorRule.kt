package net.eggc.ryoikumemo.rule

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.rules.ExternalResource
import java.io.IOException

/**
 * Firestore と Auth のエミュレータ設定を共通化し、各テスト前にデータをクリアするための JUnit Rule。
 */
class FirestoreEmulatorRule : ExternalResource() {
    lateinit var db: FirebaseFirestore
        private set
    lateinit var auth: FirebaseAuth
        private set

    private val host = if (System.getProperty("java.runtime.name")?.contains("Android") == true) {
        "10.0.2.2"
    } else {
        "127.0.0.1"
    }

    override fun before() {
        db = Firebase.firestore
        try {
            db.useEmulator(host, 8080)
        } catch (_: IllegalStateException) {
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
        }

        runBlocking {
            clearDatabase()
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
        }
    }

    private suspend fun clearDatabase() = withContext(Dispatchers.IO) {
        val projectId = FirebaseApp.getInstance().options.projectId
        val url = "http://$host:8080/emulator/v1/projects/$projectId/databases/(default)/documents"

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .delete()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("エミュレータのデータ消去に失敗しました: ${response.code} ${response.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FirestoreEmulatorRule", "Failed to clear database", e)
            throw e
        }
    }
}
