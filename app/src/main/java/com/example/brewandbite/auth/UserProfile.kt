package com.example.brewandbite.auth

/**
 * 회원 정보 (Firebase Realtime Database의 users/{회원ID} 에 저장됨).
 * Firebase가 읽고 쓸 수 있도록 모든 값은 var + 기본값.
 */
data class UserProfile(
    var uid: String = "",
    var name: String = "",
    var email: String = "",
    var phone: String = "",          // 문자 인증을 마친 번호 (예: 010-1234-5678)
    var zipCode: String = "",        // 우편번호 (주소 검색에서 자동 입력)
    var address: String = "",
    var addressDetail: String = "",
    var photo: String = "",          // 프로필 사진 (작게 줄인 JPEG 를 Base64 글자로 저장)
    var createdAt: Long = 0L
)