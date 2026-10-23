package com.example.brewandbite.order

import android.content.Context
import android.content.SharedPreferences
import com.example.brewandbite.cart.CartItem
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 가장 최근 주문 1건을 휴대폰에 저장하고, 시간에 따른 진행 단계를 계산한다 */
object OrderManager {

    /** 시연용 배속. 1 = 실제 시간, 6 = 6배 빠르게 (25분 배달이 약 4분) */
    const val DEMO_SPEED = 6

    private const val PREF_NAME = "brewbite_order"
    private const val KEY_ORDER = "current_order"

    data class OrderInfo(
        val orderNo: String = "",
        val placedAt: Long = 0L,
        val isDelivery: Boolean = true,
        val items: List<CartItem> = emptyList(),
        val subtotal: Int = 0,
        val deliveryFee: Int = 0,
        val discount: Int = 0,           // 쿠폰 할인 금액 (없으면 0)
        val couponTitle: String = "",    // 사용한 쿠폰 이름 (없으면 빈 글자)
        val stampsEarned: Int = 0        // 이 주문으로 받은 스탬프 수
    ) {
        /** 실제 결제 금액 = 메뉴 합계 + 배달비 - 쿠폰 할인 */
        val total: Int get() = (subtotal + deliveryFee - discount).coerceAtLeast(0)
    }

    /** 진행 단계 하나: 이름 + 주문 후 몇 ms 뒤에 시작하는지 */
    data class Stage(val title: String, val startMs: Long)

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private var current: OrderInfo? = null

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        current = runCatching {
            gson.fromJson(prefs.getString(KEY_ORDER, null), OrderInfo::class.java)
        }.getOrNull()?.takeIf { it.orderNo.isNotEmpty() }
    }

    fun current(): OrderInfo? = current

    /** 결제 성공 시 호출 (CartManager.clear() 전에!) */
    fun place(
        items: List<CartItem>,
        isDelivery: Boolean,
        subtotal: Int,
        deliveryFee: Int,
        discount: Int = 0,
        couponTitle: String = "",
        stampsEarned: Int = 0
    ): OrderInfo {
        val now = System.currentTimeMillis()
        val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date(now))
        val order = OrderInfo(
            orderNo = "BB-$date-${(1000..9999).random()}",
            placedAt = now,
            isDelivery = isDelivery,
            items = items,
            subtotal = subtotal,
            deliveryFee = deliveryFee,
            discount = discount,
            couponTitle = couponTitle,
            stampsEarned = stampsEarned
        )
        current = order
        prefs.edit().putString(KEY_ORDER, gson.toJson(order)).apply()
        return order
    }

    fun clear() {
        current = null
        prefs.edit().remove(KEY_ORDER).apply()
    }

    fun stages(order: OrderInfo): List<Stage> {
        val min = 60_000L / DEMO_SPEED
        return if (order.isDelivery) listOf(
            Stage("주문 접수", 0),
            Stage("메뉴 준비 중", 1 * min),
            Stage("배달 중", 10 * min),
            Stage("배달 완료", 25 * min)
        ) else listOf(
            Stage("주문 접수", 0),
            Stage("메뉴 준비 중", 1 * min),
            Stage("픽업 준비 완료", 8 * min)
        )
    }

    /** 지금 몇 번째 단계인지 (0부터) */
    fun currentStage(order: OrderInfo, now: Long = System.currentTimeMillis()): Int {
        val elapsed = now - order.placedAt
        return stages(order).indexOfLast { elapsed >= it.startMs }.coerceAtLeast(0)
    }
}