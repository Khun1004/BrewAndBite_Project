package com.example.brewandbite.reward

import com.google.firebase.database.Exclude
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 쿠폰 한 장 (Firebase: rewards/{회원ID}/coupons/{쿠폰ID}).
 *  amount   : 할인 금액 (원). 음료 무료 쿠폰은 '최대 할인 금액'
 *  minOrder : 이 금액 이상 주문해야 쓸 수 있음 (0 = 조건 없음)
 */
data class Coupon(
    var id: String = "",
    var title: String = "",
    var amount: Int = 0,
    var minOrder: Int = 0,
    var source: String = SOURCE_WELCOME,   // welcome = 가입 축하, stamp = 스탬프 10개 보상
    var issuedAt: Long = 0L,
    var expiresAt: Long = 0L,
    var used: Boolean = false,
    var usedAt: Long = 0L
) {
    /** 유효기간이 지났는지 */
    @get:Exclude
    val expired: Boolean
        get() = expiresAt in 1 until System.currentTimeMillis()

    /** 지금 쓸 수 있는 쿠폰인지 (사용 안 함 + 기간 안) */
    @get:Exclude
    val usable: Boolean
        get() = !used && !expired

    /** 남은 날짜 (오늘 끝나면 0) */
    @get:Exclude
    val daysLeft: Int
        get() = TimeUnit.MILLISECONDS.toDays((expiresAt - System.currentTimeMillis()).coerceAtLeast(0)).toInt()

    /** 예) "2026.11.21" */
    @get:Exclude
    val expiryText: String
        get() = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(expiresAt))

    companion object {
        const val SOURCE_WELCOME = "welcome"
        const val SOURCE_STAMP = "stamp"
    }
}