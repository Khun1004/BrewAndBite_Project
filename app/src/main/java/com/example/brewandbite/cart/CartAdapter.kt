package com.example.brewandbite.cart

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.brewandbite.R
import com.example.brewandbite.data.toWon
import com.example.brewandbite.databinding.ItemCartBinding

/** 장바구니 목록 한 줄씩 그리기 (이미지가 카드 위로 튀어나온 디자인, 줄마다 좌우 번갈아) */
class CartAdapter(
    private val onIncrease: (CartItem) -> Unit,
    private val onDecrease: (CartItem) -> Unit,
    private val onRemove: (CartItem) -> Unit
) : ListAdapter<CartItem, CartAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemCartBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SIDE)) {
            applySide(holder, position)   // 줄 위치만 바뀐 경우: 이미지 방향만 다시
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        applySide(holder, position)

        with(holder.binding) {
            textEmoji.text = item.emoji
            textName.text = item.name
            // 옵션이 있으면 같이 표시. 예) "ICE · Large · 개당 5,500원"
            val unit = context.getString(R.string.cart_unit_price, item.price.toWon())
            textUnitPrice.text = if (item.options.isBlank()) unit else "${item.options} · $unit"
            textQuantity.text = item.quantity.toString()
            textLineTotal.text = item.totalPrice.toWon()

            buttonPlus.setOnClickListener { onIncrease(item) }
            buttonMinus.setOnClickListener { onDecrease(item) }
            buttonRemove.setOnClickListener { onRemove(item) }
        }
    }

    /** 짝수 줄은 이미지 왼쪽, 홀수 줄은 오른쪽 (좌우만 뒤집고 글자 순서는 그대로) */
    private fun applySide(holder: ViewHolder, position: Int) {
        holder.binding.itemRoot.layoutDirection =
            if (position % 2 == 0) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
    }

    /** 삭제 등으로 줄 순서가 바뀐 뒤 좌우 배치를 다시 맞춘다 (CartFragment에서 호출) */
    fun refreshSides() {
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SIDE)
    }

    companion object {
        private const val PAYLOAD_SIDE = "side"

        private val DIFF = object : DiffUtil.ItemCallback<CartItem>() {
            override fun areItemsTheSame(oldItem: CartItem, newItem: CartItem) = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: CartItem, newItem: CartItem) = oldItem == newItem
        }
    }
}
