package com.example.brewandbite.review

import android.content.res.ColorStateList
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.example.brewandbite.R
import kotlin.math.roundToInt

/**
 * 별 5개 그리기. 예) container.showStars(4.3, 14) → ★★★★☆
 * (0.5 단위는 반올림해서 꽉 찬 별 / 빈 별로만 보여 줌)
 */
fun LinearLayout.showStars(rating: Double, sizeDp: Int) {
    removeAllViews()
    val filled = rating.roundToInt().coerceIn(0, 5)
    val px = (sizeDp * resources.displayMetrics.density).toInt()
    val gap = (1 * resources.displayMetrics.density).toInt()
    repeat(5) { i ->
        val star = ImageView(context).apply {
            setImageResource(if (i < filled) R.drawable.ic_star else R.drawable.ic_star_outline)
            imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, if (i < filled) R.color.bb_star else R.color.bb_beige)
            )
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(star, LinearLayout.LayoutParams(px, px).apply { marginEnd = gap })
    }
}