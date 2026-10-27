package com.example.brewandbite.order

import android.content.res.ColorStateList
import android.view.View
import androidx.core.content.ContextCompat
import com.example.brewandbite.R
import com.example.brewandbite.data.MenuRepository
import com.example.brewandbite.databinding.ItemOrderLineBinding
import com.example.brewandbite.review.ReviewManager

/**
 * 주문한 메뉴 한 줄 그리기 (item_order_line.xml) — Order 탭 · My Page 주문 기록에서 같이 사용.
 *  🍝 Cream Pasta × 1  /  옵션            [✏️ 리뷰 쓰기] · [✔ 작성 완료] · [완료 후 리뷰]
 *
 * @param record  이 주문의 Firebase 주문 기록 (아직 못 불러왔으면 null → 버튼 숨김)
 * @param onReview [리뷰 쓰기] 또는 [작성 완료](= 이 주문의 내 리뷰 고치기)를 눌렀을 때. canWrite = 새로 쓰는지
 *                 → 보통 ReviewEditor.open(context, view, menu, record.orderNo) 를 부르면 알아서 쓰기 / 고치기
 */
fun ItemOrderLineBinding.bindOrderLine(
    record: OrderRecord?,
    line: OrderLine,
    onReview: (canWrite: Boolean) -> Unit
) {
    val ctx = root.context
    textEmoji.text = line.emoji
    textName.text = ctx.getString(R.string.order_line_name, line.name, line.quantity)
    textOptions.text = line.options
    textOptions.visibility = if (line.options.isBlank()) View.GONE else View.VISIBLE

    if (record == null) {
        buttonReview.visibility = View.GONE
        return
    }
    buttonReview.visibility = View.VISIBLE
    // 이 주문으로 리뷰를 썼는지 (같은 메뉴라도 주문마다 따로)
    val reviewedHere = record.isReviewed(line.menuId) || ReviewManager.reviewFor(line.menuId, record.orderNo) != null
    val exists = MenuRepository.findItem(line.menuId) != null
    val (label, enabled) = when {
        !exists -> R.string.order_review_unavailable to false
        reviewedHere -> R.string.order_review_done to true          // 눌러서 내 리뷰 고치기
        !record.finished -> R.string.order_review_wait to false
        else -> R.string.order_review_write to true
    }
    val canWrite = enabled && !reviewedHere
    buttonReview.setText(label)
    buttonReview.isEnabled = enabled
    buttonReview.setTextColor(ContextCompat.getColor(ctx, if (canWrite) R.color.bb_espresso else R.color.bb_muted))
    buttonReview.strokeColor = ColorStateList.valueOf(
        ContextCompat.getColor(ctx, if (canWrite) R.color.bb_nav_active else R.color.bb_divider)
    )
    buttonReview.setOnClickListener { onReview(canWrite) }
}