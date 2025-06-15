package com.example.tfaandroid

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.Console

class RegistrationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        val firstNameInput = findViewById<EditText>(R.id.editTextFirstName)
        val lastNameInput = findViewById<EditText>(R.id.editTextLastName)
        val emailInput = findViewById<EditText>(R.id.editTextEmail)
        val passwordInput = findViewById<EditText>(R.id.editTextPassword)
        val confirmPasswordInput = findViewById<EditText>(R.id.editTextConfirmPassword)
        val registerButton = findViewById<Button>(R.id.buttonRegister)

        registerButton.setOnClickListener {
            val request = RegisterRequest(
                email = emailInput.text.toString(),
                password = passwordInput.text.toString(),
                confirmPassword = confirmPasswordInput.text.toString(),
                firstName = firstNameInput.text.toString(),
                lastName = lastNameInput.text.toString()
            )

            RetrofitClient.authService.register(request)
                .enqueue(object : Callback<RegistrationResponse> {
                    override fun onResponse(call: Call<RegistrationResponse>, response: Response<RegistrationResponse>) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@RegistrationActivity, "Регистрация успешна", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@RegistrationActivity, "Ошибка регистрации: ${response.code()}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<RegistrationResponse>, t: Throwable) {
                        Log.i("RegistrationActivity",t.localizedMessage)
                        Toast.makeText(this@RegistrationActivity, "Сетевая ошибка: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                })
        }

        val backBtn = findViewById<Button>(R.id.backBtn)
        backBtn.setOnClickListener {
            finish()
        }
    }
}
