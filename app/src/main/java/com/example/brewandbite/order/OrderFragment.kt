package com.example.brewandbite.order

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.brewandbite.MainActivity
import com.example.brewandbite.R
import com.example.brewandbite.common.setup
import com.example.brewandbite.data.toWon
import com.example.brewandbite.databinding.FragmentOrderBinding
import com.example.brewandbite.databinding.ItemOrderStepBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/** Order 탭: 가장 최근 주문의 진행 상황(접수 → 준비 → 배달/픽업)과 예상 시간 */
class OrderFragment : Fragment() {

    private var _binding: FragmentOrderBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000)
        }
    }
    private val timeFormat = SimpleDateFormat("a h:mm", Locale.KOREA)

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

        // 완료된 주문 정리하고 메뉴 보러 가기
        binding.buttonNewOrder.setOnClickListener {
            OrderManager.clear()
            (requireActivity() as MainActivity).showTab(MainActivity.TAB_HOME)
            render()
        }
    }

    // 탭은 hide/show로 전환되므로 보일 때만 1초마다 갱신
    override fun onStart() {
        super.onStart()
        if (!isHidden) startTicker()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(ticker)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) handler.removeCallbacks(ticker) else startTicker()
    }

    private fun startTicker() {
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(ticker)
        super.onDestroyView()
        _binding = null
    }

    // ───────── 화면 그리기 ─────────

    private fun render() {
        val b = _binding ?: return
        val order = OrderManager.current()

        b.emptyView.visibility = if (order == null) View.VISIBLE else View.GONE
        b.contentLayout.visibility = if (order == null) View.GONE else View.VISIBLE
        if (order == null) return

        val now = System.currentTimeMillis()
        val stages = OrderManager.stages(order)
        val last = stages.lastIndex
        val cur = OrderManager.currentStage(order, now)
        val finished = cur == last

        // 상태 제목
        b.textOrderNo.text = "주문번호 ${order.orderNo}"
        b.textStatus.text = statusText(order.isDelivery, cur)

        // 예상 시간
        val endTime = order.placedAt + stages[last].startMs
        val endLabel = timeFormat.format(Date(endTime))
        val arriveWord = if (order.isDelivery) "도착" else "픽업 가능"
        b.textEta.text = if (finished) {
            "$arriveWord 완료 · $endLabel"
        } else {
            val minLeft = ceil((endTime - now) / 60_000.0).toInt().coerceAtLeast(1)
            "예상 $arriveWord $endLabel · 약 ${minLeft}분 남음"
        }

        // 진행 바
        val total = stages[last].startMs.toDouble()
        val ratio = ((now - order.placedAt) / total).coerceIn(0.0, 1.0)
        b.progressOrder.progress = (ratio * 1000).toInt()

        // 타임라인
        val rows = listOf(b.step0, b.step1, b.step2, b.step3)
        rows.forEachIndexed { i, row ->
            if (i > last) {
                row.root.visibility = View.GONE
            } else {
                row.root.visibility = View.VISIBLE
                bindStep(row, stages[i], i, cur, last, order.placedAt)
            }
        }

        // 주문 내역
        b.textItems.text = order.items.joinToString("\n") {
            val opt = if (it.options.isBlank()) "" else " (${it.options})"
            "• ${it.name}$opt × ${it.quantity}"
        }
        val modeText = if (order.isDelivery) "배달" else "포장"
        b.textPayInfo.text = buildString {
            append("수령 방법  $modeText\n")
            append("주문 금액  ${order.subtotal.toWon()}")
            if (order.isDelivery) append("\n배달비  ${order.deliveryFee.toWon()}")
            // 쿠폰 · 스탬프 (결제 화면에서 저장)
            if (order.discount > 0) append("\n쿠폰 할인  −${order.discount.toWon()} (${order.couponTitle})")
            if (order.stampsEarned > 0) append("\n적립 스탬프  ☕ ${order.stampsEarned}개")
        }
        b.textPayTotal.text = "총 결제 ${order.total.toWon()}"

        b.buttonNewOrder.visibility = if (finished) View.VISIBLE else View.GONE
    }

    private fun bindStep(
        row: ItemOrderStepBinding, stage: OrderManager.Stage,
        index: Int, cur: Int, last: Int, placedAt: Long
    ) {
        val done = index < cur || (index == cur && index == last)
        val active = index == cur && !done
        val timeLabel = timeFormat.format(Date(placedAt + stage.startMs))

        row.textMarker.text = when {
            done -> "✅"
            active -> "🟤"
            else -> "⚪"
        }
        row.textStepTitle.text = stage.title
        row.textStepTitle.setTypeface(null, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        row.textStepTime.text = if (done || active) timeLabel else "예상 $timeLabel"
        row.root.alpha = if (done || active) 1f else 0.5f
    }

    private fun statusText(isDelivery: Boolean, stage: Int): String =
        if (isDelivery) when (stage) {
            0 -> "주문이 접수되었어요"
            1 -> "메뉴를 만들고 있어요 ☕"
            2 -> "배달 중이에요 🛵"
            else -> "배달이 완료되었어요 🎉"
        } else when (stage) {
            0 -> "주문이 접수되었어요"
            1 -> "메뉴를 만들고 있어요 ☕"
            else -> "픽업 준비가 끝났어요 🛍️"
        }
}