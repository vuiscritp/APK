package com.aidev.assistant

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

class AIDevApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        // Enable offline persistence
        FirebaseDatabase.getInstance("https://ai-api-project-1-default-rtdb.firebaseio.com/")
            .setPersistenceEnabled(true)
    }
}
