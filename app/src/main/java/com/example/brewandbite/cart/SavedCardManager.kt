package com.example.brewandbite.cart

import android.content.Context
import android.content.SharedPreferences
import com.example.brewandbite.auth.AuthManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/** 저장된 카드 (브랜드 · 끝 4자리 · 유효기간만. 전체 번호와 CVC는 절대 저장하지 않음) */
object SavedCardManager {

    data class SavedCard(
        val id: String = "",
        val brand: String = "",
        val last4: String = "",
        val expiry: String = "",
        val issuer: String = "",
        val foreign: Boolean = false     // 해외 발행 카드면 true
    ) {
        fun displayName() = if (issuer.isBlank()) "$brand ****$last4" else "$issuer ****$last4"
    }

    /** 카드사 + 카드 그림 색 (진한 색, 밝은 색) */
    class Issuer(val name: String, val dark: Int, val light: Int)

    val ISSUERS = listOf(
        Issuer("신한카드", 0xFF0B2A8C.toInt(), 0xFF2F6BFF.toInt()),
        Issuer("KB국민카드", 0xFF5D4037.toInt(), 0xFFFFB300.toInt()),
        Issuer("삼성카드", 0xFF0D1B3D.toInt(), 0xFF1565C0.toInt()),
        Issuer("현대카드", 0xFF111111.toInt(), 0xFF616161.toInt()),
        Issuer("롯데카드", 0xFF8E0E1B.toInt(), 0xFFE53935.toInt()),
        Issuer("하나카드", 0xFF00695C.toInt(), 0xFF26A69A.toInt()),
        Issuer("우리카드", 0xFF0D47A1.toInt(), 0xFF42A5F5.toInt()),
        Issuer("NH농협카드", 0xFF1B5E20.toInt(), 0xFF66BB6A.toInt()),
        Issuer("BC카드", 0xFFB71C1C.toInt(), 0xFFFF7043.toInt()),
        Issuer("카카오뱅크", 0xFF8D7B00.toInt(), 0xFFFFD600.toInt()),
        Issuer("토스뱅크", 0xFF0050CC.toInt(), 0xFF3182F6.toInt())
    )

    fun issuerOf(name: String): Issuer? = ISSUERS.find { it.name == name }

    fun add(brand: String, last4: String, expiry: String, issuer: String = "", foreign: Boolean = false) {
        val list = all()
        if (list.any { it.brand == brand && it.issuer == issuer && it.last4 == last4 }) return
        save(list + SavedCard(UUID.randomUUID().toString(), brand, last4, expiry, issuer, foreign))
    }

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("brewbite_cards", Context.MODE_PRIVATE)
    }

    /** 로그인한 사람마다 따로 저장 */
    private fun key() = "cards_" + (runCatching { AuthManager.currentUid() }.getOrNull() ?: "guest")

    fun all(): List<SavedCard> = runCatching {
        val type = object : TypeToken<List<SavedCard>>() {}.type
        gson.fromJson<List<SavedCard>>(prefs.getString(key(), null), type)
    }.getOrNull() ?: emptyList()

    fun add(brand: String, last4: String, expiry: String) {
        val list = all()
        if (list.any { it.brand == brand && it.last4 == last4 }) return   // 같은 카드는 한 번만
        save(list + SavedCard(UUID.randomUUID().toString(), brand, last4, expiry))
    }

    fun remove(id: String) = save(all().filter { it.id != id })

    private fun save(list: List<SavedCard>) {
        prefs.edit().putString(key(), gson.toJson(list)).apply()
    }

    /** 카드 번호 앞자리로 브랜드 구분 */
    fun brandOf(digits: String): String = when {
        digits.startsWith("4") -> "VISA"
        digits.startsWith("5") || digits.startsWith("2") -> "MASTER"
        digits.startsWith("34") || digits.startsWith("37") -> "AMEX"
        digits.startsWith("35") -> "JCB"
        digits.startsWith("9") -> "국내전용"
        else -> "CARD"
    }
}