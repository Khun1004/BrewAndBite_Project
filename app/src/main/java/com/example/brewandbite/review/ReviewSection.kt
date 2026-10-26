package com.example.brewandbite.review

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.brewandbite.R
import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.databinding.ItemReviewBinding
import com.example.brewandbite.databinding.SectionMenuReviewsBinding
import com.example.brewandbite.order.OrderHistory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * 메뉴 상세 화면의 ⭐ 리뷰 영역 (section_menu_reviews.xml).
 *  - 평균 별점 · 별점별 막대 · 최신 리뷰 3개 (+ [리뷰 전체 보기])
 *  - [리뷰 쓰기] → 별점 + 글 입력 창 (이미 썼으면 [내 리뷰 수정])
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
    private var expanded = false
    private val listener = ReviewManager.ReviewListener { render() }
    private val historyListener = OrderHistory.HistoryListener { render() }

    init {
        b.buttonWriteReview.setOnClickListener { openEditor() }
        b.buttonMoreReviews.setOnClickListener {
            expanded = !expanded
            render()
        }
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
            drawBars(ReviewManager.distribution(item.id), count)
        }

        // 리뷰 카드: 접혀 있으면 3개만 (내 리뷰는 항상 맨 위)
        val ordered = if (mine != null) listOf(mine) + reviews.filter { it.uid != mine.uid } else reviews
        val shown = if (expanded) ordered else ordered.take(PREVIEW_COUNT)
        b.reviewList.removeAllViews()
        shown.forEach { addReviewView(it, it.uid == mine?.uid) }

        b.buttonMoreReviews.visibility = if (count > PREVIEW_COUNT) View.VISIBLE else View.GONE
        b.buttonMoreReviews.text =
            if (expanded) ctx.getString(R.string.review_less) else ctx.getString(R.string.review_more, count)

        onChanged()
    }

    /** 5점 ~ 1점 막대 */
    private fun drawBars(dist: IntArray, total: Int) {
        val ctx = activity
        val density = ctx.resources.displayMetrics.density
        b.barsContainer.removeAllViews()
        for (score in 5 downTo 1) {
            val n = dist[score - 1]
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (2 * density).toInt(), 0, (2 * density).toInt())
            }
            row.addView(TextView(ctx).apply {
                text = score.toString()
                textSize = 12f
                setTextColor(ContextCompat.getColor(ctx, R.color.bb_brown))
                minWidth = (12 * density).toInt()
            })
            row.addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_star)
                imageTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.bb_star))
            }, LinearLayout.LayoutParams((11 * density).toInt(), (11 * density).toInt()).apply {
                marginEnd = (6 * density).toInt()
            })
            row.addView(LinearProgressIndicator(ctx).apply {
                max = 100
                progress = if (total == 0) 0 else n * 100 / total
                trackThickness = (6 * density).toInt()
                trackCornerRadius = (3 * density).toInt()
                setIndicatorColor(ContextCompat.getColor(ctx, R.color.bb_star))
                trackColor = ContextCompat.getColor(ctx, R.color.white)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(ctx).apply {
                text = n.toString()
                textSize = 11f
                gravity = Gravity.END
                setTextColor(ContextCompat.getColor(ctx, R.color.bb_muted))
                minWidth = (22 * density).toInt()
            })
            b.barsContainer.addView(row)
        }
    }

    private fun addReviewView(r: Review, isMine: Boolean) {
        val v = ItemReviewBinding.inflate(LayoutInflater.from(activity), b.reviewList, false)
        v.textAvatar.text = r.name.take(1).uppercase()
        v.textName.text = r.name
        v.textOptions.text = r.options
        v.textOptions.visibility = if (r.options.isNotBlank()) View.VISIBLE else View.GONE
        v.textMine.visibility = if (isMine) View.VISIBLE else View.GONE
        v.stars.showStars(r.rating.toDouble(), 13)
        v.textDate.text = r.dateText
        v.textReview.text = r.text
        v.rowMine.visibility = if (isMine) View.VISIBLE else View.GONE
        v.buttonEdit.setOnClickListener { openEditor() }
        v.buttonDelete.setOnClickListener { confirmDelete() }
        b.reviewList.addView(v.root)
    }

    // ───────── 쓰기 · 수정 · 삭제 ─────────

    /** 리뷰 쓰기 창 열기 (주문 기록의 [리뷰 쓰기]로 들어오면 MenuDetailActivity 가 바로 호출) */
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