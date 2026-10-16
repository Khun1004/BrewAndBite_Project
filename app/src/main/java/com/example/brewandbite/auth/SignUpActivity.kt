package com.example.brewandbite.auth

import android.app.Activity
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.brewandbite.R
import com.example.brewandbite.common.applyStatusBarPadding
import com.example.brewandbite.databinding.ActivitySignupBinding
import com.google.android.material.textfield.TextInputLayout

/**
 * 회원가입 화면: 이름 · 이메일 · 비밀번호 · 연락처 · 배달 주소.
 * 가입에 성공하면 자동으로 로그인된 상태가 되고, RESULT_OK 로 닫힌다.
 */
class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
        binding.headerBox.applyStatusBarPadding()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        binding.buttonClose.setOnClickListener { finish() }
        binding.textSwitch.setOnClickListener { finish() }   // 이미 회원 → 로그인 화면으로 돌아가기
        binding.buttonSubmit.setOnClickListener { submit() }
    }

    private fun text(layout: TextInputLayout): String = layout.editText?.text?.toString()?.trim().orEmpty()

    private fun submit() {
        val name = text(binding.layoutName)
        val email = text(binding.layoutEmail)
        val password = binding.editPassword.text?.toString().orEmpty()
        val confirm = binding.editPasswordConfirm.text?.toString().orEmpty()
        val phone = text(binding.layoutPhone)
        val address = text(binding.layoutAddress)
        val addressDetail = text(binding.layoutAddressDetail)

        // 입력 확인 (틀린 칸에 빨간 안내 문구)
        val fields = listOf(
            binding.layoutName, binding.layoutEmail, binding.layoutPassword,
            binding.layoutPasswordConfirm, binding.layoutPhone, binding.layoutAddress
        )
        fields.forEach { it.error = null }
        var ok = true
        if (name.isEmpty()) { binding.layoutName.error = getString(R.string.auth_err_required); ok = false }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.layoutEmail.error = getString(R.string.auth_err_email); ok = false
        }
        if (password.length < 6) { binding.layoutPassword.error = getString(R.string.auth_err_password); ok = false }
        if (confirm != password) {
            binding.layoutPasswordConfirm.error = getString(R.string.auth_err_password_match); ok = false
        }
        if (phone.filter { it.isDigit() }.length < 9) {
            binding.layoutPhone.error = getString(R.string.auth_err_phone); ok = false
        }
        if (address.isEmpty()) { binding.layoutAddress.error = getString(R.string.auth_err_required); ok = false }
        if (!ok) return

        val profile = UserProfile(name = name, phone = phone, address = address, addressDetail = addressDetail)

        setLoading(true)
        AuthManager.signUp(email, password, profile) { error ->
            setLoading(false)
            if (error == null) {
                Toast.makeText(this, getString(R.string.auth_welcome, name), Toast.LENGTH_SHORT).show()
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
        binding.buttonSubmit.text = getString(if (loading) R.string.auth_loading else R.string.auth_signup_button)
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) binding.textError.visibility = View.GONE
    }
}