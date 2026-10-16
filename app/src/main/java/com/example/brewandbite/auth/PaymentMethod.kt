package com.example.brewandbite.auth

/**
 * 결제 수단 한 개 (Firebase의 payments/{회원ID}/{id} 에 저장).
 * ⚠️ 학교 프로젝트라 실제 결제는 하지 않음. 카드는 '카드사 + 끝 4자리'만 저장하고 전체 카드번호는 절대 저장하지 않는다.
 */
data class PaymentMethod(
    var id: String = "",
    var type: String = TYPE_CARD,
    var cardCompany: String = "",
    var last4: String = "",
    var defaultMethod: Boolean = false,   // 기본 결제 수단 (Firebase 키 이름 때문에 isDefault 대신 이 이름 사용)
    var createdAt: Long = 0L
) {
    /** 화면에 보여 줄 이름. 예) "신한카드 •••• 1234", "카카오페이" */
    fun displayName(): String = when (type) {
        TYPE_CARD -> listOf(cardCompany.ifBlank { "카드" }, if (last4.isNotBlank()) "•••• $last4" else "")
            .filter { it.isNotBlank() }.joinToString(" ")
        TYPE_KAKAO -> "카카오페이"
        TYPE_NAVER -> "네이버페이"
        TYPE_TOSS -> "토스페이"
        else -> "만나서 결제"
    }

    fun emoji(): String = when (type) {
        TYPE_CARD -> "💳"
        TYPE_KAKAO -> "🟡"
        TYPE_NAVER -> "🟢"
        TYPE_TOSS -> "🔵"
        else -> "💵"
    }

    companion object {
        const val TYPE_CARD = "card"
        const val TYPE_KAKAO = "kakao"
        const val TYPE_NAVER = "naver"
        const val TYPE_TOSS = "toss"
        const val TYPE_CASH = "cash"
    }
}