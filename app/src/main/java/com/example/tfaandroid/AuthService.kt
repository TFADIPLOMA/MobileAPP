package com.example.tfaandroid

// api/AuthService.kt
import android.R
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthService {
    @POST("/api/Auth/Registration")
    fun register(@Body request: RegisterRequest): Call<RegistrationResponse>

    @POST("/api/Auth/Login")
    fun login(@Body request:LoginRequest):Call<Void>

    @POST("/api/Auth/VerifyEmailCode")
    fun verifyEmailCode(@Body request:VerifyCodeRequest):Call<LoginResponse>

    @GET("/api/Auth/refresh")
    fun refresh(
        @Header("Authorization") authHeader: String
    ):Call<LoginResponse>

    @POST("/api/Auth/VerifyQrCode")
    fun verifyQrCode(@Body request:VerifyCodeRequest):Call<Void>
}

data class RegistrationResponse(
    val success: Boolean,
    val message: String?,
    val userId: String?,
    val token: String?
)

data class LoginRequest(
    val email:String,
    val password:String
)

data class VerifyCodeRequest(
    val email: String,
    val code:String
)

data class UserDto (
    val id:String,
    val email:String,
    val firstName: String,
    val lastName: String
)

data class LoginResponse(
    val user: UserDto,
    val accessToken:String,
    val refreshToken:String
)