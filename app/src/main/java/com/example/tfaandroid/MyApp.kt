package com.example.tfaandroid

import android.app.Application

class MyApp:Application() {
    lateinit var authPrefs: AuthPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        authPrefs = AuthPreferences(applicationContext)
    }

    companion object {
        fun get(context: android.content.Context): MyApp {
            return context.applicationContext as MyApp
        }
    }
}