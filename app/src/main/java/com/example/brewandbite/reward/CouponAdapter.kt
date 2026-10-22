package com.example.brewandbite.reward

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.brewandbite.R
import com.example.brewandbite.data.toWon
import com.example.brewandbite.databinding.ItemCouponBinding

/** 쿠폰 목록 (쓸 수 있는 쿠폰 / 지난 쿠폰). 지난 쿠폰은 흐리게 + '사용 완료' 또는 '기간 만료' */
class CouponAdapter : ListAdapter<Coupon, CouponAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemCouponBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemCouponBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val c = getItem(position)
        val ctx = holder.itemView.context
        with(holder.binding) {
            val freeDrink = c.source == Coupon.SOURCE_STAMP
            imageCouponIcon.setImageResource(if (freeDrink) R.drawable.ic_coffee else R.drawable.ic_coupon)
            textAmount.text = if (freeDrink) ctx.getString(R.string.coupon_free) else c.amount.toWon()
            textAmountLabel.text = ctx.getString(if (freeDrink) R.string.coupon_free_label else R.string.coupon_discount_label)
            textCouponTitle.text = c.title
            textCondition.text = when {
                freeDrink -> ctx.getString(R.string.coupon_cond_free, c.amount.toWon())
                c.minOrder > 0 -> ctx.getString(R.string.coupon_cond_min, c.minOrder.toWon())
                else -> ctx.getString(R.string.coupon_cond_none)
            }
            textExpiry.text = ctx.getString(R.string.coupon_until, c.expiryText)

            textDday.visibility = View.VISIBLE
            textDday.text = when {
                c.used -> ctx.getString(R.string.coupon_used)
                c.expired -> ctx.getString(R.string.coupon_expired)
                c.daysLeft == 0 -> ctx.getString(R.string.coupon_today)
                else -> ctx.getString(R.string.coupon_dday, c.daysLeft)
            }
            root.alpha = if (c.usable) 1f else 0.45f
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Coupon>() {
            override fun areItemsTheSame(a: Coupon, b: Coupon) = a.id == b.id
            override fun areContentsTheSame(a: Coupon, b: Coupon) = a == b
        }
    }
}