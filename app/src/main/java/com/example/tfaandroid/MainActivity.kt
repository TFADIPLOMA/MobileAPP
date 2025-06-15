package com.example.tfaandroid

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {
    private lateinit var authPrefs: AuthPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        authPrefs = MyApp.get(this).authPrefs

        val refreshToken = authPrefs.refreshToken
        Log.i("Refresh token", refreshToken.toString());
        if (!refreshToken.isNullOrBlank()) {
            RetrofitClient.authService.refresh("Bearer $refreshToken")
                .enqueue(object : Callback<LoginResponse?> {
                    override fun onResponse(
                        call: Call<LoginResponse?>,
                        response: Response<LoginResponse?>
                    ) {

                        if (response.isSuccessful && response.body() != null) {
                            val loginResponse = response.body()!!
                            Log.i("Refresh response", Gson().toJson(loginResponse));
                            // сохраняем новые токены и пользователя
                            authPrefs.accessToken = loginResponse.accessToken
                            authPrefs.refreshToken = loginResponse.refreshToken
                            authPrefs.userJson = Gson().toJson(loginResponse.user)


                        } else {
                            // refreshToken невалиден — редирект на авторизацию
                            redirectToLogin()
                        }
                    }

                    override fun onFailure(call: Call<LoginResponse?>, t: Throwable) {
                        // ошибка сети или сервера — тоже редирект на авторизацию
                        redirectToLogin()
                    }
                })
        } else {
            redirectToLogin()
        }







        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, GeneralFragment())
                .commit()
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNav.setOnItemSelectedListener { item ->
            val selectedFragment = when (item.itemId) {
                R.id.nav_general -> GeneralFragment()
                R.id.nav_documents -> DocumentsFragment()
                R.id.nav_scanner -> ScannerFragment()
                R.id.nav_login -> LoginFragment()
                else -> null
            }

            selectedFragment?.let {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, it)
                    .commit()
                true
            } ?: false
        }
    }

    private fun redirectToLogin() {

    }
}