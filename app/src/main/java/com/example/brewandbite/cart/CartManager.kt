package com.example.brewandbite.cart

import android.content.Context
import android.content.SharedPreferences
import com.example.brewandbite.data.MenuItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 장바구니 데이터를 관리하는 곳 (앱 전체에서 하나만 존재).
 * 담은 메뉴는 휴대폰에 저장되므로 앱을 껐다 켜도 남아 있다.
 *
 * ───────── Order 화면을 만드는 팀원용 사용법 ─────────
 *  (Cart 화면의 주문하기 버튼은 이미 order/OrderActivity 로 연결되어 있음)
 *  목록 가져오기      CartManager.getItems()          → List<CartItem>
 *  총 수량 / 총 금액   CartManager.totalCount() / CartManager.totalPrice()
 *  수량 +1 / -1       CartManager.increase(menuId) / CartManager.decrease(menuId)
 *  수량 직접 바꾸기    CartManager.setQuantity(menuId, 3)
 *  한 줄 삭제         CartManager.remove(menuId)
 *  전부 비우기        CartManager.clear()             (주문 완료 후 호출)
 *  변경 알림 받기      CartManager.addListener(listener) / removeListener(listener)
 *                    → 화면의 onStart에서 add, onStop에서 remove 하면 된다.
 *  금액 표시          totalPrice().toWon()            (data/MenuRepository.kt의 함수)
 * ─────────────────────────────────────────────────────────
 */
object CartManager {

    /** 장바구니가 바뀔 때마다 불리는 리스너 */
    fun interface CartListener {
        fun onCartChanged()
    }

    private const val PREF_NAME = "brewbite_cart"
    private const val KEY_ITEMS = "cart_items"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private val items = mutableListOf<CartItem>()
    private val listeners = mutableSetOf<CartListener>()

    /** 앱이 시작될 때 한 번 호출 (BrewBiteApp에서 이미 호출하고 있음) */
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        load()
    }

    // ───────── 읽기 ─────────

    /** 담긴 메뉴 목록 (복사본이라 바깥에서 바꿔도 장바구니는 안전) */
    fun getItems(): List<CartItem> = items.map { it.copy() }

    fun totalCount(): Int = items.sumOf { it.quantity }

    fun totalPrice(): Int = items.sumOf { it.totalPrice }

    fun isEmpty(): Boolean = items.isEmpty()

    // ───────── 바꾸기 ─────────

    /** 메뉴 담기. 이미 있으면 수량만 늘어난다. */
    fun add(menu: MenuItem, quantity: Int = 1) {
        if (quantity <= 0) return
        val existing = items.find { it.menuId == menu.id }
        if (existing != null) {
            existing.quantity += quantity
        } else {
            items.add(CartItem(menu.id, menu.name, menu.price, menu.emoji, quantity))
        }
        saveAndNotify()
    }

    fun increase(menuId: String) {
        val item = items.find { it.menuId == menuId } ?: return
        item.quantity += 1
        saveAndNotify()
    }

    /** 수량 -1. 1개에서 줄이면 장바구니에서 빠진다. */
    fun decrease(menuId: String) {
        val item = items.find { it.menuId == menuId } ?: return
        if (item.quantity <= 1) items.remove(item) else item.quantity -= 1
        saveAndNotify()
    }

    /** 수량을 직접 정한다. 0 이하면 삭제. */
    fun setQuantity(menuId: String, quantity: Int) {
        val item = items.find { it.menuId == menuId } ?: return
        if (quantity <= 0) items.remove(item) else item.quantity = quantity
        saveAndNotify()
    }

    fun remove(menuId: String) {
        if (items.removeAll { it.menuId == menuId }) saveAndNotify()
    }

    fun clear() {
        items.clear()
        saveAndNotify()
    }

    // ───────── 리스너 ─────────

    fun addListener(listener: CartListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: CartListener) {
        listeners.remove(listener)
    }

    // ───────── 저장 / 불러오기 ─────────

    private fun saveAndNotify() {
        prefs.edit().putString(KEY_ITEMS, gson.toJson(items)).apply()
        listeners.toList().forEach { it.onCartChanged() }
    }

    private fun load() {
        val json = prefs.getString(KEY_ITEMS, null) ?: return
        try {
            val type = object : TypeToken<List<CartItem>>() {}.type
            val saved: List<CartItem> = gson.fromJson(json, type) ?: emptyList()
            items.clear()
            items.addAll(saved.filter { it.menuId.isNotEmpty() && it.quantity > 0 })
        } catch (e: Exception) {
            items.clear()   // 저장된 데이터가 깨졌으면 빈 장바구니로 시작
        }
    }
}