package com.example.brewandbite.address

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.brewandbite.common.applyStatusBarPadding
import com.example.brewandbite.databinding.ActivityAddressSearchBinding

/** 주소 검색 결과 (우편번호 + 도로명/지번 주소) */
data class AddressResult(val zipCode: String, val address: String)

/**
 * 진짜 주소 검색 화면.
 * 카카오(다음) 우편번호 서비스를 WebView 안에 띄운다. (무료 · API 키 필요 없음 · 인터넷 필요)
 *
 * 사용법 (Activity / Fragment 안에서):
 *   private val addressLauncher = registerForActivityResult(AddressSearchActivity.Contract()) { result ->
 *       if (result != null) editAddress.setText(result.address)   // result.zipCode = 우편번호
 *   }
 *   addressLauncher.launch(Unit)
 */
class AddressSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddressSearchBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAddressSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
        binding.headerBox.applyStatusBarPadding()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonRetry.setOnClickListener { loadPostcode() }

        binding.webView.apply {
            settings.javaScriptEnabled = true      // 우편번호 서비스가 자바스크립트로 동작
            settings.domStorageEnabled = true
            addJavascriptInterface(Bridge(), "BrewBite")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    // 우편번호 스크립트를 못 불러오면 (인터넷 끊김) 안내 화면
                    val host = request.url.host.orEmpty()
                    if (request.isForMainFrame || host.contains("daumcdn")) showError(true)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    binding.progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
                }
            }
        }

        // 뒤로가기: 검색 결과 안에서 이동했으면 먼저 웹 페이지 뒤로
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) binding.webView.goBack() else finish()
            }
        })

        loadPostcode()
    }

    private fun loadPostcode() {
        showError(false)
        binding.webView.loadDataWithBaseURL(BASE_URL, POSTCODE_HTML, "text/html", "utf-8", null)
    }

    private fun showError(show: Boolean) {
        binding.errorView.visibility = if (show) View.VISIBLE else View.GONE
        if (show) binding.progress.visibility = View.GONE
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }

    /** 웹 페이지(자바스크립트)에서 주소를 고르면 여기로 들어온다 */
    private inner class Bridge {
        @JavascriptInterface
        fun onComplete(zipCode: String, address: String) {
            runOnUiThread {
                setResult(
                    Activity.RESULT_OK,
                    Intent().putExtra(EXTRA_ZIP, zipCode).putExtra(EXTRA_ADDRESS, address)
                )
                finish()
            }
        }
    }

    /** registerForActivityResult(AddressSearchActivity.Contract()) 로 사용 */
    class Contract : ActivityResultContract<Unit, AddressResult?>() {
        override fun createIntent(context: Context, input: Unit) =
            Intent(context, AddressSearchActivity::class.java)

        override fun parseResult(resultCode: Int, intent: Intent?): AddressResult? {
            if (resultCode != Activity.RESULT_OK || intent == null) return null
            val address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
            if (address.isBlank()) return null
            return AddressResult(intent.getStringExtra(EXTRA_ZIP).orEmpty(), address)
        }
    }

    companion object {
        private const val EXTRA_ZIP = "zip_code"
        private const val EXTRA_ADDRESS = "address"
        private const val BASE_URL = "https://brewandbite.app/"

        /** 카카오(다음) 우편번호 서비스를 화면 가득 넣은 페이지 (Brew & Bite 색으로 꾸밈) */
        private val POSTCODE_HTML = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
              <style>
                html, body { margin: 0; padding: 0; height: 100%; background: #FAF6EF; }
                #wrap { width: 100%; height: 100%; }
              </style>
            </head>
            <body>
              <div id="wrap"></div>
              <script src="https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js"></script>
              <script>
                new daum.Postcode({
                  width: '100%',
                  height: '100%',
                  theme: {
                    bgColor: '#FAF6EF', searchBgColor: '#6B4A36', queryTextColor: '#FFFFFF',
                    outlineColor: '#D9C7A7', emphTextColor: '#6B4A36', postcodeTextColor: '#C9503F'
                  },
                  oncomplete: function (d) {
                    // 사용자가 고른 주소 (도로명 R / 지번 J)
                    var addr = d.userSelectedType === 'R' ? d.roadAddress : d.jibunAddress;
                    if (d.userSelectedType === 'R') {
                      var extra = '';
                      if (d.bname !== '' && /(동|로|가)${'$'}/.test(d.bname)) extra += d.bname;
                      if (d.buildingName !== '' && d.apartment === 'Y') extra += (extra ? ', ' : '') + d.buildingName;
                      if (extra) addr += ' (' + extra + ')';
                    }
                    BrewBite.onComplete(d.zonecode, addr);
                  }
                }).embed(document.getElementById('wrap'), { autoClose: false });
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}