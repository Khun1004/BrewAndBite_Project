package com.example.brewandbite.notice

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.brewandbite.R
import com.example.brewandbite.databinding.ItemNoticeBinding

/** 알림 목록 한 줄: 아이콘 · 제목 · 내용 · "3분 전" · 안 읽었으면 빨간 점 + 베이지 바탕 */
class NoticeAdapter(
    private val onClick: (Notice) -> Unit,
    private val onLongClick: (Notice) -> Unit
) : ListAdapter<Notice, NoticeAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemNoticeBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemNoticeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val n = getItem(position)
        val ctx = holder.itemView.context
        with(holder.binding) {
            imageIcon.setImageResource(n.icon)
            textTitle.text = n.title
            textBody.text = n.body
            textTime.text = DateUtils.getRelativeTimeSpanString(
                n.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
            dotUnread.visibility = if (n.read) View.INVISIBLE else View.VISIBLE
            rowNotice.setBackgroundColor(
                ContextCompat.getColor(ctx, if (n.read) R.color.white else R.color.bb_beige_light)
            )
            rowNotice.setOnClickListener { onClick(n) }
            rowNotice.setOnLongClickListener { onLongClick(n); true }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Notice>() {
            override fun areItemsTheSame(a: Notice, b: Notice) = a.id == b.id
            override fun areContentsTheSame(a: Notice, b: Notice) = a == b
        }
    }
}