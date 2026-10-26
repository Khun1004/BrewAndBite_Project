package com.example.brewandbite.review

import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.brewandbite.R
import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.auth.LoginActivity
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.databinding.DialogReviewBinding
import com.example.brewandbite.order.OrderHistory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * ⭐ 리뷰 쓰기 / 수정 창 (dialog_review.xml).
 * 메뉴 상세 화면(ReviewSection)과 Order 탭의 [리뷰 쓰기]에서 같이 쓴다.
 *  - 별 5개를 눌러 별점 → 글 10~200자 → [등록]
 *  - 주문했고 배달·픽업이 끝난 메뉴만 (아니면 안내만 보여 줌)
 *
 * 사용법: ReviewEditor.open(context, 화면의 아무 View, menuItem)
 */
object ReviewEditor {

    /**
     * 리뷰 쓰기 / 수정 창 열기.
     * @param anchor 안내 메시지(Snackbar)를 띄울 화면
     * @param orderNo 어느 주문의 리뷰인지 (Order 탭에서). 모르면 null → 리뷰 가능한 가장 최근 주문
     * @param onSaved 저장이 끝나면 (화면 다시 그리기 등)
     */
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
        val old = ReviewManager.myReview(item.id)
        val order = orderNo?.let { no -> OrderHistory.all().find { it.orderNo == no } }
            ?: OrderHistory.reviewableOrder(item.id)
        val d = DialogReviewBinding.inflate(LayoutInflater.from(activity))
        var rating = old?.rating ?: 0
        // 예) "🍝 Cream Pasta · 10.26 (월) 오후 3:12 주문"
        d.textMenu.text = if (old == null && order != null)
            activity.getString(R.string.review_dialog_order, item.emoji, item.name, order.dateText)
        else "${item.emoji} ${item.name}"
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
                    paintPicker(activity, d, rating, stars)
                }
            }
            stars += star
            d.starPicker.addView(star, LinearLayout.LayoutParams((44 * density).toInt(), (44 * density).toInt()))
        }
        paintPicker(activity, d, rating, stars)

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
                    ReviewManager.save(item.id, rating, text, order?.orderNo) { ok ->
                        button.isEnabled = true
                        if (ok) {
                            dialog.dismiss()
                            onSaved()
                        }
                        Toast.makeText(activity, if (ok) R.string.review_saved else R.string.review_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /** 별점 고르기 창의 별 색 + 아래 글자 (최고예요! 😍 등) */
    private fun paintPicker(activity: Context, d: DialogReviewBinding, rating: Int, stars: List<ImageView>) {
        stars.forEachIndexed { i, star ->
            val on = i < rating
            star.setImageResource(if (on) R.drawable.ic_star else R.drawable.ic_star_outline)
            star.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(activity, if (on) R.color.bb_star else R.color.bb_beige)
            )
        }
        d.textRatingLabel.setText(RATING_LABELS[rating])
    }

    private val RATING_LABELS = intArrayOf(
        R.string.review_rating_0, R.string.review_rating_1, R.string.review_rating_2,
        R.string.review_rating_3, R.string.review_rating_4, R.string.review_rating_5
    )
}