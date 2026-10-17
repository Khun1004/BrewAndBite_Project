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
import com.example.brewandbite.address.AddressSearchActivity
import com.example.brewandbite.common.applyStatusBarPadding
import com.example.brewandbite.databinding.ActivitySignupBinding
import com.google.android.material.textfield.TextInputLayout

/**
 * 회원가입 화면: 이름 · 이메일 · 비밀번호 · 휴대폰(인증번호 확인) · 배달 주소(주소 검색).
 * 가입에 성공하면 자동으로 로그인된 상태가 되고, RESULT_OK 로 닫힌다.
 */
class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var phoneVerifier: PhoneVerifier

    private var zipCode = ""          // 주소 검색에서 받은 우편번호

    /** 주소 검색 화면에서 주소를 고르면 칸에 채우고, 상세 주소 칸으로 이동 */
    private val addressLauncher = registerForActivityResult(AddressSearchActivity.Contract()) { result ->
        if (result == null) return@registerForActivityResult
        zipCode = result.zipCode
        binding.editAddress.setText(result.address)
        binding.layoutAddress.error = null
        binding.layoutAddress.helperText = getString(R.string.addr_zip, result.zipCode)
        binding.editAddressDetail.requestFocus()
    }

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

        // 휴대폰 인증 (인증번호 받기 → 입력 → 확인)
        phoneVerifier = PhoneVerifier(
            activity = this,
            v = binding.phoneVerify,
            onVerified = { Toast.makeText(this, R.string.phone_verified, Toast.LENGTH_SHORT).show() }
        )

        // 배달 주소: 칸이나 돋보기를 누르면 주소 검색 화면
        val openSearch = View.OnClickListener { addressLauncher.launch(Unit) }
        binding.editAddress.setOnClickListener(openSearch)
        binding.layoutAddress.setEndIconOnClickListener(openSearch)
    }

    override fun onDestroy() {
        phoneVerifier.release()
        super.onDestroy()
    }

    private fun text(layout: TextInputLayout): String = layout.editText?.text?.toString()?.trim().orEmpty()

    private fun submit() {
        val name = text(binding.layoutName)
        val email = text(binding.layoutEmail)
        val password = binding.editPassword.text?.toString().orEmpty()
        val confirm = binding.editPasswordConfirm.text?.toString().orEmpty()
        val address = text(binding.layoutAddress)
        val addressDetail = text(binding.layoutAddressDetail)

        // 입력 확인 (틀린 칸에 빨간 안내 문구)
        val fields = listOf(
            binding.layoutName, binding.layoutEmail, binding.layoutPassword,
            binding.layoutPasswordConfirm, binding.layoutAddress
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
        val phone = phoneVerifier.verifiedPhone
        if (phone == null) {
            binding.phoneVerify.layoutPhone.error = getString(R.string.phone_err_need_verify); ok = false
        }
        if (address.isEmpty()) { binding.layoutAddress.error = getString(R.string.addr_err_required); ok = false }
        if (!ok || phone == null) return

        val profile = UserProfile(
            name = name, phone = phone, zipCode = zipCode,
            address = address, addressDetail = addressDetail
        )

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