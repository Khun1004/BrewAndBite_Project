package com.example.brewandbite.review

import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.brewandbite.R
import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.databinding.ItemReviewBinding
import com.example.brewandbite.databinding.SectionMenuReviewsBinding
import com.example.brewandbite.order.OrderHistory
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 메뉴 상세 화면의 ⭐ 리뷰 영역 (section_menu_reviews.xml).
 *  - 평균 별점 · 별점별 막대 · 최신 리뷰 3개 (📷 사진 포함)
 *  - [리뷰 전체 보기] → 리뷰 전체 화면 (ReviewListActivity: 정렬 · 사진 리뷰만)
 *  - [리뷰 쓰기] → 리뷰 쓰기 화면 (ReviewWriteActivity: 별점 · 사진 · 글)
 *    리뷰는 이 메뉴를 주문했고 배달·픽업이 끝난 사람만 (아니면 🔒 안내)
 *  - 내 리뷰에는 수정 · 삭제
 *
 * MenuDetailActivity 에서:  reviews = ReviewSection(this, binding.reviewSection, item)
 *                          onStart → reviews.start() / onStop → reviews.stop()
 */
class ReviewSection(
    private val activity: AppCompatActivity,
    private val b: SectionMenuReviewsBinding,
    private val item: MenuItem,
    /** 리뷰가 바뀌면 (위쪽 별점 숫자 다시 그리기 등) */
    private val onChanged: () -> Unit = {}
) {
    private val listener = ReviewManager.ReviewListener { render() }
    private val historyListener = OrderHistory.HistoryListener { render() }

    init {
        b.buttonWriteReview.setOnClickListener { openEditor() }
        b.buttonMoreReviews.setOnClickListener { ReviewListActivity.start(activity, item.id) }
        b.summaryBox.setOnClickListener { ReviewListActivity.start(activity, item.id) }
    }

    fun start() {
        ReviewManager.addListener(listener)
        OrderHistory.addListener(historyListener)
        render()
    }

    fun stop() {
        ReviewManager.removeListener(listener)
        OrderHistory.removeListener(historyListener)
    }

    // ───────── 그리기 ─────────

    private fun render() {
        val ctx = activity
        val reviews = ReviewManager.reviews(item.id)
        val count = reviews.size
        val mine = ReviewManager.myReview(item.id)

        b.textReviewCount.text = if (count > 0) ctx.getString(R.string.review_count, count) else ""
        // 버튼: 내 리뷰 수정 / 리뷰 쓰기 / 배달 완료 후 / 🔒 주문 후
        val loggedIn = AuthManager.isLoggedIn()
        b.buttonWriteReview.setText(
            when {
                mine != null -> R.string.review_edit_mine
                !loggedIn || OrderHistory.reviewableOrder(item.id) != null -> R.string.review_write
                OrderHistory.isWaiting(item.id) -> R.string.review_wait_button
                else -> R.string.review_locked_button
            }
        )
        b.buttonWriteReview.alpha = if (!loggedIn || ReviewManager.canWrite(item.id)) 1f else 0.55f

        // 평균 + 막대 (리뷰가 없으면 숨김)
        b.summaryBox.visibility = if (count > 0) View.VISIBLE else View.GONE
        b.textNoReview.visibility = if (count > 0) View.GONE else View.VISIBLE
        if (count > 0) {
            val avg = ReviewManager.average(item.id) ?: 0.0
            b.textAverage.text = ReviewManager.ratingText(item)
            b.starsAverage.showStars(avg, 16)
            b.textAverageCount.text = ctx.getString(R.string.review_count_label, count)
            b.barsContainer.drawRatingBars(ReviewManager.distribution(item.id), count)
        }

        // 최신 리뷰 3개 (내 리뷰는 항상 맨 위)
        val ordered = if (mine != null) listOf(mine) + reviews.filter { it.uid != mine.uid } else reviews
        b.reviewList.removeAllViews()
        ordered.take(PREVIEW_COUNT).forEach { r ->
            val v = ItemReviewBinding.inflate(LayoutInflater.from(activity), b.reviewList, false)
            v.bindReview(item.id, r, r.uid == mine?.uid, onEdit = { openEditor() }, onDelete = { confirmDelete() })
            b.reviewList.addView(v.root)
        }

        // 리뷰가 있으면 [리뷰 전체 보기 (12) ›] → 리뷰 전체 화면
        b.buttonMoreReviews.visibility = if (count > 0) View.VISIBLE else View.GONE
        b.buttonMoreReviews.text = ctx.getString(R.string.review_more_all, count)

        onChanged()
    }

    // ───────── 쓰기 · 수정 · 삭제 ─────────

    /** 리뷰 쓰기 화면 열기 (주문 기록의 [리뷰 쓰기]로 들어오면 MenuDetailActivity 가 바로 호출) */
    fun openEditor() = ReviewEditor.open(activity, b.root, item)

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.review_delete_title)
            .setNegativeButton(R.string.cart_cancel, null)
            .setPositiveButton(R.string.my_delete) { _, _ ->
                ReviewManager.delete(item.id) { ok ->
                    Toast.makeText(activity, if (ok) R.string.review_deleted else R.string.review_failed, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    companion object {
        private const val PREVIEW_COUNT = 3
    }
}