package com.example.brewandbite.notice

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.brewandbite.MainActivity
import com.example.brewandbite.R
import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.auth.LoginActivity
import com.example.brewandbite.common.applyStatusBarPadding
import com.example.brewandbite.data.MenuRepository
import com.example.brewandbite.databinding.ActivityNoticeBinding
import com.example.brewandbite.menu.MenuDetailActivity
import com.example.brewandbite.mypage.MyDetailActivity
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 🔔 알림함 화면. Home / My Page 상세 헤더의 🔔 를 누르면 열린다.
 *  헤더: [<] 뒤로 · 알림 (가운데) · [모두 읽음] (오른쪽)
 *  - 위쪽 필터 칩: 전체 · 혜택(쿠폰·스탬프) · 주문 · 소식(추천·이벤트)
 *  - 목록은 날짜별(오늘 / 어제 / 이번 주 / 이전)로 묶어서 카드로 보여 줌
 *  - 알림을 누르면 읽음 처리 + 해당 화면으로 (쿠폰·스탬프 → 쿠폰함, 주문 → Order 탭, 메뉴 → 메뉴 상세)
 *  - 길게 누르면 삭제
 *  - 로그인 안 했으면 로그인 안내
 *
 * 여는 법: NoticeActivity.start(context)
 */
class NoticeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoticeBinding
    private val adapter = NoticeAdapter(onClick = ::open, onLongClick = ::confirmDelete)
    private val listener = NoticeManager.NoticeListener { render() }

    /** 지금 고른 필터 (null = 전체) */
    private var filter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNoticeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
        binding.headerBox.applyStatusBarPadding()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonReadAll.setOnClickListener { NoticeManager.markAllRead() }
        binding.buttonLogin.setOnClickListener { startActivity(LoginActivity.intent(this)) }

        binding.recyclerNotice.layoutManager = LinearLayoutManager(this)
        binding.recyclerNotice.adapter = adapter
        setupFilters()
    }

    /** 전체 · 🎟 혜택 · 🧾 주문 · ✨ 소식 (고른 칩은 에스프레소 바탕) */
    private fun setupFilters() {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        fun colors(on: Int, off: Int) = ColorStateList(
            states, intArrayOf(ContextCompat.getColor(this, on), ContextCompat.getColor(this, off))
        )
        val filters = listOf(
            R.string.notice_filter_all to null,
            R.string.notice_filter_benefit to Notice.GROUP_BENEFIT,
            R.string.notice_filter_order to Notice.GROUP_ORDER,
            R.string.notice_filter_news to Notice.GROUP_NEWS
        )
        filters.forEach { (label, group) ->
            val chip = Chip(this).apply {
                setText(label)
                isCheckable = true
                isCheckedIconVisible = false
                isChecked = group == filter
                chipBackgroundColor = colors(R.color.bb_espresso, R.color.white)
                setTextColor(colors(R.color.bb_cream, R.color.bb_brown))
                chipStrokeColor = colors(R.color.bb_espresso, R.color.bb_divider)
                chipStrokeWidth = resources.displayMetrics.density
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        filter = group
                        render()
                    }
                }
            }
            binding.chipsFilter.addView(chip)
        }
    }

    override fun onStart() {
        super.onStart()
        NoticeManager.addListener(listener)
        render()
    }

    override fun onStop() {
        super.onStop()
        NoticeManager.removeListener(listener)
    }

    private fun render() {
        val loggedIn = AuthManager.isLoggedIn()
        val list = NoticeManager.all().filter { filter == null || it.group == filter }
        val unread = NoticeManager.unreadCount()

        binding.loggedOutView.visibility = if (loggedIn) View.GONE else View.VISIBLE
        binding.filterBar.visibility = if (loggedIn) View.VISIBLE else View.GONE
        binding.emptyView.visibility = if (loggedIn && list.isEmpty()) View.VISIBLE else View.GONE
        binding.textEmptyTitle.setText(if (filter == null) R.string.notice_empty else R.string.notice_empty_filter)
        binding.recyclerNotice.visibility = if (loggedIn && list.isNotEmpty()) View.VISIBLE else View.GONE

        binding.textSubtitle.text =
            if (unread > 0) getString(R.string.notice_unread, unread) else getString(R.string.notice_all_read)
        binding.buttonReadAll.visibility = if (unread > 0) View.VISIBLE else View.INVISIBLE
        adapter.submitNotices(list)
    }

    /** 알림 누르기: 읽음 처리 → 알맞은 화면으로 */
    private fun open(n: Notice) {
        NoticeManager.markRead(n.id)
        when (n.type) {
            Notice.TYPE_COUPON, Notice.TYPE_STAMP, Notice.TYPE_EVENT ->
                startActivity(MyDetailActivity.intent(this, MyDetailActivity.Section.COUPONS))
            Notice.TYPE_ORDER -> MainActivity.openTab(this, MainActivity.TAB_ORDER)
            Notice.TYPE_MENU ->
                if (MenuRepository.findItem(n.link) != null) MenuDetailActivity.start(this, n.link)
        }
    }

    private fun confirmDelete(n: Notice) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.notice_delete_title)
            .setMessage(n.title)
            .setNegativeButton(R.string.cart_cancel, null)
            .setPositiveButton(R.string.my_delete) { _, _ -> NoticeManager.delete(n.id) }
            .show()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, NoticeActivity::class.java))
        }
    }
}