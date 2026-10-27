package com.example.brewandbite.review

import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.auth.UserRepository
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.order.OrderHistory
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Locale

/**
 * 메뉴 리뷰 · 별점 (Firebase Realtime Database 의 reviews/{메뉴ID}/{리뷰ID}).
 *
 *  - 리뷰는 '주문 1건의 메뉴마다 1개'. 같은 메뉴를 두 번 주문하면 리뷰도 두 번 쓸 수 있다.
 *    리뷰ID = "{회원ID}_{주문번호}" (예전 리뷰는 회원ID 그대로 — 그대로 보이고 고치고 지울 수 있음)
 *  - 앱이 시작되면 모든 리뷰를 불러와서(BrewBiteApp) 메뉴별 평균 별점을 계산한다.
 *  - 리뷰가 있는 메뉴는 목록 · 홈 · 상세 화면의 별점이 '실제 평균'으로 바뀐다 (없으면 원래 숫자).
 *  - 누구나 읽을 수 있고, 리뷰는 그 메뉴를 주문했고 배달·픽업이 끝난 사람만 쓸 수 있다 (OrderHistory).
 *  - 시연용으로 메뉴마다 '다른 손님' 리뷰 3~5개를 같이 보여 준다 (DemoReviews.kt · ENABLED = false 로 끄기).
 *
 * ───────── 사용법 ─────────
 *  ReviewManager.reviews(menuId)            리뷰 목록 (최신 순)
 *  ReviewManager.ratingText(item)           "4.6" (화면에 보여 줄 별점)
 *  ReviewManager.count(menuId)              리뷰 수
 *  ReviewManager.reviewFor(menuId, 주문번호)  그 주문으로 내가 쓴 리뷰 (없으면 null)
 *  ReviewManager.myReviewsOf(menuId)        이 메뉴에 내가 쓴 리뷰들
 *  ReviewManager.myReviews()                내가 쓴 리뷰 전부 (My Page → 리뷰 내역)
 *  ReviewManager.canWrite(menuId)           새 리뷰를 쓸 수 있는 주문이 있는지
 * ─────────────────────────
 */
object ReviewManager {

    fun interface ReviewListener {
        fun onReviewsChanged()
    }

    const val MIN_LENGTH = 10
    const val MAX_LENGTH = 200

    private val ref get() = FirebaseDatabase.getInstance().getReference("reviews")

    /** 메뉴ID → 리뷰 목록 (최신 순) */
    private var byMenu: Map<String, List<Review>> = emptyMap()
    private val listeners = mutableSetOf<ReviewListener>()
    private var started = false

    // ───────── 읽기 ─────────

    /** 리뷰 목록 (최신 순) = 손님들이 쓴 리뷰 + 시연용 다른 손님 리뷰 (DemoReviews · 끌 수 있음) */
    fun reviews(menuId: String): List<Review> =
        (byMenu[menuId].orEmpty() + DemoReviews.forMenu(menuId)).sortedByDescending { it.createdAt }

    fun count(menuId: String): Int = reviews(menuId).size

    /** 평균 별점 (리뷰가 없으면 null) */
    fun average(menuId: String): Double? = reviews(menuId).takeIf { it.isNotEmpty() }?.map { it.rating }?.average()

    /** 화면에 보여 줄 별점: 리뷰 평균, 리뷰가 없으면 메뉴에 적힌 원래 별점 */
    fun ratingText(item: MenuItem): String =
        average(item.id)?.let { String.format(Locale.US, "%.1f", it) } ?: item.rating.toString()

    /** 별점별 개수 [1점, 2점, 3점, 4점, 5점] */
    fun distribution(menuId: String): IntArray {
        val result = IntArray(5)
        reviews(menuId).forEach { result[(it.rating - 1).coerceIn(0, 4)]++ }
        return result
    }

    /** 내가 쓴 리뷰 전부 (메뉴ID, 리뷰) — 최근에 쓰거나 고친 순 (My Page → 리뷰 내역) */
    fun myReviews(): List<Pair<String, Review>> {
        val uid = AuthManager.currentUid() ?: return emptyList()
        return byMenu.flatMap { (menuId, list) -> list.filter { it.uid == uid }.map { menuId to it } }
            .sortedByDescending { maxOf(it.second.createdAt, it.second.updatedAt) }
    }

    /** 이 메뉴에 내가 쓴 리뷰들 (최신 순) */
    fun myReviewsOf(menuId: String): List<Review> {
        val uid = AuthManager.currentUid() ?: return emptyList()
        return reviews(menuId).filter { it.uid == uid }
    }

    /** 그 주문으로 내가 쓴 리뷰 (없으면 null) */
    fun reviewFor(menuId: String, orderNo: String): Review? =
        if (orderNo.isEmpty()) null else myReviewsOf(menuId).find { it.orderNo == orderNo }

    /** 리뷰ID 로 찾기 */
    fun find(menuId: String, reviewId: String): Review? = reviews(menuId).find { it.id == reviewId }

    /** 새 리뷰를 쓸 수 있는 주문이 있는지 (주문했고 · 끝났고 · 아직 리뷰 안 쓴 주문) */
    fun canWrite(menuId: String): Boolean = OrderHistory.reviewableOrder(menuId) != null

    // ───────── 쓰기 ─────────

    /**
     * 리뷰 저장. 이름은 회원 정보에서 가져온다.
     * @param reviewId 고치는 리뷰의 ID (새로 쓰면 null)
     * @param orderNo  새로 쓸 때 어느 주문의 리뷰인지 (null 이면 리뷰 가능한 가장 최근 주문)
     * @param photos   📷 사진 (Base64, 최대 3장). 리뷰 글과 사진을 한 번에 저장한다
     */
    fun save(
        menuId: String, rating: Int, text: String,
        reviewId: String? = null,
        orderNo: String? = null,
        photos: List<String> = emptyList(),
        onDone: (success: Boolean) -> Unit
    ) {
        val uid = AuthManager.currentUid() ?: return onDone(false)
        val old = reviewId?.let { find(menuId, it) }?.takeIf { it.uid == uid }
        // 어느 주문으로 쓰는지: 수정이면 원래 주문, 새로 쓰면 지정한 주문(끝난 주문) 또는 리뷰 가능한 가장 최근 주문
        val order = if (old != null) OrderHistory.all().find { it.orderNo == old.orderNo }
        else orderNo?.let { no -> OrderHistory.all().find { it.orderNo == no && it.finished } }
            ?: OrderHistory.reviewableOrder(menuId)
        if (old == null && order == null) return onDone(false)   // 주문 안 한 메뉴
        // 이미 그 주문으로 쓴 리뷰가 있으면 새로 만들지 않고 그 리뷰를 고친다
        val target = old ?: order?.let { reviewFor(menuId, it.orderNo) }
        val no = target?.orderNo?.takeIf { it.isNotEmpty() } ?: order?.orderNo.orEmpty()
        val id = target?.id?.takeIf { it.isNotEmpty() } ?: "${uid}_$no"

        val options = order?.items?.firstOrNull { it.menuId == menuId }?.options.orEmpty()
            .substringBefore("요청:").trim().removeSuffix("·").trim()   // 요청사항은 빼고 옵션만
        val pics = photos.take(ReviewPhotos.MAX_PHOTOS)
        UserRepository.loadMyProfile { profile ->
            val name = profile?.name?.takeIf { it.isNotBlank() }
                ?: AuthManager.currentEmail()?.substringBefore("@").orEmpty().ifBlank { "고객" }
            val now = System.currentTimeMillis()
            val review = Review(
                id = id, uid = uid, name = name, rating = rating.coerceIn(1, 5), text = text.trim(),
                orderNo = no, options = options.ifEmpty { target?.options.orEmpty() },
                photoCount = pics.size,
                createdAt = target?.createdAt ?: now, updatedAt = now
            )
            // 리뷰 글 + 사진을 한 번에 (사진이 없으면 사진 칸은 지움)
            val updates = hashMapOf<String, Any?>(
                "reviews/$menuId/$id" to review,
                ReviewPhotos.path(menuId, id) to pics.ifEmpty { null }
            )
            FirebaseDatabase.getInstance().reference.updateChildren(updates)
                .addOnSuccessListener {
                    OrderHistory.setReviewed(no, menuId, true)   // 주문 기록에 '작성 완료'
                    onDone(true)
                }
                .addOnFailureListener { onDone(false) }
        }
    }

    /** 리뷰 지우기 (사진도 같이). 그 주문에서 다시 [리뷰 쓰기]를 할 수 있게 된다 */
    fun delete(menuId: String, reviewId: String, onDone: (success: Boolean) -> Unit) {
        val uid = AuthManager.currentUid() ?: return onDone(false)
        val review = find(menuId, reviewId)?.takeIf { it.uid == uid } ?: return onDone(false)
        val updates = hashMapOf<String, Any?>(
            "reviews/$menuId/$reviewId" to null,
            ReviewPhotos.path(menuId, reviewId) to null
        )
        FirebaseDatabase.getInstance().reference.updateChildren(updates)
            .addOnSuccessListener {
                OrderHistory.setReviewed(review.orderNo, menuId, false)
                onDone(true)
            }
            .addOnFailureListener { onDone(false) }
    }

    // ───────── 불러오기 ─────────

    /** 앱 시작 시 한 번 (BrewBiteApp). 이후 리뷰가 바뀌면 자동으로 다시 계산 */
    fun start() {
        if (started) return
        started = true
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                byMenu = snapshot.children.associate { menu ->
                    menu.key.orEmpty() to menu.children.mapNotNull { child ->
                        child.getValue(Review::class.java)?.apply {
                            id = child.key.orEmpty()                     // 리뷰ID = Firebase 키
                            if (uid.isEmpty()) uid = child.key.orEmpty()  // 예전 리뷰 (키 = 회원ID)
                        }
                    }.sortedByDescending { it.createdAt }
                }
                notifyChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                // 규칙에 reviews 읽기가 없으면 여기로 옴 → 리뷰 없이 원래 별점만 보여 줌
                started = false
            }
        })
    }

    fun addListener(listener: ReviewListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ReviewListener) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it.onReviewsChanged() }
    }
}