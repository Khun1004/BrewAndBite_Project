package com.example.brewandbite.reward

import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.cart.CartItem
import com.example.brewandbite.data.MenuRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.TimeUnit

/**
 * 스탬프 · 쿠폰 (Firebase Realtime Database 의 rewards/{회원ID}).
 *
 *   rewards/{회원ID}/stamps        = 지금 모은 스탬프 (0~9)
 *   rewards/{회원ID}/welcomeGiven  = 가입 축하 쿠폰을 받았는지
 *   rewards/{회원ID}/coupons/{ID}  = Coupon
 *
 *  - 음료(커피·차·음료) 1잔 = 스탬프 1개. 10개를 모으면 '음료 1잔 무료' 쿠폰이 생기고 스탬프는 0부터 다시.
 *  - 처음 로그인하면 '가입 축하 2,000원 할인' 쿠폰을 1번 준다.
 *  - 로그인하면 자동으로 불러오고(BrewBiteApp), 서버 값이 바뀌면 바로 화면에 반영된다.
 *
 * ───────── 팀원용 사용법 (Order 화면) ─────────
 *  결제 화면에서 쓸 수 있는 쿠폰   RewardManager.usableCoupons(orderTotal)
 *  쿠폰 할인 금액                RewardManager.discount(coupon, orderTotal)
 *  주문 완료 후 쿠폰 사용 처리      RewardManager.useCoupon(coupon.id)
 *  주문 완료 후 스탬프 적립         RewardManager.addStampsFor(cartItems) { newCoupons -> }
 * ───────────────────────────────────────────
 */
object RewardManager {

    fun interface RewardListener {
        fun onRewardsChanged()
    }

    const val STAMPS_PER_COUPON = 10
    const val FREE_DRINK_MAX = 5_000          // 음료 무료 쿠폰: 최대 5,000원까지
    const val WELCOME_AMOUNT = 2_000
    const val WELCOME_MIN_ORDER = 10_000
    private val WELCOME_DAYS = TimeUnit.DAYS.toMillis(30)
    private val STAMP_COUPON_DAYS = TimeUnit.DAYS.toMillis(60)

    /** 스탬프가 쌓이는 카테고리 (음료) */
    private val STAMP_CATEGORIES = setOf("coffee", "tea", "drinks")

    private var stamps = 0
    private var coupons: List<Coupon> = emptyList()
    private val listeners = mutableSetOf<RewardListener>()

    private var loadedUid: String? = null
    private var ref: DatabaseReference? = null
    private var valueListener: ValueEventListener? = null

    // ───────── 읽기 ─────────

    fun stamps(): Int = stamps

    /** 스탬프 몇 개 더 모으면 무료 음료인지 */
    fun stampsToGo(): Int = STAMPS_PER_COUPON - stamps

    /** 쓸 수 있는 쿠폰 (곧 끝나는 순) */
    fun activeCoupons(): List<Coupon> = coupons.filter { it.usable }.sortedBy { it.expiresAt }

    /** 이미 썼거나 기간이 지난 쿠폰 (최근 순) */
    fun pastCoupons(): List<Coupon> = coupons.filterNot { it.usable }.sortedByDescending { maxOf(it.usedAt, it.expiresAt) }

    /** 이 주문 금액에 쓸 수 있는 쿠폰 */
    fun usableCoupons(orderTotal: Int): List<Coupon> = activeCoupons().filter { orderTotal >= it.minOrder }

    /** 이 쿠폰으로 얼마나 할인되는지 (조건이 안 맞으면 0) */
    fun discount(coupon: Coupon, orderTotal: Int): Int =
        if (!coupon.usable || orderTotal < coupon.minOrder) 0 else minOf(coupon.amount, orderTotal)

    /** 장바구니에서 음료가 몇 잔인지 = 받을 스탬프 수 */
    fun stampsFor(items: List<CartItem>): Int = items.sumOf { line ->
        val category = MenuRepository.findItem(line.menuId)?.categoryId
        if (category in STAMP_CATEGORIES) line.quantity else 0
    }

    // ───────── 쓰기 ─────────

    /** 주문한 음료 수만큼 스탬프 적립. onDone(새로 생긴 쿠폰 수, 성공 여부) */
    fun addStampsFor(items: List<CartItem>, onDone: (newCoupons: Int, success: Boolean) -> Unit = { _, _ -> }) =
        addStamps(stampsFor(items), onDone)

    /** 스탬프 count 개 적립. 10개가 넘으면 그만큼 무료 음료 쿠폰을 만들고 남은 스탬프만 남긴다. */
    fun addStamps(count: Int, onDone: (newCoupons: Int, success: Boolean) -> Unit = { _, _ -> }) {
        val r = ref
        if (r == null || count <= 0) return onDone(0, r != null)
        val total = stamps + count
        val rewards = total / STAMPS_PER_COUPON
        val updates = HashMap<String, Any>()
        updates["stamps"] = total % STAMPS_PER_COUPON
        repeat(rewards) {
            val coupon = newCoupon(
                r, title = "음료 1잔 무료", amount = FREE_DRINK_MAX, minOrder = 0,
                source = Coupon.SOURCE_STAMP, validFor = STAMP_COUPON_DAYS
            )
            updates["coupons/${coupon.id}"] = coupon
        }
        r.updateChildren(updates)
            .addOnSuccessListener { onDone(rewards, true) }
            .addOnFailureListener { onDone(0, false) }
    }

    /** 쿠폰 사용 처리 (주문이 끝난 뒤 호출) */
    fun useCoupon(couponId: String, onDone: (success: Boolean) -> Unit = {}) {
        val r = ref ?: return onDone(false)
        r.child("coupons").child(couponId).updateChildren(
            mapOf("used" to true, "usedAt" to System.currentTimeMillis())
        ).addOnSuccessListener { onDone(true) }.addOnFailureListener { onDone(false) }
    }

    // ───────── 로그인 연결 ─────────

    /** 로그인 / 로그아웃 될 때 호출 (BrewBiteApp) */
    fun onAuthChanged() {
        val uid = AuthManager.currentUid()
        if (uid == loadedUid) return
        // 예전 계정 연결 끊기
        valueListener?.let { ref?.removeEventListener(it) }
        ref = null
        valueListener = null
        loadedUid = uid
        stamps = 0
        coupons = emptyList()
        notifyChanged()
        if (uid == null) return

        val r = FirebaseDatabase.getInstance().getReference("rewards").child(uid)
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (loadedUid != uid) return
                stamps = (snapshot.child("stamps").value as? Long)?.toInt() ?: 0
                coupons = snapshot.child("coupons").children.mapNotNull { child ->
                    child.getValue(Coupon::class.java)?.apply { if (id.isEmpty()) id = child.key.orEmpty() }
                }
                notifyChanged()
                // 처음 로그인한 회원에게 가입 축하 쿠폰
                if (snapshot.child("welcomeGiven").value != true) giveWelcome(r)
            }

            override fun onCancelled(error: DatabaseError) {
                // 규칙에 rewards 가 없으면 여기로 옴 → 빈 상태로 둠
                loadedUid = null
            }
        }
        ref = r
        valueListener = l
        r.addValueEventListener(l)
    }

    private fun giveWelcome(r: DatabaseReference) {
        val coupon = newCoupon(
            r, title = "가입 축하 할인", amount = WELCOME_AMOUNT, minOrder = WELCOME_MIN_ORDER,
            source = Coupon.SOURCE_WELCOME, validFor = WELCOME_DAYS
        )
        r.updateChildren(mapOf("welcomeGiven" to true, "coupons/${coupon.id}" to coupon))
    }

    private fun newCoupon(r: DatabaseReference, title: String, amount: Int, minOrder: Int, source: String, validFor: Long): Coupon {
        val now = System.currentTimeMillis()
        return Coupon(
            id = r.child("coupons").push().key.orEmpty(),
            title = title, amount = amount, minOrder = minOrder, source = source,
            issuedAt = now, expiresAt = now + validFor
        )
    }

    fun addListener(listener: RewardListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: RewardListener) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it.onRewardsChanged() }
    }
}