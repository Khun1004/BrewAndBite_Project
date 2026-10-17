package com.example.brewandbite.data

/**
 * 메뉴 한 개의 정보.
 * 나중에 Firebase Realtime Database로 옮길 때도 이 구조 그대로 쓸 수 있도록
 * 모든 값에 기본값을 넣어 두었다 (Firebase는 빈 생성자가 필요함).
 */
data class MenuItem(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val price: Int = 0,
    val rating: Double = 0.0,
    val categoryId: String = "",
    val emoji: String = "",
    val popular: Boolean = false,
    val imageUrl: String = ""
)