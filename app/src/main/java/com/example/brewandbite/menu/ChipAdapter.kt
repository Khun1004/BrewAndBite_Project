package com.example.brewandbite.menu

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.brewandbite.R
import com.example.brewandbite.data.Category
import com.example.brewandbite.databinding.ItemChipBinding

/** 메뉴 목록 화면 위쪽의 카테고리 칩 (가로 스크롤) */
class ChipAdapter(
    private val items: List<Category>,
    private var selectedId: String,
    private val onChipClick: (Category) -> Unit
) : RecyclerView.Adapter<ChipAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemChipBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val selected = item.id == selectedId

        with(holder.binding.textChip) {
            text = "${item.emoji}  ${item.name}"
            setBackgroundResource(if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip)
            setTextColor(ContextCompat.getColor(context, if (selected) R.color.bb_nav_active else R.color.bb_espresso))
            setOnClickListener {
                if (item.id == selectedId) return@setOnClickListener
                val oldIndex = items.indexOfFirst { it.id == selectedId }
                selectedId = item.id
                notifyItemChanged(oldIndex)
                notifyItemChanged(holder.bindingAdapterPosition)
                onChipClick(item)
            }
        }
    }

    override fun getItemCount() = items.size

    fun selectedIndex(): Int = items.indexOfFirst { it.id == selectedId }
}