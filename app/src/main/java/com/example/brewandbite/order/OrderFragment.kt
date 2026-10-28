package com.example.brewandbite.order


import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.brewandbite.MainActivity
import com.example.brewandbite.R
import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.common.setup
import com.example.brewandbite.data.MenuRepository
import com.example.brewandbite.data.toWon
import com.example.brewandbite.databinding.FragmentOrderBinding
import com.example.brewandbite.databinding.ItemOrderCardBinding
import com.example.brewandbite.databinding.ItemOrderDateBinding
import com.example.brewandbite.databinding.ItemOrderLineBinding
import com.example.brewandbite.databinding.LayoutOrderTrackingBinding
import com.example.brewandbite.menu.MenuDetailActivity
import com.example.brewandbite.review.ReviewEditor
import com.example.brewandbite.review.ReviewManager
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/**
 * Order 탭
 *  ① 진행 중인 주문 — 배달 현황 카드 (주문이 여러 건이면 여러 장)
 *     상태 · 남은 시간 · 진행 바 위를 움직이는 🛵 · 단계 아이콘(접수 → 준비 → 배달 중 → 도착) · 메뉴 상세
 *     끝난 뒤 10분 동안은 "배달 완료 🎉" 카드로 남아서 바로 리뷰를 쓸 수 있다
 *  ② 주문 내역 — 기간 칩(오늘 · 최근 7일 · 최근 30일 · 전체) + 날짜별(오늘 / 어제 / 10.24 (금)) 주문 카드
 *     카드의 > 를 누르면 주문 상세 화면(OrderDetailActivity): 메뉴마다 [✏️ 리뷰 쓰기] · [🛒 다시 담기]
 *
 * 데이터: Firebase 주문 기록(OrderHistory). 로그인 전에는 휴대폰에 저장된 마지막 주문(OrderManager)만.
 */
class OrderFragment : Fragment() {

    private var _binding: FragmentOrderBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000)
        }
    }
    private val timeFormat = SimpleDateFormat("a h:mm", Locale.KOREA)
    private val dateFormat = SimpleDateFormat("M.d (E)", Locale.KOREA)

    /** 기간 필터 */
    private enum class Period(val label: Int, val days: Int) {
        TODAY(R.string.order_period_today, 1),
        WEEK(R.string.order_period_week, 7),
        MONTH(R.string.order_period_month, 30),
        ALL(R.string.order_period_all, 0)
    }

    private var period = Period.WEEK

    /** 펼친 주문 카드 (주문번호) */
    private val expanded = mutableSetOf<String>()

    /** 지금 화면에 있는 배달 현황 카드들 */
    private val trackers = mutableListOf<Tracker>()
    private var trackingKey = ""

    /** 주문 기록에 채워 넣은 주문번호 (주문 기록 기능 전에 한 주문, 한 번만) */
    private var backfilled = ""
    private val riderStage = mutableMapOf<String, Int>()
    private val historyListener = OrderHistory.HistoryListener { refreshAll() }
    private val reviewListener = ReviewManager.ReviewListener { refreshAll() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pageHeader.setup(
            R.string.header_order_eyebrow, R.string.header_order_title, R.drawable.ic_receipt,
            getString(R.string.header_order_subtitle)
        )
        binding.buttonGoMenu.setOnClickListener { (requireActivity() as MainActivity).showTab(MainActivity.TAB_HOME) }
        setupPeriodChips()
    }

    // 탭은 hide/show로 전환되므로 보일 때만 1초마다 갱신
    override fun onStart() {
        super.onStart()
        OrderHistory.addListener(historyListener)
        ReviewManager.addListener(reviewListener)
        refreshAll()
        if (!isHidden) startTicker()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(ticker)
        OrderHistory.removeListener(historyListener)
        ReviewManager.removeListener(reviewListener)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) handler.removeCallbacks(ticker) else {
            refreshAll()
            startTicker()
        }
    }

    private fun startTicker() {
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(ticker)
        trackers.forEach { it.stop() }
        trackers.clear()
        trackingKey = ""
        super.onDestroyView()
        _binding = null
    }

    // ───────── 데이터 ─────────

    /** 전체 주문 (최신 순). 로그인 전이면 휴대폰에 저장된 마지막 주문만 */
    private fun records(): List<OrderRecord> {
        val history = OrderHistory.all()
        val local = OrderManager.current() ?: return history
        if (history.any { it.orderNo == local.orderNo }) return history
        // 주문 기록 기능 전에 한 주문이면 Firebase 에 한 번 채워 넣기 (같은 주문번호라 중복 안 됨)
        if (AuthManager.isLoggedIn() && backfilled != local.orderNo && OrderHistory.save(local)) backfilled = local.orderNo
        return listOf(local.toRecord()) + history
    }

    /** 배달 현황 카드로 보여 줄 주문: 진행 중 + 끝난 지 10분 안 된 주문 */
    private fun activeRecords(now: Long) = records().filter { now < it.finishAt + RECENT_DONE_MS }

    // ───────── 1초마다 ─────────

    private fun tick() {
        val b = _binding ?: return
        val now = System.currentTimeMillis()
        val active = activeRecords(now)
        // 진행 중인 주문이 생기거나 끝났으면 전체 다시 그리기
        val key = active.joinToString { "${it.orderNo}:${it.stageIndex(now)}" }
        if (key != trackingKey) {
            refreshAll()
            return
        }
        trackers.forEach { it.update(now) }
        b.pageHeader.textSubtitle.text = headerSubtitle(active.count { now < it.finishAt })
    }

    /** 배달 현황 + 주문 내역 다시 그리기 */
    private fun refreshAll() {
        val b = _binding ?: return
        val now = System.currentTimeMillis()
        val active = activeRecords(now)
        trackingKey = active.joinToString { "${it.orderNo}:${it.stageIndex(now)}" }
        renderTracking(active, now)
        renderHistory(now)
        b.pageHeader.textSubtitle.text = headerSubtitle(active.count { now < it.finishAt })
    }

    private fun headerSubtitle(running: Int) =
        if (running > 0) getString(R.string.order_header_running, running) else getString(R.string.header_order_subtitle)

    // ───────── ① 배달 현황 ─────────

    private fun renderTracking(active: List<OrderRecord>, now: Long) {
        val b = _binding ?: return
        b.trackingSection.visibility = if (active.isEmpty()) View.GONE else View.VISIBLE
        val running = active.count { now < it.finishAt }
        b.textTrackingTitle.text =
            if (running > 0) getString(R.string.order_tracking_title, running) else getString(R.string.order_tracking_done_title)
        b.trackingList.removeAllViews()
        trackers.forEach { it.stop() }
        trackers.clear()
        active.forEach { record ->
            val t = Tracker(LayoutOrderTrackingBinding.inflate(layoutInflater, b.trackingList, false), record)
            b.trackingList.addView(t.b.root)
            trackers += t
            t.update(now)
        }
    }

    /** 배달 현황 카드 1장 */
    private inner class Tracker(val b: LayoutOrderTrackingBinding, val record: OrderRecord) {
        private val stages = record.stages()
        private val stepIcons = if (record.delivery)
            listOf(R.drawable.ic_receipt, R.drawable.ic_coffee, R.drawable.ic_delivery, R.drawable.ic_home)
        else listOf(R.drawable.ic_receipt, R.drawable.ic_coffee, R.drawable.ic_takeout)
        private val icons = mutableListOf<ImageView>()
        private val labels = mutableListOf<TextView>()
        private var lastStage = -1
        private var painted = false
        private var pulse: ObjectAnimator? = null
        private val riderStage = mutableMapOf<String, Int>()
        init {
            b.textOrderNo.text = record.orderNo
            b.textMode.text = getString(if (record.delivery) R.string.order_mode_delivery_chip else R.string.order_mode_pickup_chip)
            b.textSummary.text = getString(R.string.order_summary_total, record.summary, record.total.toWon())
            buildSteps()
            buildDetail()
            // 끝난 주문은 바로 펼쳐서 리뷰 버튼이 보이게
            setExpanded(record.orderNo in expanded || System.currentTimeMillis() >= record.finishAt)
            b.rowSummary.setOnClickListener { setExpanded(b.detailBox.visibility != View.VISIBLE) }
        }

        private fun setExpanded(open: Boolean) {
            b.detailBox.visibility = if (open) View.VISIBLE else View.GONE
            b.textToggle.setText(if (open) R.string.order_detail_close else R.string.order_detail_open)
            if (open) expanded += record.orderNo else expanded -= record.orderNo
        }

        /** 단계 아이콘 + 이름 + 시간 */
        private fun buildSteps() {
            val ctx = requireContext()
            val d = resources.displayMetrics.density

            stages.forEachIndexed { i, stage ->
                val col = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                val icon = ImageView(ctx).apply {
                    setImageResource(stepIcons[i])
                    val pad = (8 * d).toInt()
                    setPadding(pad, pad, pad, pad)
                }
                col.addView(icon, LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt()))
                val label = TextView(ctx).apply {
                    text = stage.title
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setPadding(0, (6 * d).toInt(), 0, 0)
                }
                col.addView(label)
                col.addView(TextView(ctx).apply {
                    text = timeFormat.format(Date(record.placedAt + stage.startMs))
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setTextColor(ContextCompat.getColor(ctx, R.color.bb_nav_inactive))
                })
                b.stepRow.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                icons += icon
                labels += label
            }
        }

        /** 펼친 상세: 메뉴 줄(리뷰 버튼) + 금액 */
        private fun buildDetail() {
            b.lineList.removeAllViews()
            record.items.forEach { line -> b.lineList.addView(lineView(record, line, b.lineList)) }
            b.textPayInfo.text = payInfo(record)
        }

        fun stop() {
            pulse?.cancel()
        }

        fun update(now: Long) {
            val last = stages.lastIndex
            val cur = record.stageIndex(now)
            val finished = now >= record.finishAt

            b.textStatus.text = statusText(record.delivery, cur, finished)
            val endLabel = timeFormat.format(Date(record.finishAt))
            b.textEta.text = if (finished) {
                getString(if (record.delivery) R.string.order_eta_done_delivery else R.string.order_eta_done_pickup, endLabel)
            } else {
                val minLeft = ceil((record.finishAt - now) / 60_000.0).toInt().coerceAtLeast(1)
                getString(if (record.delivery) R.string.order_eta_delivery else R.string.order_eta_pickup, minLeft, endLabel)
            }

            // 진행 바 + 움직이는 오토바이
            val ratio = ((now - record.placedAt).toDouble() / (record.finishAt - record.placedAt)).coerceIn(0.0, 1.0)
            b.progressOrder.progress = (ratio * 1000).toInt()
            b.trackBox.post {
                val room = b.trackBox.width - b.imageRider.width
                if (room > 0) b.imageRider.translationX = (room * ratio).toFloat()
            }

            // 단계에 맞는 아이콘이 진행 바 위를 움직인다 (접수 → 준비 → 배달/포장 → 도착)
            b.imageRider.setImageResource(stepIcons[cur])
            val before = riderStage[record.orderNo]
            if (before != null && before != cur) {
                b.imageRider.scaleX = 0.5f
                b.imageRider.scaleY = 0.5f
                b.imageRider.animate().scaleX(1f).scaleY(1f).setDuration(400)
                    .setInterpolator(OvershootInterpolator(2.5f)).start()
            }
            riderStage[record.orderNo] = cur

            if (cur != lastStage) {
                lastStage = cur
                paintSteps(cur, finished, last)
            }
        }

        /** 지난 단계: ✓ · 지금 단계: 자기 아이콘 + 두근두근 · 남은 단계: 흐리게 */
        private fun paintSteps(cur: Int, finished: Boolean, last: Int) {
            val ctx = requireContext()
            val firstPaint = !painted
            painted = true
            pulse?.cancel()
            icons.forEachIndexed { i, icon ->
                val reached = i <= cur

                // 지난 단계는 체크, 나머지는 원래 아이콘
                icon.setImageResource(stepIcons[i])
                icon.setBackgroundResource(if (reached) R.drawable.bg_stamp_filled else R.drawable.bg_stamp_empty)
                icon.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, if (reached) R.color.bb_espresso else R.color.bb_glass_stroke)
                )
                icon.alpha = if (reached) 1f else 0.5f
                icon.scaleX = 1f
                icon.scaleY = 1f
                labels[i].setTextColor(ContextCompat.getColor(ctx, if (reached) R.color.bb_cream else R.color.bb_nav_inactive))
                labels[i].setTypeface(null, if (i == cur) Typeface.BOLD else Typeface.NORMAL)
            }
            // 지금 단계 아이콘만 계속 두근두근 (끝났으면 멈춤)
            if (!finished && cur <= last) {
                val icon = icons[cur]
                pulse = ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.15f).apply {
                    duration = 700
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener { icon.scaleY = icon.scaleX }
                    start()
                }
            }
        }
    }

    private fun statusText(isDelivery: Boolean, stage: Int, finished: Boolean): String = getString(
        when {
            finished && isDelivery -> R.string.order_status_text_delivered
            finished -> R.string.order_status_text_picked
            stage == 0 -> R.string.order_status_text_received
            stage == 1 -> R.string.order_status_text_making
            isDelivery -> R.string.order_status_text_delivering
            else -> R.string.order_status_text_picked
        }
    )

    // ───────── ② 주문 내역 ─────────

    private fun setupPeriodChips() {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        fun colors(on: Int, off: Int) = ColorStateList(
            states, intArrayOf(ContextCompat.getColor(requireContext(), on), ContextCompat.getColor(requireContext(), off))
        )
        Period.entries.forEach { p ->
            val chip = Chip(requireContext()).apply {
                setText(p.label)
                isCheckable = true
                isCheckedIconVisible = false
                isChecked = p == period
                chipBackgroundColor = colors(R.color.bb_espresso, R.color.white)
                setTextColor(colors(R.color.bb_cream, R.color.bb_brown))
                chipStrokeColor = colors(R.color.bb_espresso, R.color.bb_divider)
                chipStrokeWidth = resources.displayMetrics.density
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        period = p
                        renderHistory(System.currentTimeMillis())
                    }
                }
            }
            binding.chipsPeriod.addView(chip)
        }
    }

    private fun renderHistory(now: Long) {
        val b = _binding ?: return
        val todayStart = startOfDay(now)
        val from = if (period == Period.ALL) 0L else todayStart - (period.days - 1) * DAY_MS
        // 위 배달 현황 카드에 없는 주문만 (진행 중 · 막 끝난 주문은 위에)
        val list = records().filter { now >= it.finishAt + RECENT_DONE_MS && it.placedAt >= from }

        b.textPeriodSummary.text =
            if (list.isEmpty()) "" else getString(R.string.order_period_summary, list.size, list.sumOf { it.total }.toWon())

        b.historyList.removeAllViews()
        var lastDay = -1L
        list.forEach { record ->
            val day = startOfDay(record.placedAt)
            if (day != lastDay) {
                lastDay = day
                val header = ItemOrderDateBinding.inflate(layoutInflater, b.historyList, false)
                header.textDate.text = when (day) {
                    todayStart -> getString(R.string.notice_today)
                    todayStart - DAY_MS -> getString(R.string.notice_yesterday)
                    else -> dateFormat.format(Date(day))
                }
                b.historyList.addView(header.root)
            }
            b.historyList.addView(orderCard(record))
        }

        // 비어 있을 때
        val nothingAtAll = records().isEmpty()
        b.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        b.textEmptyTitle.text = when {
            nothingAtAll -> getString(R.string.order_empty_title)
            else -> getString(R.string.order_empty_period, getString(period.label))
        }
        b.textEmptyDesc.setText(if (AuthManager.isLoggedIn()) R.string.order_empty_desc else R.string.order_empty_login)
        b.buttonGoMenu.visibility = if (nothingAtAll) View.VISIBLE else View.GONE
    }

    /** 주문 카드 1장 (누르면 주문 상세 화면 OrderDetailActivity) */
    private fun orderCard(record: OrderRecord): View {
        val ctx = requireContext()
        val c = ItemOrderCardBinding.inflate(layoutInflater, binding.historyList, false)
        val mode = getString(if (record.delivery) R.string.order_mode_delivery else R.string.order_mode_pickup)
        c.textTime.text = getString(R.string.order_card_time, timeFormat.format(Date(record.placedAt)), mode)
        c.textStatus.setText(if (record.delivery) R.string.order_status_delivered else R.string.order_status_picked)
        c.textStatus.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.bb_open_bg))
        c.textStatus.setTextColor(ContextCompat.getColor(ctx, R.color.bb_open))
        c.textSummary.text = record.summary
        c.textTotal.text = record.total.toWon()

        // 리뷰를 기다리는 메뉴 수
        val waiting = record.items.map { it.menuId }.distinct().count {
            !record.isReviewed(it) && ReviewManager.reviewFor(it, record.orderNo) == null && MenuRepository.findItem(it) != null
        }
        c.textReviewBadge.visibility = if (waiting > 0) View.VISIBLE else View.GONE
        c.textReviewBadge.text = getString(R.string.order_review_waiting, waiting)

        // > 누르면 주문 상세 화면
        c.cardOrder.setOnClickListener { OrderDetailActivity.start(requireContext(), record.orderNo) }
        return c.root
    }

    /** 메뉴 한 줄 + [리뷰 쓰기] (OrderLineView.kt) — 이 화면에서 바로 리뷰 쓰기 창 */
    private fun lineView(record: OrderRecord, line: OrderLine, parent: ViewGroup): View {
        val v = ItemOrderLineBinding.inflate(layoutInflater, parent, false)
        val saved = OrderHistory.all().any { it.orderNo == record.orderNo }
        v.bindOrderLine(if (saved) record else null, line) { _ ->
            val menu = MenuRepository.findItem(line.menuId) ?: return@bindOrderLine
            // 이 주문의 리뷰: 안 썼으면 쓰기, 썼으면 고치기
            ReviewEditor.open(requireContext(), binding.root, menu, record.orderNo)
        }
        return v.root
    }

    /** 예) "주문 15,000원 · 배달비 1,000원 · 쿠폰 −2,000원" */
    private fun payInfo(r: OrderRecord) = buildList {
        add(getString(R.string.pay_detail_subtotal, r.subtotal.toWon()))
        if (r.deliveryFee > 0) add(getString(R.string.pay_detail_fee, r.deliveryFee.toWon()))
        if (r.discount > 0) add(getString(R.string.pay_detail_coupon, r.discount.toWon()))
    }.joinToString(" · ")

    private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        /** 끝난 주문도 이 시간 동안은 배달 현황 카드로 보여 줌 (바로 리뷰 쓰기 좋게) */
        private const val RECENT_DONE_MS = 10 * 60 * 1000L
    }
}