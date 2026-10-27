package com.example.brewandbite.data

/**
 * 메뉴 한 개의 정보 (Firebase: menu/items/{메뉴ID}).
 * Firebase 가 값을 채워 넣을 수 있게 모든 값에 기본값 + var (빈 생성자 · setter 필요).
 */
data class MenuItem(
    var id: String = "",
    var name: String = "",
    var description: String = "",
    var price: Int = 0,
    var rating: Double = 0.0,
    var categoryId: String = "",
    var emoji: String = "",
    var popular: Boolean = false,
    var imageUrl: String = ""
)