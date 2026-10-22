package com.example.brewandbite.menu

import com.example.brewandbite.data.MenuItem

/**
 * 메뉴 상세 화면에서 고를 수 있는 옵션 (카테고리마다 다름).
 *
 *  커피   : 온도(HOT/ICE) · 사이즈 · 샷 추가
 *  차     : 온도(HOT/ICE) · 사이즈
 *  음료   : 사이즈 (항상 ICE)
 *  디저트 · 베이커리 : 따뜻하게 데우기
 *  식사   : 옵션 없음
 */
object MenuOptions {

    enum class Size(val label: String, val desc: String, val extraPrice: Int) {
        REGULAR("Regular", "355ml", 0),
        LARGE("Large", "473ml", 500),
        GRANDE("Grande", "591ml", 1000)
    }

    const val SHOT_PRICE = 500
    const val MAX_SHOTS = 3

    fun hasTemperature(item: MenuItem) = item.categoryId == "coffee" || item.categoryId == "tea"
    fun hasSize(item: MenuItem) = item.categoryId in setOf("coffee", "tea", "drinks")
    fun hasShot(item: MenuItem) = item.categoryId == "coffee"
    fun canWarm(item: MenuItem) = item.categoryId == "dessert" || item.categoryId == "bakery"

    /** 고른 옵션 */
    data class Selection(
        val iced: Boolean = true,
        val size: Size = Size.REGULAR,
        val shots: Int = 0,
        val warm: Boolean = false
    )

    /** 옵션을 더한 1개 가격 */
    fun unitPrice(item: MenuItem, s: Selection): Int {
        var price = item.price
        if (hasSize(item)) price += s.size.extraPrice
        if (hasShot(item)) price += s.shots * SHOT_PRICE
        return price
    }

    /** 장바구니에 보여 줄 옵션 설명. 예) "ICE · Large · 샷 +1" */
    fun describe(item: MenuItem, s: Selection): String {
        val parts = mutableListOf<String>()
        if (hasTemperature(item)) parts += if (s.iced) "ICE" else "HOT"
        if (hasSize(item)) parts += s.size.label
        if (hasShot(item) && s.shots > 0) parts += "샷 +${s.shots}"
        if (canWarm(item) && s.warm) parts += "데워서"
        return parts.joinToString(" · ")
    }
}