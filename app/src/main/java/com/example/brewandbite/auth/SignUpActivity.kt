package com.example.brewandbite.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
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
 *
 * Google 모드: 'Google 계정으로 계속하기' 로 처음 온 사람은 이메일·비밀번호 칸 없이
 * 이름 · 휴대폰 · 주소만 입력한다. (끝내지 않고 나가면 로그아웃)
 */
class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var phoneVerifier: PhoneVerifier

    private var zipCode = ""          // 주소 검색에서 받은 우편번호
    private var googleMode = false    // Google 계정으로 가입 중인지
    private var completed = false     // 가입을 끝까지 마쳤는지

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
        binding.googleBox.buttonGoogle.setOnClickListener { startGoogle() }

        // 로그인 화면에서 Google 로 들어온 경우
        if (intent.getBooleanExtra(EXTRA_GOOGLE, false)) {
            enterGoogleMode(
                intent.getStringExtra(EXTRA_NAME).orEmpty(),
                intent.getStringExtra(EXTRA_EMAIL).orEmpty()
            )
        }

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
        // Google 로 로그인만 하고 추가 정보를 안 넣었으면 로그아웃 (반쪽 가입 방지)
        if (googleMode && isFinishing && !completed) AuthManager.signOut()
        super.onDestroy()
    }

    // ───────── Google 계정으로 가입 ─────────

    private fun startGoogle() {
        binding.textError.visibility = View.GONE
        GoogleSignInHelper.pickAccount(this) { account, error ->
            if (account == null) {
                if (error != null) showError(error)
                return@pickAccount
            }
            setLoading(true)
            AuthManager.signInWithGoogle(account) { needsProfile, signInError ->
                setLoading(false)
                when {
                    signInError != null -> showError(signInError)
                    needsProfile -> enterGoogleMode(account.name, account.email)
                    else -> finishWithSuccess(account.name)   // 이미 가입한 Google 계정 → 바로 로그인
                }
            }
        }
    }

    /** 이메일·비밀번호 칸을 숨기고, 연결된 Google 계정을 보여 준다 */
    private fun enterGoogleMode(name: String, email: String) {
        googleMode = true
        binding.textTitle.setText(R.string.google_profile_title)
        binding.textDesc.setText(R.string.google_profile_desc)
        binding.textGoogleAccount.text = getString(R.string.google_connected, email)
        binding.textGoogleAccount.visibility = View.VISIBLE
        binding.layoutEmail.visibility = View.GONE
        binding.layoutPassword.visibility = View.GONE
        binding.layoutPasswordConfirm.visibility = View.GONE
        binding.googleBox.root.visibility = View.GONE
        binding.buttonSubmit.setText(R.string.google_profile_submit)
        if (binding.editName.text.isNullOrBlank()) binding.editName.setText(name)
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.visibility = View.VISIBLE
    }

    private fun finishWithSuccess(name: String) {
        completed = true
        Toast.makeText(this, getString(R.string.auth_welcome, name), Toast.LENGTH_SHORT).show()
        setResult(Activity.RESULT_OK)
        finish()
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
        if (!googleMode) {   // Google 모드는 이메일·비밀번호가 필요 없음
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.layoutEmail.error = getString(R.string.auth_err_email); ok = false
            }
            if (password.length < 6) { binding.layoutPassword.error = getString(R.string.auth_err_password); ok = false }
            if (confirm != password) {
                binding.layoutPasswordConfirm.error = getString(R.string.auth_err_password_match); ok = false
            }
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
        val onDone: (String?) -> Unit = { error ->
            setLoading(false)
            if (error == null) finishWithSuccess(name) else showError(error)
        }
        if (googleMode) AuthManager.completeGoogleProfile(profile, onDone)
        else AuthManager.signUp(email, password, profile, onDone)
    }

    private fun setLoading(loading: Boolean) {
        binding.buttonSubmit.isEnabled = !loading
        binding.buttonSubmit.text = getString(
            when {
                loading -> R.string.auth_loading
                googleMode -> R.string.google_profile_submit
                else -> R.string.auth_signup_button
            }
        )
        binding.googleBox.buttonGoogle.isEnabled = !loading
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) binding.textError.visibility = View.GONE
    }

    companion object {
        private const val EXTRA_GOOGLE = "google_mode"
        private const val EXTRA_NAME = "google_name"
        private const val EXTRA_EMAIL = "google_email"

        /** 로그인 화면에서 Google 로 처음 온 사람 → 추가 정보 입력 */
        fun googleIntent(context: Context, account: GoogleAccount) =
            Intent(context, SignUpActivity::class.java)
                .putExtra(EXTRA_GOOGLE, true)
                .putExtra(EXTRA_NAME, account.name)
                .putExtra(EXTRA_EMAIL, account.email)
    }
}