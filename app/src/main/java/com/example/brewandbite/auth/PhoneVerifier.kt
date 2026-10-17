package com.example.brewandbite.auth

import android.app.Activity
import android.os.CountDownTimer
import android.telephony.PhoneNumberFormattingTextWatcher
import android.view.View
import com.example.brewandbite.R
import com.example.brewandbite.databinding.LayoutPhoneVerifyBinding
import com.google.android.material.snackbar.Snackbar
import kotlin.random.Random

/**
 * 휴대폰 인증 (데모 방식) 을 layout_phone_verify.xml 에 연결해 주는 도우미.
 *
 * 실제 문자 발송은 유료(Firebase Blaze)라서, 학교 프로젝트에서는 앱이 직접 인증번호를 만들고
 * 화면 위에 '문자 알림'처럼 보여 준다. 흐름은 실제 문자 인증과 똑같다.
 *
 *  1) 번호 입력 → [인증번호 받기] → 6자리 인증번호 생성, 📩 알림으로 보여 줌, 인증번호 칸 + 2분 타이머
 *  2) 인증번호 입력 → [확인] → 맞으면 '✓ 휴대폰 인증 완료' + onVerified(번호)
 *     (틀리면 다시, 5번 틀리거나 2분이 지나면 재전송)
 *
 * @param onVerified 인증이 끝났을 때 (번호 예: "010-1234-5678")
 */
class PhoneVerifier(
    private val activity: Activity,
    private val v: LayoutPhoneVerifyBinding,
    private val onVerified: (phone: String) -> Unit = {}
) {
    private var code: String? = null        // 지금 유효한 인증번호
    private var sentPhone = ""              // 인증번호를 보낸 번호 (숫자만)
    private var wrongCount = 0
    private var timer: CountDownTimer? = null

    /** 인증을 마친 번호 (안 했으면 null) */
    var verifiedPhone: String? = null
        private set

    val isVerified: Boolean get() = verifiedPhone != null

    init {
        v.editPhone.addTextChangedListener(PhoneNumberFormattingTextWatcher("KR"))   // 01012345678 → 010-1234-5678
        v.buttonSendCode.setOnClickListener { sendCode() }
        v.buttonConfirmCode.setOnClickListener { confirmCode() }
    }

    /** 처음에 보여 줄 번호 (My Page에서 지금 번호 채우기) */
    fun prefill(phone: String) {
        v.editPhone.setText(phone)
    }

    /** 화면이 닫힐 때 꼭 호출 (타이머 정리) */
    fun release() {
        timer?.cancel()
    }

    // ───────── 1) 인증번호 보내기 ─────────

    private fun sendCode() {
        v.layoutPhone.error = null
        val digits = v.editPhone.text?.toString().orEmpty().filter { it.isDigit() }
        if (!PHONE_REGEX.matches(digits)) {
            v.layoutPhone.error = activity.getString(R.string.phone_err_format)
            return
        }

        val newCode = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
        code = newCode
        sentPhone = digits
        wrongCount = 0

        v.buttonSendCode.setText(R.string.phone_resend)
        v.codeGroup.visibility = View.VISIBLE
        v.editCode.text = null
        v.layoutCode.error = null
        v.editCode.requestFocus()
        startTimer()
        showSmsBanner(newCode)
    }

    /** 문자가 온 것처럼 화면 아래에 알림 표시 ([입력] 을 누르면 자동으로 채워짐) */
    private fun showSmsBanner(newCode: String) {
        Snackbar.make(v.root, activity.getString(R.string.phone_sms_message, newCode), Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.phone_sms_fill) { v.editCode.setText(newCode) }
            .setDuration(15_000)
            .show()
    }

    // ───────── 2) 인증번호 확인 ─────────

    private fun confirmCode() {
        v.layoutCode.error = null
        val input = v.editCode.text?.toString().orEmpty().trim()
        val current = code
        when {
            current == null -> v.layoutCode.error = activity.getString(
                if (sentPhone.isEmpty()) R.string.phone_err_send_first else R.string.phone_err_expired
            )
            v.editPhone.text.toString().filter { it.isDigit() } != sentPhone ->
                v.layoutPhone.error = activity.getString(R.string.phone_err_changed)
            input.length != 6 -> v.layoutCode.error = activity.getString(R.string.phone_err_code)
            input != current -> {
                wrongCount++
                if (wrongCount >= MAX_WRONG) {
                    code = null
                    timer?.cancel()
                    v.layoutCode.helperText = null
                    v.layoutCode.error = activity.getString(R.string.phone_err_too_many_wrong)
                } else {
                    v.layoutCode.error = activity.getString(R.string.phone_err_wrong, MAX_WRONG - wrongCount)
                }
            }
            else -> markVerified()
        }
    }

    // ───────── 3) 인증 완료 ─────────

    private fun markVerified() {
        timer?.cancel()
        code = null
        val phone = formatPhone(sentPhone)
        verifiedPhone = phone
        v.editPhone.setText(phone)
        v.editPhone.isEnabled = false
        v.buttonSendCode.visibility = View.GONE
        v.codeGroup.visibility = View.GONE
        v.textVerified.visibility = View.VISIBLE
        onVerified(phone)
    }

    /** 남은 시간 2:00 → 0:00, 시간이 지나면 인증번호 무효 */
    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(TIMEOUT_SEC * 1000, 1000) {
            override fun onTick(left: Long) {
                val sec = left / 1000
                v.layoutCode.helperText = activity.getString(R.string.phone_time_left, sec / 60, sec % 60)
            }

            override fun onFinish() {
                code = null
                v.layoutCode.helperText = activity.getString(R.string.phone_err_expired)
            }
        }.start()
    }

    companion object {
        private const val TIMEOUT_SEC = 120L
        private const val MAX_WRONG = 5

        /** 010 / 011 / 016 / 017 / 018 / 019 로 시작하는 10~11자리 */
        private val PHONE_REGEX = Regex("^01[016789]\\d{7,8}$")

        fun formatPhone(digits: String): String = when (digits.length) {
            11 -> "${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7)}"
            10 -> "${digits.substring(0, 3)}-${digits.substring(3, 6)}-${digits.substring(6)}"
            else -> digits
        }
    }
}