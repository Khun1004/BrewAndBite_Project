package com.example.brewandbite.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.data.toWon
import com.example.brewandbite.favorite.bindFavorite
import com.example.brewandbite.databinding.ItemMenuBinding

/** 홈 화면 '추천 메뉴' 세로 리스트 (카테고리/검색 결과에 따라 바뀜) */
class MenuAdapter(
    private val onItemClick: (MenuItem) -> Unit,
    private val onAddClick: (MenuItem) -> Unit
) : ListAdapter<MenuItem, MenuAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemMenuBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMenuBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            textEmoji.text = item.emoji
            textName.text = item.name
            textDescription.text = item.description
            textPrice.text = item.price.toWon()
            textRating.text = item.rating.toString()

            root.setOnClickListener { onItemClick(item) }
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