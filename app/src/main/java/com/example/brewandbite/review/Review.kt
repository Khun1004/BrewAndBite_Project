package com.example.brewandbite.review

import com.google.firebase.database.Exclude
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 리뷰 한 개 (Firebase: reviews/{메뉴ID}/{리뷰ID}). 주문 1건의 메뉴마다 1개 (같은 메뉴를 또 주문하면 또 쓸 수 있음).
 * 리뷰ID = "{회원ID}_{주문번호}" — Firebase 키라서 따로 저장하지 않고 불러올 때 채운다.
 * 주문한 사람만 쓸 수 있다 → orderNo = 어느 주문으로 썼는지, options = 그때 고른 옵션 (예: "ICE · Large")
 */
data class Review(
    @get:Exclude var id: String = "",
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