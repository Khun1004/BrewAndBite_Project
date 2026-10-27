package com.example.brewandbite.order

import android.util.Log
import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.review.ReviewManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.Exclude
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 주문한 메뉴 한 줄 (주문 기록용) */
data class OrderLine(
    var menuId: String = "",
    var name: String = "",
    var emoji: String = "",
    var price: Int = 0,
    var quantity: Int = 1,
    var options: String = ""
)

/**
 * 지난 주문 1건 (Firebase: orders/{회원ID}/{주문번호}).
 *  reviewed : 메뉴ID → 리뷰를 썼는지 (메뉴마다 1번)
 */
data class OrderRecord(
    var orderNo: String = "",
    var placedAt: Long = 0L,
    var finishAt: Long = 0L,           // 배달 · 픽업이 끝나는 시각 (이후 리뷰 가능)
    var delivery: Boolean = true,
    var items: List<OrderLine> = emptyList(),
    var subtotal: Int = 0,
    var deliveryFee: Int = 0,
    var discount: Int = 0,
    var total: Int = 0,
    var reviewed: Map<String, Boolean> = emptyMap()
) {
    /** 배달 · 픽업이 끝났는지 (끝나야 리뷰를 쓸 수 있음) */
    @get:Exclude
    val finished: Boolean
        get() = System.currentTimeMillis() >= finishAt

    fun isReviewed(menuId: String) = reviewed[menuId] == true

    /** 진행 단계 (주문 접수 → 준비 → 배달 중 → 배달 완료 / 픽업 준비 완료) */
    @Exclude
    fun stages(): List<OrderManager.Stage> = OrderManager.stages(delivery)

    /** 지금 몇 번째 단계인지 (0부터) */
    @Exclude
    fun stageIndex(now: Long = System.currentTimeMillis()): Int =
        stages().indexOfLast { now - placedAt >= it.startMs }.coerceAtLeast(0)

    /** 첫 메뉴 + 나머지 개수. 예) "☕ Americano 외 2개" */
    @get:Exclude
    val summary: String
        get() {
            val first = items.firstOrNull() ?: return ""
            return if (items.size > 1) "${first.emoji} ${first.name} 외 ${items.size - 1}개" else "${first.emoji} ${first.name}"
        }

    /** 예) "10.26 (월) 오후 3:12" */
    @get:Exclude
    val dateText: String
        get() = SimpleDateFormat("M.d (E) a h:mm", Locale.KOREA).format(Date(placedAt))
}

/** 휴대폰에 저장된 주문(OrderManager) → 주문 기록 모양으로 */
fun OrderManager.OrderInfo.toRecord() = OrderRecord(
    orderNo = orderNo, placedAt = placedAt,
    finishAt = placedAt + OrderManager.stages(isDelivery).last().startMs,
    delivery = isDelivery,
    items = items.map { OrderLine(it.menuId, it.name, it.emoji, it.price, it.quantity, it.options) },
    subtotal = subtotal, deliveryFee = deliveryFee, discount = discount, total = total
)

/**
 * 주문 기록 (Firebase Realtime Database 의 orders/{회원ID}).
 *
 *  - 결제가 끝나면 OrderManager.place() 가 자동으로 여기에 저장한다.
 *  - My Page → 🧾 주문 기록 화면, 리뷰 쓰기 자격 확인에 쓴다.
 *  - 리뷰는 '주문했고 + 배달·픽업이 끝난' 메뉴만, 주문 1건의 메뉴마다 1번 쓸 수 있다.
 *    (같은 메뉴를 두 번 주문하면 리뷰도 두 번)
 */
object OrderHistory {

    private const val TAG = "OrderHistory"

    fun interface HistoryListener {
        fun onHistoryChanged()
    }

    private var orders: List<OrderRecord> = emptyList()
    private val listeners = mutableSetOf<HistoryListener>()

    private var loadedUid: String? = null
    private var ref: DatabaseReference? = null
    private var valueListener: ValueEventListener? = null

    // ───────── 읽기 ─────────

    /** 읽기 실패한 회원ID (같은 회원으로 계속 다시 시도하지 않게) */
    private var failedUid: String? = null

    /**
     * 아직 이 회원의 주문 기록에 연결 안 됐으면 지금 연결.
     * (BrewBiteApp 에서 onAuthChanged 를 안 불러도 주문 기록이 동작하게 하는 안전장치)
     */
    private fun ensureConnected() {
        val uid = AuthManager.currentUid() ?: return
        if (uid != loadedUid && uid != failedUid) onAuthChanged()
    }

    /** 지난 주문 (최신 순) */
    fun all(): List<OrderRecord> {
        ensureConnected()
        return orders
    }

    /** 주문번호로 찾기 (Firebase 에 없으면 휴대폰에 저장된 마지막 주문에서) */
    fun find(orderNo: String): OrderRecord? =
        all().find { it.orderNo == orderNo }
            ?: OrderManager.current()?.takeIf { it.orderNo == orderNo }?.toRecord()

    /** 이 메뉴를 주문한 적이 있는지 */
    fun hasOrdered(menuId: String): Boolean = orders.any { o -> o.items.any { it.menuId == menuId } }

    /**
     * 이 메뉴로 리뷰를 쓸 수 있는 주문 (배달·픽업이 끝났고 아직 리뷰 안 쓴 가장 최근 주문).
     * 없으면 null
     */
    fun reviewableOrder(menuId: String): OrderRecord? =
        all().firstOrNull { o ->
            o.finished && !o.isReviewed(menuId) && o.items.any { it.menuId == menuId } &&
                    ReviewManager.reviewFor(menuId, o.orderNo) == null
        }

    /** 주문은 했지만 아직 배달·픽업 중인지 (리뷰는 끝난 뒤에) */
    fun isWaiting(menuId: String): Boolean =
        all().any { o -> !o.finished && o.items.any { it.menuId == menuId } }

    // ───────── 쓰기 ─────────

    /** 결제 완료 시 (OrderManager.place 에서 자동 호출). 로그인 전 · 아직 연결 전이면 false */
    fun save(order: OrderManager.OrderInfo): Boolean {
        ensureConnected()
        val r = ref ?: run {
            Log.w(TAG, "주문 기록 저장 못 함 (${order.orderNo}): 로그인 안 됨")
            return false
        }
        val record = OrderRecord(
            orderNo = order.orderNo,
            placedAt = order.placedAt,
            finishAt = order.placedAt + OrderManager.stages(order).last().startMs,
            delivery = order.isDelivery,
            items = order.items.map { OrderLine(it.menuId, it.name, it.emoji, it.price, it.quantity, it.options) },
            subtotal = order.subtotal,
            deliveryFee = order.deliveryFee,
            discount = order.discount,
            total = order.total
        )
        r.child(order.orderNo).setValue(record)
            .addOnSuccessListener { Log.d(TAG, "주문 기록 저장: ${order.orderNo}") }
            .addOnFailureListener { Log.w(TAG, "주문 기록 저장 실패 (${order.orderNo}): ${it.message}") }
        return true
    }

    /** 리뷰를 쓰거나 지웠을 때 그 주문의 메뉴에 표시 */
    fun setReviewed(orderNo: String, menuId: String, reviewed: Boolean) {
        if (orderNo.isEmpty()) return
        ensureConnected()
        val node = ref?.child(orderNo)?.child("reviewed")?.child(menuId) ?: return
        if (reviewed) node.setValue(true) else node.removeValue()
    }

    // ───────── 로그인 연결 ─────────

    /** 로그인 / 로그아웃 될 때 호출 (BrewBiteApp) */
    fun onAuthChanged() {
        val uid = AuthManager.currentUid()
        if (uid == loadedUid) return
        valueListener?.let { ref?.removeEventListener(it) }
        ref = null
        valueListener = null
        loadedUid = uid
        orders = emptyList()
        if (uid == null) {
            notifyChanged()
            return
        }
        Log.d(TAG, "주문 기록 연결: orders/$uid")

        val r = FirebaseDatabase.getInstance().getReference("orders").child(uid)
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (loadedUid != uid) return
                orders = snapshot.children
                    .mapNotNull { child ->
                        try {
                            child.getValue(OrderRecord::class.java)
                        } catch (e: Exception) {
                            Log.w(TAG, "주문 기록 읽기 오류 (${child.key}): ${e.message}")
                            null
                        }
                    }
                    .sortedByDescending { it.placedAt }
                Log.d(TAG, "주문 기록 ${orders.size}건 불러옴")
                notifyChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "주문 기록 읽기 실패: ${error.message}")
                failedUid = uid
                loadedUid = null   // 규칙에 orders 가 없으면 여기로 옴
            }
        }
        ref = r
        valueListener = l
        r.addValueEventListener(l)
        notifyChanged()
    }

    fun addListener(listener: HistoryListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: HistoryListener) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it.onHistoryChanged() }
    }
}