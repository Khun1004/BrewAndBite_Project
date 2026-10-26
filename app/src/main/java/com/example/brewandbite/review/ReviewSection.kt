package com.example.brewandbite.review

import android.content.DialogInterface
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
import com.example.brewandbite.auth.LoginActivity
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.databinding.DialogReviewBinding
import com.example.brewandbite.databinding.ItemReviewBinding
import com.example.brewandbite.databinding.SectionMenuReviewsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar

/**
 * 메뉴 상세 화면의 ⭐ 리뷰 영역 (section_menu_reviews.xml).
 *  - 평균 별점 · 별점별 막대 · 최신 리뷰 3개 (+ [리뷰 전체 보기])
 *  - [리뷰 쓰기] → 별점 + 글 입력 창 (이미 썼으면 [내 리뷰 수정])
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

    init {
        b.buttonWriteReview.setOnClickListener { openEditor() }
        b.buttonMoreReviews.setOnClickListener {
            expanded = !expanded
            render()
        }
    }

    fun start() {
        ReviewManager.addListener(listener)
        render()
    }

    fun stop() {
        ReviewManager.removeListener(listener)
    }

    // ───────── 그리기 ─────────

    private fun render() {
        val ctx = activity
        val reviews = ReviewManager.reviews(item.id)
        val count = reviews.size
        val mine = ReviewManager.myReview(item.id)

        b.textReviewCount.text = if (count > 0) ctx.getString(R.string.review_count, count) else ""
        b.buttonWriteReview.setText(if (mine != null) R.string.review_edit_mine else R.string.review_write)

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
        v.textOrdered.visibility = if (r.ordered) View.VISIBLE else View.GONE
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

    private fun openEditor() {
        if (!AuthManager.isLoggedIn()) {
            Snackbar.make(b.root, R.string.review_login_needed, Snackbar.LENGTH_LONG)
                .setAction(R.string.review_go_login) { activity.startActivity(LoginActivity.intent(activity)) }
                .show()
            return
        }
        val old = ReviewManager.myReview(item.id)
        val d = DialogReviewBinding.inflate(LayoutInflater.from(activity))
        var rating = old?.rating ?: 0
        d.textMenu.text = "${item.emoji} ${item.name}"
        d.editReview.setText(old?.text.orEmpty())

        // 큰 별 5개: 누르면 그 개수만큼 채워짐
        val density = activity.resources.displayMetrics.density
        val stars = mutableListOf<ImageView>()
        for (score in 1..5) {
            val star = ImageView(activity).apply {
                val pad = (4 * density).toInt()
                setPadding(pad, pad, pad, pad)
                setOnClickListener {
                    rating = score
                    paintPicker(d, rating, stars)
                }
            }
            stars += star
            d.starPicker.addView(star, LinearLayout.LayoutParams((44 * density).toInt(), (44 * density).toInt()))
        }
        paintPicker(d, rating, stars)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(if (old == null) R.string.review_dialog_title else R.string.review_dialog_edit_title)
            .setView(d.root)
            .setNegativeButton(R.string.cart_cancel, null)
            .setPositiveButton(if (old == null) R.string.review_submit else R.string.review_edit, null)
            .show()

        // 입력이 맞을 때만 닫히게 (버튼을 직접 처리)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { button ->
            val text = d.editReview.text?.toString()?.trim().orEmpty()
            when {
                rating == 0 -> d.textRatingLabel.setText(R.string.review_err_rating)
                text.length < ReviewManager.MIN_LENGTH -> d.layoutReview.error = activity.getString(R.string.review_err_short)
                else -> {
                    d.layoutReview.error = null
                    button.isEnabled = false
                    ReviewManager.save(item.id, rating, text) { ok ->
                        button.isEnabled = true
                        if (ok) dialog.dismiss()
                        Toast.makeText(activity, if (ok) R.string.review_saved else R.string.review_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /** 별점 고르기 창의 별 색 + 아래 글자 (최고예요! 😍 등) */
    private fun paintPicker(d: DialogReviewBinding, rating: Int, stars: List<ImageView>) {
        stars.forEachIndexed { i, star ->
            val on = i < rating
            star.setImageResource(if (on) R.drawable.ic_star else R.drawable.ic_star_outline)
            star.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(activity, if (on) R.color.bb_star else R.color.bb_beige)
            )
        }
        d.textRatingLabel.setText(RATING_LABELS[rating])
    }

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
        private val RATING_LABELS = intArrayOf(
            R.string.review_rating_0, R.string.review_rating_1, R.string.review_rating_2,
            R.string.review_rating_3, R.string.review_rating_4, R.string.review_rating_5
        )
    }
}