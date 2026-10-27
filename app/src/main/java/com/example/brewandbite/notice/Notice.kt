package com.example.brewandbite.notice

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
            TYPE_MENU -> R.drawable.ic_heart
            else -> R.drawable.ic_gift
        }

    /** 종류 이름 (카드 위 작은 꼬리표) */
    @get:Exclude
    @get:StringRes
    val label: Int
        get() = when (type) {
            TYPE_COUPON -> R.string.notice_type_coupon
            TYPE_STAMP -> R.string.notice_type_stamp
            TYPE_ORDER -> R.string.notice_type_order
            TYPE_MENU -> R.string.notice_type_menu
            else -> R.string.notice_type_event
        }

    /** 아이콘 동그라미 색 (바탕, 아이콘) — 종류마다 다른 색 */
    @get:Exclude
    val colors: Pair<Int, Int>
        get() = when (type) {
            TYPE_COUPON -> R.color.bb_nav_active to R.color.bb_espresso
            TYPE_STAMP -> R.color.bb_brown to R.color.bb_cream
            TYPE_ORDER -> R.color.bb_open_bg to R.color.bb_open
            TYPE_MENU -> R.color.bb_closed_bg to R.color.bb_closed
            else -> R.color.bb_beige_light to R.color.bb_brown
        }

    /** 필터 묶음: 혜택(쿠폰·스탬프) / 주문 / 소식(추천·이벤트) */
    @get:Exclude
    val group: String
        get() = when (type) {
            TYPE_COUPON, TYPE_STAMP -> GROUP_BENEFIT
            TYPE_ORDER -> GROUP_ORDER
            else -> GROUP_NEWS
        }

    companion object {
        const val GROUP_BENEFIT = "benefit"
        const val GROUP_ORDER = "order"
        const val GROUP_NEWS = "news"

        const val TYPE_COUPON = "coupon"
        const val TYPE_STAMP = "stamp"
        const val TYPE_ORDER = "order"
        const val TYPE_MENU = "menu"
        const val TYPE_EVENT = "event"
    }
}