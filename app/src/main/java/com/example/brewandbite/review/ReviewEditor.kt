package com.example.brewandbite.review

import android.content.Context
import android.view.View
import com.example.brewandbite.R
import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.auth.LoginActivity
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.order.OrderHistory
import com.google.android.material.snackbar.Snackbar

/**
 * ⭐ 리뷰 쓰기 / 수정 시작하기.
 * 메뉴 상세(ReviewSection) · Order 탭 · 주문 상세 · My Page 리뷰 내역에서 같이 쓴다.
 *  - 로그인 안 했으면 → 로그인 안내
 *  - 주문 안 했거나 배달·픽업 중이면 → 안내만
 *  - 쓸 수 있으면 → 리뷰 쓰기 화면 (ReviewWriteActivity: 별점 · 📷 사진 · 글)
 *
 * 사용법: ReviewEditor.open(context, 화면의 아무 View, menuItem, 주문번호(모르면 null))
 */
object ReviewEditor {

    /**
     * @param anchor 안내 메시지(Snackbar)를 띄울 화면
     * @param orderNo 어느 주문의 리뷰인지 (Order 탭에서). 모르면 null → 리뷰 가능한 가장 최근 주문
     * @param onSaved (예전 버전 호환용) 저장 후 화면은 ReviewManager 리스너가 자동으로 다시 그림
     */
    @Suppress("UNUSED_PARAMETER")
    fun open(activity: Context, anchor: View, item: MenuItem, orderNo: String? = null, onSaved: () -> Unit = {}) {
        if (!AuthManager.isLoggedIn()) {
            Snackbar.make(anchor, R.string.review_login_needed, Snackbar.LENGTH_LONG)
                .setAction(R.string.review_go_login) { activity.startActivity(LoginActivity.intent(activity)) }
                .show()
            return
        }
        if (!ReviewManager.canWrite(item.id)) {
            val msg = if (OrderHistory.isWaiting(item.id)) R.string.review_wait else R.string.review_locked
            Snackbar.make(anchor, msg, Snackbar.LENGTH_LONG).show()
            return
        }
        ReviewWriteActivity.start(activity, item.id, orderNo)
    }
}