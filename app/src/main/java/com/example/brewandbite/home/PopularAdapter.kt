package com.example.brewandbite.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.data.toWon
import com.example.brewandbite.databinding.ItemPopularBinding

/** 홈 화면 '인기 메뉴' 가로 카드 */
class PopularAdapter(
    private val items: List<MenuItem>,
    private val onItemClick: (MenuItem) -> Unit,
    private val onAddClick: (MenuItem) -> Unit
) : RecyclerView.Adapter<PopularAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemPopularBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPopularBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            textEmoji.text = item.emoji
            textName.text = item.name
            textCategory.text = item.description
            textPrice.text = item.price.toWon()
            textRating.text = item.rating.toString()

            root.setOnClickListener { onItemClick(item) }
            buttonAdd.setOnClickListener { onAddClick(item) }
        }
    }

    override fun getItemCount() = items.size
}