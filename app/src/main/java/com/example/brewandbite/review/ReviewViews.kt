package com.example.brewandbite.review

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.brewandbite.R
import com.example.brewandbite.databinding.ItemReviewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * 리뷰 화면 조각들 (메뉴 상세의 리뷰 영역 · 리뷰 전체 보기 · My Page 리뷰 내역에서 같이 사용)
 *  - drawRatingBars : 5점 ~ 1점 막대
 *  - bindReview     : 리뷰 카드 1개 (item_review.xml)
 *  - showReviewPhotos : 📷 사진 줄 (누르면 크게)
 */

/** 5점 ~ 1점 막대: "5 ★ ██████ 8" */
fun LinearLayout.drawRatingBars(dist: IntArray, total: Int) {
    val ctx = context
    val density = resources.displayMetrics.density
    removeAllViews()
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
        addView(row)
    }
}

/**
 * 리뷰 카드 1개 채우기 (item_review.xml).
 * 내 리뷰면 [내 리뷰] 꼬리표 + 수정 · 삭제 버튼
 */
fun ItemReviewBinding.bindReview(
    menuId: String,
    r: Review,
    isMine: Boolean,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    // 카드: 손님마다 한 장 · 내 리뷰는 살구색 테두리
    cardReview.setBackgroundResource(if (isMine) R.drawable.bg_review_mine else R.drawable.bg_order_card)
    // 동그라미: 이름 첫 글자 + 손님마다 다른 색 (내 리뷰는 기본 커피색)
    textAvatar.text = r.name.take(1).uppercase()
    textAvatar.backgroundTintList =
        if (isMine) null else ColorStateList.valueOf(avatarColor(r.uid.ifEmpty { r.name }))
    textName.text = r.name
    textOptions.text = r.options
    textOptions.visibility = if (r.options.isNotBlank()) View.VISIBLE else View.GONE
    textMine.visibility = if (isMine) View.VISIBLE else View.GONE
    stars.showStars(r.rating.toDouble(), 13)
    textDate.text = r.dateText
    textReview.text = r.text
    photoRow.showReviewPhotos(menuId, r, 84)
    rowMine.visibility = if (isMine) View.VISIBLE else View.GONE
    buttonEdit.setOnClickListener { onEdit() }
    buttonDelete.setOnClickListener { onDelete() }
}

/**
 * 📷 리뷰 사진 줄. 사진이 없으면 숨김.
 * 사진은 Firebase 에서 따로 불러오므로 먼저 빈 칸(베이지)을 보여 주고, 다 불러오면 채운다.
 */
fun LinearLayout.showReviewPhotos(menuId: String, r: Review, sizeDp: Int) {
    removeAllViews()
    if (r.photoCount <= 0) {
        visibility = View.GONE
        return
    }
    visibility = View.VISIBLE
    val key = "$menuId/${r.id}/${r.updatedAt}"
    tag = key
    val px = (sizeDp * resources.displayMetrics.density).toInt()
    val gap = (8 * resources.displayMetrics.density).toInt()
    val corner = 12 * resources.displayMetrics.density
    val slots = (0 until r.photoCount).map {
        ShapeableImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(corner).build()
            setBackgroundColor(ContextCompat.getColor(context, R.color.bb_beige_light))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }.also { addView(it, LinearLayout.LayoutParams(px, px).apply { marginEnd = gap }) }
    }
    ReviewPhotos.load(menuId, r) { bitmaps ->
        if (tag != key) return@load   // 그 사이 다른 리뷰로 바뀐 칸이면 무시
        slots.forEachIndexed { i, slot ->
            val bmp = bitmaps.getOrNull(i) ?: return@forEachIndexed
            slot.setImageBitmap(bmp)
            slot.setOnClickListener { showPhotoLarge(context, bmp) }
        }
    }
}

/** 손님마다 다른 동그라미 색 (같은 사람은 항상 같은 색) */
private val AVATAR_COLORS = intArrayOf(
    0xFF8D6E63.toInt(), // 밀크 브라운
    0xFFC9503F.toInt(), // 토마토
    0xFF2F9E5B.toInt(), // 그린
    0xFF4A6FA5.toInt(), // 블루
    0xFFB0793A.toInt(), // 카라멜
    0xFF7E57C2.toInt(), // 라벤더
    0xFF00897B.toInt(), // 민트
    0xFFD81B60.toInt()  // 베리
)

private fun avatarColor(key: String): Int = AVATAR_COLORS[Math.floorMod(key.hashCode(), AVATAR_COLORS.size)]

/** 사진 크게 보기 */
fun showPhotoLarge(context: Context, bitmap: Bitmap) {
    val image = ImageView(context).apply {
        setImageBitmap(bitmap)
        adjustViewBounds = true
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    MaterialAlertDialogBuilder(context)
        .setView(image)
        .setPositiveButton(R.string.auth_close, null)
        .show()
}