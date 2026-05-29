package net.eggc.ryoikumemo

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.firestore

class RyoikuMemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        configureFirestore()
    }

    private fun configureFirestore() {
        val db = Firebase.firestore

        // Keep frequently visited months in local cache to reduce repeated server reads.
        val cacheSettings = MemoryCacheSettings.newBuilder().build()

        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(cacheSettings)
            .build()

        db.firestoreSettings = settings
    }
}