package com.example.brewandbite.cart

import android.content.Context
import android.content.SharedPreferences
import com.example.brewandbite.data.MenuItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.max

/**
 * 장바구니 데이터를 관리하는 곳 (앱 전체에서 하나만 존재).
 * 담은 메뉴는 휴대폰에 저장되므로 앱을 껐다 켜도 남아 있다.
 *
 * ───────── Order 화면을 만드는 팀원용 사용법 ─────────
 *  (Cart 화면의 결제하기 버튼 → PaymentBottomSheet → 결제가 끝나면 Order 탭)
 *  목록 가져오기      CartManager.getItems()          → List<CartItem>
 *  총 수량 / 총 금액   CartManager.totalCount() / CartManager.totalPrice()
 *  수량 +1 / -1       CartManager.increase(item.key) / CartManager.decrease(item.key)
 *  수량 직접 바꾸기    CartManager.setQuantity(item.key, 3)
 *  한 줄 삭제         CartManager.remove(item.key)
 *  (item.key = 메뉴 ID + 옵션. 같은 메뉴라도 옵션이 다르면 다른 줄)
 *  옵션 표시          item.options  예) "ICE · Large · 샷 +1"
 *  전부 비우기        CartManager.clear()             (주문 완료 후 호출)
 *  배달 / 포장        CartManager.fulfillment · setFulfillment(...)
 *  배달비 · 결제 금액  CartManager.deliveryFee() · payableTotal()
 *  최소 주문 금액      CartManager.isMinOrderReached() · shortfall()
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

    /** 수령 방법 */
    enum class Fulfillment { DELIVERY, PICKUP }

    /** 배달비 / 최소 주문 금액 (원) */
    const val DELIVERY_FEE = 1000
    const val MIN_ORDER_AMOUNT = 14000

    private const val PREF_NAME = "brewbite_cart"
    private const val KEY_ITEMS = "cart_items"
    private const val KEY_FULFILLMENT = "fulfillment"

    /** 현재 선택된 수령 방법 (앱을 껐다 켜도 유지) */
    var fulfillment: Fulfillment = Fulfillment.DELIVERY
        private set

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private val items = mutableListOf<CartItem>()
    private val listeners = mutableSetOf<CartListener>()

    /** 앱이 시작될 때 한 번 호출 (BrewBiteApp에서 이미 호출하고 있음) */
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        fulfillment = runCatching {
            Fulfillment.valueOf(prefs.getString(KEY_FULFILLMENT, null) ?: "")
        }.getOrDefault(Fulfillment.DELIVERY)
        load()
    }

    // ───────── 읽기 ─────────

    /** 담긴 메뉴 목록 (복사본이라 바깥에서 바꿔도 장바구니는 안전) */
    fun getItems(): List<CartItem> = items.map { it.copy() }

    fun totalCount(): Int = items.sumOf { it.quantity }

    fun totalPrice(): Int = items.sumOf { it.totalPrice }

    fun isEmpty(): Boolean = items.isEmpty()

    /** 배달비: 포장이거나 장바구니가 비어 있으면 0원 */
    fun deliveryFee(): Int =
        if (items.isEmpty() || fulfillment == Fulfillment.PICKUP) 0 else DELIVERY_FEE

    /** 실제 결제 금액 = 메뉴 합계 + 배달비 */
    fun payableTotal(): Int = totalPrice() + deliveryFee()

    /** 최소 주문 금액 달성 여부 (포장은 최소 금액 없음) */
    fun isMinOrderReached(): Boolean =
        items.isNotEmpty() && (fulfillment == Fulfillment.PICKUP || totalPrice() >= MIN_ORDER_AMOUNT)

    /** 최소 주문 금액까지 남은 금액 (포장이면 0) */
    fun shortfall(): Int =
        if (fulfillment == Fulfillment.PICKUP) 0 else max(0, MIN_ORDER_AMOUNT - totalPrice())

    // ───────── 바꾸기 ─────────

    /**
     * 메뉴 담기. 같은 메뉴 + 같은 옵션이 이미 있으면 수량만 늘어난다.
     * @param options   고른 옵션 설명 (메뉴 상세 화면에서). 목록의 + 버튼은 기본 옵션("")
     * @param unitPrice 옵션 추가 금액을 더한 1개 가격
     */
    fun add(menu: MenuItem, quantity: Int = 1, options: String = "", unitPrice: Int = menu.price) {
        if (quantity <= 0) return
        val newItem = CartItem(menu.id, menu.name, unitPrice, menu.emoji, quantity, options)
        val existing = items.find { it.key == newItem.key }
        if (existing != null) {
            existing.quantity += quantity
        } else {
            items.add(newItem)
        }
        saveAndNotify()
    }

    /** 배달 / 포장 바꾸기 */
    fun setFulfillment(value: Fulfillment) {
        if (fulfillment == value) return
        fulfillment = value
        saveAndNotify()
    }

    /** 수량 +1 */
    fun increase(key: String) {
        val item = items.find { it.key == key } ?: return
        item.quantity += 1
        saveAndNotify()
    }

    /** 수량 -1. 1개에서 줄이면 장바구니에서 빠진다. */
    fun decrease(key: String) {
        val item = items.find { it.key == key } ?: return
        if (item.quantity <= 1) items.remove(item) else item.quantity -= 1
        saveAndNotify()
    }

    /** 수량을 직접 정한다. 0 이하면 삭제. */
    fun setQuantity(key: String, quantity: Int) {
        val item = items.find { it.key == key } ?: return
        if (quantity <= 0) items.remove(item) else item.quantity = quantity
        saveAndNotify()
    }

    fun remove(key: String) {
        if (items.removeAll { it.key == key }) saveAndNotify()
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
        prefs.edit()
            .putString(KEY_ITEMS, gson.toJson(items))
            .putString(KEY_FULFILLMENT, fulfillment.name)
            .apply()
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