package com.example.brewandbite.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.brewandbite.R
import com.example.brewandbite.common.applyStatusBarPadding
import com.example.brewandbite.databinding.ActivityLoginBinding

/**
 * 로그인 화면. 로그인에 성공하면 RESULT_OK 로 닫힌다.
 *
 * 다른 화면에서 쓰는 법 (로그인 후 할 일이 있을 때):
 *   private val loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
 *       if (it.resultCode == Activity.RESULT_OK) { 로그인 후 할 일 }
 *   }
 *   loginLauncher.launch(LoginActivity.intent(requireContext()))
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    /** 회원가입 화면에서 가입까지 끝나면 로그인 화면도 바로 닫는다 */
    private val signUpLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
        binding.headerBox.applyStatusBarPadding()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        binding.buttonClose.setOnClickListener { finish() }
        binding.buttonSubmit.setOnClickListener { submit() }
        binding.textSwitch.setOnClickListener {
            signUpLauncher.launch(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun submit() {
        val email = binding.editEmail.text?.toString()?.trim().orEmpty()
        val password = binding.editPassword.text?.toString().orEmpty()

        // 입력 확인
        binding.layoutEmail.error = null
        binding.layoutPassword.error = null
        var ok = true
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.layoutEmail.error = getString(R.string.auth_err_email); ok = false
        }
        if (password.length < 6) {
            binding.layoutPassword.error = getString(R.string.auth_err_password); ok = false
        }
        if (!ok) return

        setLoading(true)
        AuthManager.signIn(email, password) { error ->
            setLoading(false)
            if (error == null) {
                Toast.makeText(this, R.string.auth_login_success, Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                binding.textError.text = error
                binding.textError.visibility = View.VISIBLE
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.buttonSubmit.isEnabled = !loading
        binding.buttonSubmit.text = getString(if (loading) R.string.auth_loading else R.string.auth_login_button)
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) binding.textError.visibility = View.GONE
    }

    companion object {
        fun intent(context: Context) = Intent(context, LoginActivity::class.java)
    }
}