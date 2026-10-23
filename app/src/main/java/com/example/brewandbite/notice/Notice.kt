package com.example.brewandbite.notice

import androidx.annotation.DrawableRes
import com.example.brewandbite.R
import com.google.firebase.database.Exclude

/**
 * 알림 한 개 (Firebase: notices/{회원ID}/items/{알림ID}).
 *  type : coupon / stamp / order / menu / event  → 아이콘과 눌렀을 때 갈 화면이 정해짐
 *  link : menu 알림이면 메뉴 ID (그 메뉴 상세 화면으로), 나머지는 비워 둠
 */
data class Notice(
    var id: String = "",
    var type: String = TYPE_EVENT,
    var title: String = "",
    var body: String = "",
    var link: String = "",
    var createdAt: Long = 0L,
    var read: Boolean = false
) {
    /** 종류별 아이콘 */
    @get:Exclude
    @get:DrawableRes
    val icon: Int
        get() = when (type) {
            TYPE_COUPON -> R.drawable.ic_coupon
            TYPE_STAMP -> R.drawable.ic_coffee
            TYPE_ORDER -> R.drawable.ic_receipt
            TYPE_MENU -> R.drawable.ic_heart_outline
            else -> R.drawable.ic_gift
        }

    companion object {
        const val TYPE_COUPON = "coupon"
        const val TYPE_STAMP = "stamp"
        const val TYPE_ORDER = "order"
        const val TYPE_MENU = "menu"
        const val TYPE_EVENT = "event"
    }
}