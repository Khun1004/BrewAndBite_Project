package com.example.brewandbite.splash

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.brewandbite.MainActivity
import com.example.brewandbite.R
import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.auth.LoginActivity
import com.example.brewandbite.databinding.ActivitySplashBinding

/**
 * 첫 화면 (스플래시)
 *  - 로고 · 문구 · 특징 칩이 차례로 떠오르고, 커피 사진이 천천히 다가온다
 *  - 로고 뒤 빛이 숨 쉬듯 밝아졌다 어두워진다
 *  - [시작하기] → 홈 / [로그인] → 로그인 후 홈
 *  - 이미 로그인한 상태면 "○○님, 다시 오셨네요" + [주문하러 가기]
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var glowAnimator: ObjectAnimator? = null

    /** 스플래시에서 로그인하면 바로 홈으로 */
    private val loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) goHome()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 검은 배경이라 상태바 아이콘을 밝게. 사진은 화면 끝까지, 글자/버튼만 시스템 바를 피해서
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        val topPad = binding.topContent.paddingTop
        val bottomPad = binding.bottomPanel.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topContent.setPadding(
                binding.topContent.paddingLeft, topPad + bars.top, binding.topContent.paddingRight, 0
            )
            binding.bottomPanel.setPadding(
                binding.bottomPanel.paddingLeft, binding.bottomPanel.paddingTop,
                binding.bottomPanel.paddingRight, bottomPad + bars.bottom
            )
            insets
        }

        styleBrand()
        setupButtons()

        if (savedInstanceState == null) playIntro()
        startGlow()
    }

    override fun onDestroy() {
        glowAnimator?.cancel()
        super.onDestroy()
    }

    /** "Brew & Bite" 에서 & 만 작고 크림색으로 */
    private fun styleBrand() {
        val text = "Brew\n&\nBite"
        val amp = text.indexOf('&')
        binding.textBrand.text = SpannableString(text).apply {
            setSpan(RelativeSizeSpan(0.55f), amp, amp + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this@SplashActivity, R.color.bb_cream)),
                amp, amp + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun setupButtons() {
        binding.buttonStart.setOnClickListener { goHome() }

        if (AuthManager.isLoggedIn()) {
            // 이미 로그인: 인사 + [주문하러 가기]
            val name = AuthManager.currentEmail()?.substringBefore("@").orEmpty()
            binding.buttonStart.setText(R.string.splash_go_order)
            binding.textLoginHint.text = getString(R.string.splash_welcome_back, name)
            binding.textLogin.visibility = View.GONE
        } else {
            binding.textLogin.setOnClickListener { loginLauncher.launch(LoginActivity.intent(this)) }
        }
    }

    private fun goHome() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // ───────── 애니메이션 ─────────

    /** 처음 열 때: 사진이 살짝 커진 상태에서 제자리로, 글자들은 아래에서 차례로 떠오름 */
    private fun playIntro() {
        val rise = 28f * resources.displayMetrics.density
        val ease = DecelerateInterpolator(1.6f)

        binding.imageHero.apply {
            alpha = 0f
            scaleX = 1.08f
            scaleY = 1.08f
            pivotY = 0f
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(1400).setInterpolator(ease).start()
        }

        val sequence = listOf(
            binding.eyebrowRow, binding.textBrand, binding.textTagline,
            binding.chipCoffee, binding.chipBakery, binding.chipDelivery,
            binding.textSlogan, binding.buttonStart, binding.loginRow
        )
        sequence.forEachIndexed { i, view ->
            val endAlpha = view.alpha          // 원래 투명도 (태그라인은 0.8)
            view.alpha = 0f
            view.translationY = rise
            view.animate()
                .alpha(endAlpha)
                .translationY(0f)
                .setStartDelay(200L + i * 110L)
                .setDuration(650)
                .setInterpolator(ease)
                .start()
        }
    }

    /** 로고 뒤 빛: 3초 주기로 천천히 밝아졌다 어두워짐 */
    private fun startGlow() {
        glowAnimator = ObjectAnimator.ofFloat(binding.glow, View.ALPHA, 0.45f, 1f).apply {
            duration = 3000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }
}