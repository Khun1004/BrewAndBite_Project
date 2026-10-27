package com.example.brewandbite.favorite

import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.brewandbite.R
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.data.MenuRepository
import com.example.brewandbite.data.toWon
import com.example.brewandbite.databinding.ItemFavoriteBinding
import com.example.brewandbite.review.ReviewManager
import com.example.brewandbite.search.MenuSearch

/**
 * ♡ 찜한 메뉴 카드 (item_favorite.xml) — 한 메뉴 = 카드 한 장.
 *  - 메뉴 그림 칸은 카테고리마다 다른 색 (커피 베이지 · 음료 핑크 · 차 그린 · 디저트 로즈 · 베이커리 카라멜 · 식사 블루)
 *  - [☕ 커피] 꼬리표 · 이름 · 한글 이름 · 설명 · ★ 별점 · 리뷰 수 · "3일 전 찜"
 *  - ♥ 를 누르면 찜 해제 (카드가 목록에서 빠짐) · [🛒 담기] · [주문 ›] (메뉴 상세)
 */
class FavoriteAdapter(
    private val onOpen: (MenuItem) -> Unit,
    private val onAdd: (MenuItem) -> Unit
) : ListAdapter<MenuItem, FavoriteAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemFavoriteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemFavoriteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val ctx = holder.itemView.context
        val (soft, accent) = CATEGORY_COLORS[item.categoryId] ?: CATEGORY_COLORS.getValue("coffee")
        val category = MenuRepository.findCategory(item.categoryId)

        with(holder.binding) {
            // 카테고리 색 그림 칸
            plate.backgroundTintList = ColorStateList.valueOf(soft)
            textEmoji.text = item.emoji

            textCategory.text = "${category.emoji} ${category.nameKo}"
            textCategory.backgroundTintList = ColorStateList.valueOf(soft)
            textCategory.setTextColor(accent)

            textName.text = item.name
            val korean = MenuSearch.koreanName(item)
            textKorean.text = if (korean.isNotEmpty()) "$korean · ${item.description}" else item.description

            textRating.text = ReviewManager.ratingText(item)
            val reviews = ReviewManager.count(item.id)
            val at = FavoriteManager.favoritedAt(item.id)
            val ago = if (at > 0) DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) else ""
            // "· 리뷰 5 · 3일 전 찜"
            textMeta.text = listOfNotNull(
                ctx.getString(R.string.fav_meta_reviews, reviews).takeIf { reviews > 0 },
                ctx.getString(R.string.fav_meta_ago, ago).takeIf { ago.isNotEmpty() }
            ).joinToString("") { "· $it " }

            textPrice.text = item.price.toWon()
            buttonFavorite.bindFavorite(item)          // ♥ 누르면 찜 해제
            buttonAdd.setOnClickListener { onAdd(item) }
            buttonOrder.setOnClickListener { onOpen(item) }
            cardFavorite.setOnClickListener { onOpen(item) }
        }
    }

    companion object {
        /** 카테고리 → (연한 바탕색, 진한 글자색) */
        private val CATEGORY_COLORS = mapOf(
            "coffee" to (0xFFF1E8DA.toInt() to 0xFF6B4A36.toInt()),
            "drinks" to (0xFFFDE4EC.toInt() to 0xFFC2185B.toInt()),
            "tea" to (0xFFE1F2E7.toInt() to 0xFF2F7D4E.toInt()),
            "dessert" to (0xFFF9E0E4.toInt() to 0xFFB23A48.toInt()),
            "bakery" to (0xFFFBEBD5.toInt() to 0xFFA0652B.toInt()),
            "food" to (0xFFE3ECF7.toInt() to 0xFF3D5F91.toInt())
        )

        private val DIFF = object : DiffUtil.ItemCallback<MenuItem>() {
            override fun areItemsTheSame(a: MenuItem, b: MenuItem) = a.id == b.id
            override fun areContentsTheSame(a: MenuItem, b: MenuItem) = a == b
        }
    }
}