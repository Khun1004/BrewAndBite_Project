package com.example.brewandbite.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.brewandbite.R
import com.example.brewandbite.data.Category
import com.example.brewandbite.databinding.ItemCategoryBinding

class CategoryAdapter(
    private val items: List<Category>,
    private val onCategoryClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    private var selectedPosition = 0

    inner class ViewHolder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val selected = position == selectedPosition

        with(holder.binding) {
            textEmoji.text = item.emoji
            textName.text = item.name
            textNameKo.text = item.nameKo

            // 선택된 카테고리는 브라운 배경 + 밝은 글씨
            cardCategory.setCardBackgroundColor(
                ContextCompat.getColor(context, if (selected) R.color.bb_brown else R.color.white)
            )
            cardCategory.strokeWidth = if (selected) 0 else context.resources.displayMetrics.density.toInt()
            textName.setTextColor(
                ContextCompat.getColor(context, if (selected) R.color.white else R.color.bb_espresso)
            )
            textNameKo.setTextColor(
                ContextCompat.getColor(context, if (selected) R.color.bb_beige else R.color.bb_muted)
            )

            root.setOnClickListener {
                val clicked = holder.bindingAdapterPosition
                if (clicked == RecyclerView.NO_POSITION || clicked == selectedPosition) return@setOnClickListener
                val previous = selectedPosition
                selectedPosition = clicked
                notifyItemChanged(previous)
                notifyItemChanged(clicked)
                onCategoryClick(items[clicked])
            }
        }
    }

    override fun getItemCount() = items.size
}