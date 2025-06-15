package com.example.tfaandroid

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS) // ожидание подключения
        .readTimeout(30, TimeUnit.SECONDS)    // ожидание ответа сервера
        .writeTimeout(30, TimeUnit.SECONDS)   // ожидание записи запроса
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.tfadiploma.ru")
        .addConverterFactory(GsonConverterFactory.create())
        .client(okHttpClient) // передаём клиент с таймаутами
        .build()

    val authService: AuthService = retrofit.create(AuthService::class.java)
}