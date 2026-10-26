package com.example.brewandbite.menu

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.data.toWon
import com.example.brewandbite.databinding.ItemMenuGridBinding
import com.example.brewandbite.favorite.bindFavorite
import com.example.brewandbite.review.ReviewManager

/** 메뉴 목록 화면의 2열 카드 */
class MenuGridAdapter(
    private val onItemClick: (MenuItem) -> Unit,
    private val onAddClick: (MenuItem) -> Unit
) : ListAdapter<MenuItem, MenuGridAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemMenuGridBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMenuGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            textEmoji.text = item.emoji
            textName.text = item.name
            textDescription.text = item.description
            textPrice.text = item.price.toWon()
            textRating.text = ReviewManager.ratingText(item)   // 리뷰 평균 (없으면 원래 별점)

            cardMenu.setOnClickListener { onItemClick(item) }
            buttonAdd.setOnClickListener { onAddClick(item) }
            buttonFavorite.bindFavorite(item)   // ♡ 찜하기
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MenuItem>() {
            override fun areItemsTheSame(oldItem: MenuItem, newItem: MenuItem) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: MenuItem, newItem: MenuItem) = oldItem == newItem
        }
    }
}