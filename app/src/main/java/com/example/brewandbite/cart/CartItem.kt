package com.example.brewandbite.cart

/**
 * 장바구니에 담긴 메뉴 한 줄.
 * 같은 메뉴 + 같은 옵션을 여러 번 담으면 새 줄이 생기지 않고 quantity(수량)만 늘어난다.
 * 옵션이 다르면 (예: Americano ICE / Americano HOT) 다른 줄이 된다.
 */
data class CartItem(
    val menuId: String = "",
    val name: String = "",
    val price: Int = 0,        // 1개 가격 (옵션 추가 금액 포함, 원)
    val emoji: String = "",
    var quantity: Int = 1,
    val options: String = ""   // 고른 옵션 설명. 예) "ICE · Large · 샷 +1" (없으면 빈 글자)
) {
    /** 줄을 구분하는 키 = 메뉴 ID + 옵션 (수량 바꾸기 / 삭제에 사용) */
    val key: String
        get() = if (options.isEmpty()) menuId else "$menuId|$options"

    /** 이 줄의 합계 금액 = 가격 × 수량 */
    val totalPrice: Int
        get() = price * quantity
}