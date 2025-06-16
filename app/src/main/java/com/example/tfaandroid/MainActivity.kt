package com.example.tfaandroid

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {
    private lateinit var authPrefs: AuthPreferences

    fun updatePermissions() {
        if(!authPrefs.userJson.isNullOrEmpty()) {
            val user: UserDto = Gson().fromJson(
                authPrefs.userJson,
                UserDto::class.java
            )
            Log.d("FCM Token","UpdatePermission")
            sendFcmTokenToServer()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !user.email.isNullOrEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        authPrefs = MyApp.get(this).authPrefs

        updatePermissions()

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
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .addToBackStack(null)
            .commit()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Log.d("FCM", "Разрешение на уведомления получено")
                sendFcmTokenToServer()
            } else {
                Log.d("FCM", "Разрешение на уведомления отклонено")
            }
        }
    }

    // Получение и отправка токена
    private fun sendFcmTokenToServer() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Log.d("FCM", "FCM Token: $token")
                    // TODO: Отправь токен на свой сервер
                    sendTokenToBackend(token)
                } else {
                    Log.w("FCM", "Ошибка получения токена", task.exception)
                }
            }
    }

    private fun sendTokenToBackend(token: String) {

        Log.d("FCM", "Отправка токена на сервер: $token")

        val user: UserDto = Gson().fromJson(
            authPrefs.userJson,
            UserDto::class.java
        )
        RetrofitClient.authService.saveUserFCMToken(VerifyCodeRequest(user.email,token))
            .enqueue(object: Callback<Void>{
                override fun onResponse(call: Call<Void?>, response: Response<Void?>) {
                    Log.d("FCM", "Токен сохранен на сервере")
                }

                override fun onFailure(call: Call<Void?>, t: Throwable) {
                    Log.d("FCM", "Ошибка сохраненния токена на сервере")
                }
            })
    }
}