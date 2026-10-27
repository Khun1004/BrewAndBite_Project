package com.example.brewandbite.review

import com.google.firebase.database.Exclude
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 리뷰 한 개 (Firebase: reviews/{메뉴ID}/{회원ID}). 한 메뉴에 1명당 1개.
 * 주문한 사람만 쓸 수 있다 → orderNo = 어느 주문으로 썼는지, options = 그때 고른 옵션 (예: "ICE · Large")
 */
data class Review(
    var uid: String = "",
    var name: String = "",
    var rating: Int = 5,          // 1 ~ 5
    var text: String = "",
    var orderNo: String = "",
    var options: String = "",
    var photoCount: Int = 0,      // 📷 사진 수 (사진은 reviewPhotos 에 따로 저장 · ReviewPhotos.kt)
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