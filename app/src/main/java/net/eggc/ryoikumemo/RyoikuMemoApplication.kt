package net.eggc.ryoikumemo

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestore

class RyoikuMemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        configureFirestore()
    }

    private fun configureFirestore() {
        val db = Firebase.firestore

        // Persist cache on device storage so data survives app restarts.
        val cacheSettings = PersistentCacheSettings.newBuilder().build()

        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(cacheSettings)
            .build()

        db.firestoreSettings = settings
    }
}