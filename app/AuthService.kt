// api/AuthService.kt
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthService {
    @POST("api/register")
    fun registerUser(@Body request: RegisterRequest): Call<Void> // Или другой тип, если сервер что-то возвращает
}
