package com.example.brewandbite.reward

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.example.brewandbite.R
import com.example.brewandbite.databinding.LayoutStampCardBinding

/**
 * 스탬프 카드 그리기 (layout_stamp_card.xml).
 *  - 위 5개 · 아래 5개 동그라미. 모은 만큼 살구색 + 커피잔, 나머지는 점선
 *  - 10번째 칸은 선물 아이콘 (무료 음료)
 *  - 오른쪽 아래: 쓸 수 있는 쿠폰 수
 */
object StampCard {

    fun bind(b: LayoutStampCardBinding) {
        val ctx = b.root.context
        val stamps = RewardManager.stamps()
        val toGo = RewardManager.stampsToGo()
        b.textStampCount.text = ctx.getString(R.string.stamp_count, stamps, RewardManager.STAMPS_PER_COUPON)
        b.textStampHint.text =
            if (toGo == 1) ctx.getString(R.string.stamp_hint_last)
            else ctx.getString(R.string.stamp_hint, toGo)
        b.textCouponCount.text = ctx.getString(R.string.stamp_coupon_count, RewardManager.activeCoupons().size)

        fillRow(b.stampRow1, 0, stamps)
        fillRow(b.stampRow2, 5, stamps)
    }

    /** start 번째부터 5칸 */
    private fun fillRow(row: LinearLayout, start: Int, stamps: Int) {
        val ctx = row.context
        val density = ctx.resources.displayMetrics.density
        val size = (38 * density).toInt()
        val pad = (9 * density).toInt()
        row.removeAllViews()
        for (i in start until start + 5) {
            val filled = i < stamps
            val last = i == RewardManager.STAMPS_PER_COUPON - 1
            val icon = ImageView(ctx).apply {
                setBackgroundResource(if (filled) R.drawable.bg_stamp_filled else R.drawable.bg_stamp_empty)
                setImageResource(if (last) R.drawable.ic_gift else R.drawable.ic_coffee)
                setPadding(pad, pad, pad, pad)
                imageTintList = ColorStateList.valueOf(
                    if (filled) ContextCompat.getColor(ctx, R.color.bb_espresso)
                    else if (last) ContextCompat.getColor(ctx, R.color.bb_nav_active)
                    else ContextCompat.getColor(ctx, R.color.bb_glass_stroke)
                )
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            // 칸마다 같은 너비로 나누고 동그라미는 가운데
            val cell = FrameLayout(ctx)
            cell.addView(icon, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
            row.addView(cell, LinearLayout.LayoutParams(0, size, 1f))
        }
    }
}