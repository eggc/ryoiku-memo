package net.eggc.ryoikumemo.rule

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.firestore
import org.junit.rules.ExternalResource

/**
 * Firestore と Auth のエミュレータ設定を共通化するための JUnit Rule。
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
            // Firestore エミュレータの設定
            db.useEmulator(host, 8080)
        } catch (_: IllegalStateException) {
            // すでに設定済みの場合は無視
        }

        // テスト用に永続化を無効化（メモリキャッシュのみ）
        val memoryCacheSettings = MemoryCacheSettings.newBuilder().build()
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(memoryCacheSettings)
            .build()
        db.firestoreSettings = settings

        auth = Firebase.auth
        try {
            // Auth エミュレータの設定
            auth.useEmulator(host, 9099)
        } catch (_: IllegalStateException) {
            // すでに設定済みの場合は無視
        }
    }
}
