package com.example.brewandbite.cart

/**
 * 장바구니에 담긴 메뉴 한 줄.
 * 같은 메뉴를 여러 번 담으면 새 줄이 생기지 않고 quantity(수량)만 늘어난다.
 */
data class CartItem(
    val menuId: String = "",
    val name: String = "",
    val price: Int = 0,        // 메뉴 1개 가격 (원)
    val emoji: String = "",
    var quantity: Int = 1
) {
    /** 이 줄의 합계 금액 = 가격 × 수량 */
    val totalPrice: Int
        get() = price * quantity
}