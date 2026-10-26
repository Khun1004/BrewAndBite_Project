package com.example.brewandbite.order

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.brewandbite.MainActivity
import com.example.brewandbite.R
import com.example.brewandbite.cart.CartManager
import com.example.brewandbite.common.applyStatusBarPadding
import com.example.brewandbite.data.MenuRepository
import com.example.brewandbite.data.toWon
import com.example.brewandbite.databinding.ActivityOrderDetailBinding
import com.example.brewandbite.databinding.ItemOrderLineBinding
import com.example.brewandbite.menu.MenuDetailActivity
import com.example.brewandbite.review.ReviewEditor
import com.example.brewandbite.review.ReviewManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/**
 * 🧾 주문 상세 화면 — Order 탭 주문 내역 카드의 > 를 누르면 열린다.
 *  ① 상태: [배달 완료] · 배달/포장 · 상태 문구 · 남은 시간 · 진행 바 · 주문 일시 · 주문 번호
 *  ② 주문한 메뉴: 메뉴마다 [✏️ 리뷰 쓰기] (여기서 바로 리뷰 쓰기 창)
 *  ③ 결제 정보: 주문 금액 · 배달비 · 쿠폰 할인 · 총 결제
 *  [🛒 같은 메뉴 다시 담기]
 *
 * 여는 법: OrderDetailActivity.start(context, 주문번호)
 */
class OrderDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderDetailBinding
    private lateinit var orderNo: String

    private val timeFormat = SimpleDateFormat("a h:mm", Locale.KOREA)
    private val dateFormat = SimpleDateFormat("yyyy.M.d (E) a h:mm", Locale.KOREA)

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            render()
            // 진행 중이면 1초마다 시간 · 진행 바 갱신
            if (OrderHistory.find(orderNo)?.finished == false) handler.postDelayed(this, 1000)
        }
    }
    private val historyListener = OrderHistory.HistoryListener { render() }
    private val reviewListener = ReviewManager.ReviewListener { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        orderNo = intent.getStringExtra(EXTRA_ORDER_NO).orEmpty()
        enableEdgeToEdge()
        binding = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
        binding.headerBox.applyStatusBarPadding()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonReorder.setOnClickListener { reorder() }
    }

    override fun onStart() {
        super.onStart()
        OrderHistory.addListener(historyListener)
        ReviewManager.addListener(reviewListener)
        handler.post(ticker)
    }

    override fun onStop() {
        super.onStop()
        OrderHistory.removeListener(historyListener)
        ReviewManager.removeListener(reviewListener)
        handler.removeCallbacks(ticker)
    }

    /** 마지막으로 그린 메뉴 줄 상태 (바뀔 때만 다시 만들기) */
    private var linesKey = ""

    private fun render() {
        val record = OrderHistory.find(orderNo)
        if (record == null) {
            // 아직 불러오는 중이거나 없는 주문
            binding.textStatus.setText(R.string.order_detail_loading)
            return
        }
        val now = System.currentTimeMillis()
        val finished = record.finished
        val cur = record.stageIndex(now)

        // ① 상태
        val doneColor = if (finished) R.color.bb_open else R.color.bb_brown
        binding.textStatusChip.setText(
            when {
                finished && record.delivery -> R.string.order_status_delivered
                finished -> R.string.order_status_picked
                record.delivery -> R.string.order_status_delivering
                else -> R.string.order_status_preparing
            }
        )
        binding.textStatusChip.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (finished) R.color.bb_open_bg else R.color.bb_beige_light)
        )
        binding.textStatusChip.setTextColor(ContextCompat.getColor(this, doneColor))
        binding.textMode.setText(if (record.delivery) R.string.order_mode_delivery_chip else R.string.order_mode_pickup_chip)
        binding.textStatus.text = getString(
            when {
                finished && record.delivery -> R.string.order_status_text_delivered
                finished -> R.string.order_status_text_picked
                cur == 0 -> R.string.order_status_text_received
                cur == 1 -> R.string.order_status_text_making
                record.delivery -> R.string.order_status_text_delivering
                else -> R.string.order_status_text_picked
            }
        )
        val endLabel = timeFormat.format(Date(record.finishAt))
        binding.textEta.text = if (finished) {
            getString(if (record.delivery) R.string.order_detail_done_delivery else R.string.order_detail_done_pickup, endLabel)
        } else {
            val minLeft = ceil((record.finishAt - now) / 60_000.0).toInt().coerceAtLeast(1)
            getString(if (record.delivery) R.string.order_eta_delivery else R.string.order_eta_pickup, minLeft, endLabel)
        }
        val ratio = ((now - record.placedAt).toDouble() / (record.finishAt - record.placedAt)).coerceIn(0.0, 1.0)
        binding.progressOrder.progress = (ratio * 1000).toInt()
        binding.textInfo.text = getString(R.string.order_detail_info, dateFormat.format(Date(record.placedAt)), record.orderNo)

        // ② 메뉴 (리뷰 상태가 바뀔 때만 다시 만들기)
        val key = listOf(
            finished, record.reviewed, OrderHistory.all().any { it.orderNo == orderNo },
            record.items.map { ReviewManager.myReview(it.menuId)?.updatedAt }
        ).toString()
        if (key != linesKey) {
            linesKey = key
            binding.textMenuTitle.text = getString(R.string.order_detail_menu_title, record.items.sumOf { it.quantity })
            binding.lineList.removeAllViews()
            val saved = OrderHistory.all().any { it.orderNo == orderNo }
            record.items.forEach { line ->
                val v = ItemOrderLineBinding.inflate(layoutInflater, binding.lineList, false)
                v.bindOrderLine(if (saved) record else null, line) { canWrite ->
                    val menu = MenuRepository.findItem(line.menuId) ?: return@bindOrderLine
                    if (canWrite) ReviewEditor.open(this, binding.root, menu, record.orderNo) { render() }
                    else MenuDetailActivity.start(this, menu.id)   // 내 리뷰 보기 · 수정
                }
                // 메뉴 이름을 누르면 메뉴 상세로
                v.textName.setOnClickListener { if (MenuRepository.findItem(line.menuId) != null) MenuDetailActivity.start(this, line.menuId) }
                binding.lineList.addView(v.root)
            }
            binding.textReviewHint.setText(
                when {
                    !saved -> R.string.order_review_hint_loading
                    !finished -> R.string.order_review_hint_wait
                    else -> R.string.order_review_hint_ready
                }
            )

            // ③ 결제 정보
            binding.payRows.removeAllViews()
            addPayRow(getString(R.string.cart_subtotal_label), record.subtotal.toWon())
            if (record.delivery) addPayRow(getString(R.string.cart_delivery_fee_label), record.deliveryFee.toWon())
            if (record.discount > 0) addPayRow(getString(R.string.order_detail_coupon), "−${record.discount.toWon()}", R.color.bb_closed)
            binding.textTotal.text = record.total.toWon()
        }
    }

    /** 결제 정보 한 줄: 이름 ··· 금액 */
    private fun addPayRow(label: String, value: String, valueColor: Int = R.color.bb_espresso) {
        val pad = (6 * resources.displayMetrics.density).toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, pad, 0, pad)
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@OrderDetailActivity, R.color.bb_muted))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@OrderDetailActivity, valueColor))
        })
        binding.payRows.addView(row)
    }

    /** 🛒 같은 메뉴 · 옵션 · 수량을 장바구니에 담고 Cart 탭으로 */
    private fun reorder() {
        val record = OrderHistory.find(orderNo) ?: return
        var added = 0
        record.items.forEach { line ->
            val menu = MenuRepository.findItem(line.menuId) ?: return@forEach
            CartManager.add(menu, line.quantity, line.options, line.price)
            added++
        }
        if (added == 0) {
            Toast.makeText(this, R.string.order_reorder_none, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, getString(R.string.order_reorder_done, added), Toast.LENGTH_SHORT).show()
        MainActivity.openTab(this, MainActivity.TAB_CART)
        finish()
    }

    companion object {
        private const val EXTRA_ORDER_NO = "order_no"

        fun start(context: Context, orderNo: String) {
            context.startActivity(Intent(context, OrderDetailActivity::class.java).putExtra(EXTRA_ORDER_NO, orderNo))
        }
    }
}