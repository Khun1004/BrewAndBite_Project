package com.example.brewandbite.cart

import android.content.Context
import android.content.SharedPreferences
import com.example.brewandbite.auth.AuthManager
import com.google.gson.Gson

/** 배달 주소 (로그인한 사람마다 따로 저장) */
object DeliveryAddress {

    data class Address(val base: String = "", val detail: String = "") {
        fun full() = if (detail.isBlank()) base else "$base $detail"
    }

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("brewbite_address", Context.MODE_PRIVATE)
    }

    private fun key() = "addr_" + (runCatching { AuthManager.currentUid() }.getOrNull() ?: "guest")

    fun get(): Address? = runCatching {
        gson.fromJson(prefs.getString(key(), null), Address::class.java)
    }.getOrNull()?.takeIf { it.base.isNotBlank() }

    fun save(address: Address) {
        prefs.edit().putString(key(), gson.toJson(address)).apply()
    }
}