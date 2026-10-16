package com.example.brewandbite.auth

/**
 * 회원 정보 (Firebase Realtime Database의 users/{회원ID} 에 저장됨).
 * Firebase가 읽고 쓸 수 있도록 모든 값은 var + 기본값.
 */
data class UserProfile(
    var uid: String = "",
    var name: String = "",
    var email: String = "",
    var phone: String = "",
    var address: String = "",
    var addressDetail: String = "",
    var createdAt: Long = 0L
)