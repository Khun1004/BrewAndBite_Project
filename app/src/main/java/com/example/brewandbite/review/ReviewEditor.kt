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
 * 메뉴 상세 · 리뷰 전체 · Order 탭 · 주문 상세 · My Page(주문 기록 · 리뷰 내역)에서 같이 쓴다.
 *
 * 리뷰는 '주문 1건의 메뉴마다 1개' — 같은 메뉴를 또 주문하면 또 쓸 수 있다.
 *  open(..., 주문번호)  그 주문의 리뷰: 이미 썼으면 수정, 아니면 새로 쓰기 (배달·픽업이 끝나야)
 *  open(...)           주문번호를 모를 때: 아직 리뷰 안 쓴 가장 최근 주문으로 새로 쓰기
 *  edit(..., 리뷰ID)    내 리뷰 고치기
 */
object ReviewEditor {

    /**
     * @param anchor 안내 메시지(Snackbar)를 띄울 화면
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
        // 주문을 알 때: 그 주문의 리뷰
        if (orderNo != null) {
            val existing = ReviewManager.reviewFor(item.id, orderNo)
            if (existing != null) return ReviewWriteActivity.start(activity, item.id, reviewId = existing.id)
            val order = OrderHistory.find(orderNo)
            if (order != null && order.finished) return ReviewWriteActivity.start(activity, item.id, orderNo = orderNo)
            if (order != null) return Snackbar.make(anchor, R.string.review_wait, Snackbar.LENGTH_LONG).show()
        }
        // 주문을 모를 때: 리뷰 안 쓴 가장 최근 주문
        val target = OrderHistory.reviewableOrder(item.id)
        if (target != null) return ReviewWriteActivity.start(activity, item.id, orderNo = target.orderNo)
        val msg = when {
            OrderHistory.isWaiting(item.id) -> R.string.review_wait
            ReviewManager.myReviewsOf(item.id).isNotEmpty() -> R.string.review_all_done
            else -> R.string.review_locked
        }
        Snackbar.make(anchor, msg, Snackbar.LENGTH_LONG).show()
    }

    /** 내 리뷰 고치기 */
    fun edit(activity: Context, item: MenuItem, reviewId: String) {
        ReviewWriteActivity.start(activity, item.id, reviewId = reviewId)
    }
}