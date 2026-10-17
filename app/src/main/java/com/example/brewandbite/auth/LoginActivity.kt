package com.example.brewandbite.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.DialogInterface
import android.os.Bundle
import android.telephony.PhoneNumberFormattingTextWatcher
import android.text.InputType
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
import com.example.brewandbite.databinding.DialogTwoFieldsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 로그인 화면 (휴대폰 번호 + 비밀번호). 로그인에 성공하면 RESULT_OK 로 닫힌다.
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
        binding.editPhone.addTextChangedListener(PhoneNumberFormattingTextWatcher("KR"))   // 010-1234-5678 자동 하이픈
        binding.buttonSubmit.setOnClickListener { submit() }
        binding.textForgot.setOnClickListener { showForgotDialog() }
        binding.googleBox.buttonGoogle.setOnClickListener { startGoogle() }
        binding.textSwitch.setOnClickListener {
            signUpLauncher.launch(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun submit() {
        val phone = binding.editPhone.text?.toString()?.trim().orEmpty()
        val password = binding.editPassword.text?.toString().orEmpty()

        // 입력 확인
        binding.layoutPhone.error = null
        binding.layoutPassword.error = null
        var ok = true
        if (!isPhone(phone)) {
            binding.layoutPhone.error = getString(R.string.phone_err_format); ok = false
        }
        if (password.length < 6) {
            binding.layoutPassword.error = getString(R.string.auth_err_password); ok = false
        }
        if (!ok) return

        setLoading(true)
        AuthManager.signInWithPhone(phone, password) { error ->
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

    /** Google 계정으로 계속하기: 이미 가입했으면 바로 로그인, 처음이면 추가 정보 입력 화면 */
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
                    needsProfile -> signUpLauncher.launch(SignUpActivity.googleIntent(this, account))
                    else -> {
                        Toast.makeText(this, R.string.auth_login_success, Toast.LENGTH_SHORT).show()
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                }
            }
        }
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.visibility = View.VISIBLE
    }

    private fun isPhone(phone: String) = Regex("^01[016789]\\d{7,8}$").matches(phone.filter { it.isDigit() })

    /** 비밀번호 찾기: 휴대폰 번호 입력 → 가입한 이메일로 재설정 메일 발송 */
    private fun showForgotDialog() {
        val d = DialogTwoFieldsBinding.inflate(layoutInflater)
        d.layoutFirst.hint = getString(R.string.phone_hint)
        d.editFirst.inputType = InputType.TYPE_CLASS_PHONE
        d.editFirst.addTextChangedListener(PhoneNumberFormattingTextWatcher("KR"))
        d.editFirst.setText(binding.editPhone.text?.toString()?.trim().orEmpty())   // 로그인 칸에 쓴 번호 미리 채우기
        d.layoutSecond.visibility = View.GONE

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auth_forgot_title)
            .setMessage(R.string.auth_forgot_desc)
            .setView(d.root)
            .setNegativeButton(R.string.cart_cancel, null)
            .setPositiveButton(R.string.auth_forgot_send, null)   // 아래에서 직접 처리 (틀리면 안 닫히게)
            .show()

        val sendButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        sendButton.setOnClickListener {
            val phone = d.editFirst.text?.toString()?.trim().orEmpty()
            if (!isPhone(phone)) {
                d.layoutFirst.error = getString(R.string.phone_err_format)
                return@setOnClickListener
            }
            d.layoutFirst.error = null
            sendButton.isEnabled = false
            AuthManager.sendPasswordResetByPhone(phone) { maskedEmail, error ->
                sendButton.isEnabled = true
                if (maskedEmail != null) {
                    dialog.dismiss()
                    binding.editPhone.setText(phone)
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.auth_forgot_sent_title)
                        .setMessage(getString(R.string.auth_forgot_sent, maskedEmail))
                        .setPositiveButton(R.string.auth_ok, null)
                        .show()
                } else {
                    d.layoutFirst.error = error
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.buttonSubmit.isEnabled = !loading
        binding.buttonSubmit.text = getString(if (loading) R.string.auth_loading else R.string.auth_login_button)
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.googleBox.buttonGoogle.isEnabled = !loading
        if (loading) binding.textError.visibility = View.GONE
    }

    companion object {
        fun intent(context: Context) = Intent(context, LoginActivity::class.java)
    }
}