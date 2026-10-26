package com.example.brewandbite.notice

import android.content.res.ColorStateList
import android.graphics.Typeface
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
import com.example.brewandbite.databinding.ItemNoticeHeaderBinding
import java.util.Calendar

/**
 * 알림 목록: 날짜 묶음 제목(오늘 / 어제 / 이번 주 / 이전) + 알림 카드.
 *  카드 = 종류별 색 동그라미 아이콘 · [쿠폰] 꼬리표 · 시간 · 제목 · 내용
 *  안 읽은 알림: 살구색 테두리 + 빨간 점 + 굵은 제목 / 읽은 알림: 연한 테두리 + 흐린 글자
 */
class NoticeAdapter(
    private val onClick: (Notice) -> Unit,
    private val onLongClick: (Notice) -> Unit
) : ListAdapter<NoticeAdapter.Row, RecyclerView.ViewHolder>(DIFF) {

    /** 목록 한 줄: 날짜 제목 또는 알림 */
    sealed class Row {
        data class Header(val title: Int) : Row()
        data class Item(val notice: Notice) : Row()
    }

    class HeaderHolder(val binding: ItemNoticeHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    class ItemHolder(val binding: ItemNoticeBinding) : RecyclerView.ViewHolder(binding.root)

    /** 알림 목록(최신 순)을 날짜 묶음으로 나눠서 보여 주기 */
    fun submitNotices(notices: List<Notice>) {
        val rows = mutableListOf<Row>()
        var lastHeader = 0
        notices.forEach { n ->
            val header = dayHeader(n.createdAt)
            if (header != lastHeader) {
                rows += Row.Header(header)
                lastHeader = header
            }
            rows += Row.Item(n)
        }
        submitList(rows)
    }

    override fun getItemViewType(position: Int) = if (getItem(position) is Row.Header) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) HeaderHolder(ItemNoticeHeaderBinding.inflate(inflater, parent, false))
        else ItemHolder(ItemNoticeBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is Row.Header -> (holder as HeaderHolder).binding.textHeader.setText(row.title)
            is Row.Item -> bindItem(holder as ItemHolder, row.notice)
        }
    }

    private fun bindItem(holder: ItemHolder, n: Notice) {
        val ctx = holder.itemView.context
        fun color(id: Int) = ContextCompat.getColor(ctx, id)
        val (bg, fg) = n.colors
        with(holder.binding) {
            // 종류별 색 동그라미
            imageIcon.setImageResource(n.icon)
            imageIcon.backgroundTintList = ColorStateList.valueOf(color(bg))
            imageIcon.imageTintList = ColorStateList.valueOf(color(fg))

            // [쿠폰] 꼬리표 · 3분 전
            textType.setText(n.label)
            textType.backgroundTintList = ColorStateList.valueOf(color(bg))
            textType.setTextColor(color(fg))
            textTime.text = DateUtils.getRelativeTimeSpanString(
                n.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )

            textTitle.text = n.title
            textBody.text = n.body

            // 안 읽음 / 읽음
            val unread = !n.read
            dotUnread.visibility = if (unread) View.VISIBLE else View.GONE
            cardNotice.strokeColor = color(if (unread) R.color.bb_nav_active else R.color.bb_divider)
            cardNotice.strokeWidth = (ctx.resources.displayMetrics.density * if (unread) 1.5f else 1f).toInt()
            textTitle.setTypeface(null, if (unread) Typeface.BOLD else Typeface.NORMAL)
            textTitle.setTextColor(color(if (unread) R.color.bb_espresso else R.color.bb_brown))
            textBody.setTextColor(color(if (unread) R.color.bb_brown else R.color.bb_muted))
            imageIcon.alpha = if (unread) 1f else 0.7f

            cardNotice.setOnClickListener { onClick(n) }
            cardNotice.setOnLongClickListener { onLongClick(n); true }
        }
    }

    /** 오늘 / 어제 / 이번 주 / 이전 */
    private fun dayHeader(time: Long): Int {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val day = DateUtils.DAY_IN_MILLIS
        return when {
            time >= start -> R.string.notice_today
            time >= start - day -> R.string.notice_yesterday
            time >= start - 6 * day -> R.string.notice_this_week
            else -> R.string.notice_earlier
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1

        private val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(a: Row, b: Row) = when {
                a is Row.Header && b is Row.Header -> a.title == b.title
                a is Row.Item && b is Row.Item -> a.notice.id == b.notice.id
                else -> false
            }
            override fun areContentsTheSame(a: Row, b: Row) = a == b
        }
    }
}