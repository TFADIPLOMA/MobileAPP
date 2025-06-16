package com.example.tfaandroid

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import androidx.core.content.edit

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class LoginFragment : Fragment() {

    private var param1: String? = null
    private var param2: String? = null

    private lateinit var editTextLogin: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var buttonLogin: Button
    private lateinit var buttonToRegistration: Button

    private lateinit var editTextCode: EditText
    private lateinit var goToRegistrationLabel: TextView
    private lateinit var buttonVerifyCode: Button

    private var isWaitingCode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_login, container, false)

        editTextLogin = view.findViewById(R.id.editTextLogin)
        editTextPassword = view.findViewById(R.id.editTextPassword)
        buttonLogin = view.findViewById(R.id.buttonLogin)
        buttonToRegistration = view.findViewById(R.id.goToRegistrationBtn)
        goToRegistrationLabel = view.findViewById(R.id.goToRegistrationLabel)

        editTextCode = view.findViewById(R.id.editTextCode)
        buttonVerifyCode = view.findViewById(R.id.buttonVerifyCode)

        val gson = Gson()

        buttonLogin.setOnClickListener {
            if (isWaitingCode) return@setOnClickListener // защита от повторного нажатия

            val login = editTextLogin.text.toString()
            val password = editTextPassword.text.toString()

            if (login.isBlank() || password.isBlank()) {
                Toast.makeText(requireContext(), "Пожалуйста, заполните все поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            RetrofitClient.authService.login(LoginRequest(login,password))
                .enqueue(object:Callback<Void> {
                    override fun onResponse(call: Call<Void?>, response: Response<Void?>) {
                        // переключаем UI для ввода кода
                        isWaitingCode = true

                        setVisibleForLoginForm(View.GONE)
                        setVisibleForRegistration(View.GONE)
                        setVisibleForCodeForm(View.VISIBLE)
                        Toast.makeText(requireContext(), "Код отправлен на почту", Toast.LENGTH_SHORT).show()
                    }

                    override fun onFailure(call: Call<Void?>, t: Throwable) {
                        Toast.makeText(requireContext(), "Ошибка при входе", Toast.LENGTH_SHORT).show()
                    }
                })





        }

        buttonToRegistration.setOnClickListener {
            val intent = Intent(context, RegistrationActivity::class.java)
            startActivity(intent)
        }



        buttonVerifyCode.setOnClickListener {
            val code = editTextCode.text.toString()

            if (code.isBlank()) {
                Toast.makeText(requireContext(), "Введите код", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            RetrofitClient.authService.verifyEmailCode(VerifyCodeRequest(editTextLogin.text.toString(),code))
                .enqueue(object:Callback<LoginResponse>{
                    override fun onResponse(
                        call: Call<LoginResponse?>,
                        response: Response<LoginResponse?>
                    ) {

                        if(response.code() == 200 && response.body() != null) {
                            val authPrefs = MyApp.get(requireContext()).authPrefs
                            val responseBody = response.body()

                            if (responseBody != null) {
                                authPrefs.accessToken = responseBody.accessToken
                                authPrefs.refreshToken = responseBody.refreshToken
                                authPrefs.userJson = gson.toJson(responseBody.user)

                                (activity as? MainActivity)?.updatePermissions()
                            }

                            Toast.makeText(requireContext(), "Код верный", Toast.LENGTH_SHORT).show()

                            Handler(Looper.getMainLooper()).postDelayed({
                                parentFragmentManager.beginTransaction()
                                    .replace(R.id.fragment_container, GeneralFragment())
                                    .commit()
                            }, 2000)
                        }
                    }

                    override fun onFailure(call: Call<LoginResponse?>, t: Throwable) {
                        Toast.makeText(requireContext(), "Ошибка при подтверждении кода", Toast.LENGTH_SHORT).show()
                    }
                })
        }

        return view
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            LoginFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }

    private fun setVisibleForRegistration(mode:Int) {
        buttonToRegistration.visibility = mode
        goToRegistrationLabel.visibility = mode
    }

    private fun setVisibleForLoginForm(mode:Int) {
        editTextLogin.visibility = mode
        editTextPassword.visibility = mode
        buttonLogin.visibility = mode
    }

    private fun setVisibleForCodeForm(mode:Int) {
        editTextCode.visibility = mode
        buttonVerifyCode.visibility = mode
    }
}
