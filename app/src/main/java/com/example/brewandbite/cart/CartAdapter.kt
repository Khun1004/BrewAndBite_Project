package com.example.brewandbite.cart

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.brewandbite.R
import com.example.brewandbite.data.toWon
import com.example.brewandbite.databinding.ItemCartBinding

/** 장바구니 목록 한 줄씩 그리기 */
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

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context

        with(holder.binding) {
            textEmoji.text = item.emoji
            textName.text = item.name
            textUnitPrice.text = context.getString(R.string.cart_unit_price, item.price.toWon())
            textQuantity.text = item.quantity.toString()
            textLineTotal.text = item.totalPrice.toWon()

            buttonPlus.setOnClickListener { onIncrease(item) }
            buttonMinus.setOnClickListener { onDecrease(item) }
            buttonRemove.setOnClickListener { onRemove(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CartItem>() {
            override fun areItemsTheSame(oldItem: CartItem, newItem: CartItem) = oldItem.menuId == newItem.menuId
            override fun areContentsTheSame(oldItem: CartItem, newItem: CartItem) = oldItem == newItem
        }
    }
}