package com.example.tfaandroid

import android.content.Context

class AuthPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) = prefs.edit().putString("access_token", value).apply()

    var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        set(value) = prefs.edit().putString("refresh_token", value).apply()

    var userJson: String?
        get() = prefs.getString("user", null)
        set(value) = prefs.edit().putString("user", value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
