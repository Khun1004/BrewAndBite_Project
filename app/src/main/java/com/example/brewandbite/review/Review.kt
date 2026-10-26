package com.example.brewandbite.review

import com.google.firebase.database.Exclude
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 리뷰 한 개 (Firebase: reviews/{메뉴ID}/{회원ID}). 한 메뉴에 1명당 1개.
 *  ordered = 이 메뉴를 주문한 적이 있는 사람이 썼는지 ('주문 고객' 표시)
 */
data class Review(
    var uid: String = "",
    var name: String = "",
    var rating: Int = 5,          // 1 ~ 5
    var text: String = "",
    var ordered: Boolean = false,
    var createdAt: Long = 0L,
    var updatedAt: Long = 0L
) {
    /** 예) "2026.10.24" (수정했으면 " · 수정됨") */
    @get:Exclude
    val dateText: String
        get() {
            val date = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(createdAt))
            return if (updatedAt > createdAt + 60_000) "$date · 수정됨" else date
        }
}