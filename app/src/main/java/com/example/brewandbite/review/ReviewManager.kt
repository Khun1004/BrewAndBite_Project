package com.example.brewandbite.review

import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.auth.UserRepository
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.order.OrderManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Locale

/**
 * 메뉴 리뷰 · 별점 (Firebase Realtime Database 의 reviews/{메뉴ID}/{회원ID}).
 *
 *  - 앱이 시작되면 모든 리뷰를 불러와서(BrewBiteApp) 메뉴별 평균 별점을 계산한다.
 *  - 리뷰가 있는 메뉴는 목록 · 홈 · 상세 화면의 별점이 '실제 평균'으로 바뀐다 (없으면 원래 숫자).
 *  - 누구나 읽을 수 있고, 로그인한 사람은 메뉴마다 리뷰 1개를 쓰고 · 고치고 · 지울 수 있다.
 *
 * ───────── 사용법 ─────────
 *  ReviewManager.reviews(menuId)      리뷰 목록 (최신 순)
 *  ReviewManager.ratingText(item)     "4.6" (화면에 보여 줄 별점)
 *  ReviewManager.count(menuId)        리뷰 수
 *  ReviewManager.myReview(menuId)     내가 쓴 리뷰 (없으면 null)
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

    fun reviews(menuId: String): List<Review> = byMenu[menuId].orEmpty()

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

    fun myReview(menuId: String): Review? {
        val uid = AuthManager.currentUid() ?: return null
        return reviews(menuId).find { it.uid == uid }
    }

    // ───────── 쓰기 ─────────

    /** 리뷰 저장 (처음이면 등록, 이미 있으면 수정). 이름은 회원 정보에서 가져온다 */
    fun save(menuId: String, rating: Int, text: String, onDone: (success: Boolean) -> Unit) {
        val uid = AuthManager.currentUid() ?: return onDone(false)
        val old = myReview(menuId)
        UserRepository.loadMyProfile { profile ->
            val name = profile?.name?.takeIf { it.isNotBlank() }
                ?: AuthManager.currentEmail()?.substringBefore("@").orEmpty().ifBlank { "고객" }
            val now = System.currentTimeMillis()
            val ordered = old?.ordered == true ||
                    OrderManager.current()?.items?.any { it.menuId == menuId } == true
            val review = Review(
                uid = uid, name = name, rating = rating.coerceIn(1, 5), text = text.trim(),
                ordered = ordered, createdAt = old?.createdAt ?: now, updatedAt = now
            )
            ref.child(menuId).child(uid).setValue(review)
                .addOnSuccessListener { onDone(true) }
                .addOnFailureListener { onDone(false) }
        }
    }

    fun delete(menuId: String, onDone: (success: Boolean) -> Unit) {
        val uid = AuthManager.currentUid() ?: return onDone(false)
        ref.child(menuId).child(uid).removeValue()
            .addOnSuccessListener { onDone(true) }
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
                        child.getValue(Review::class.java)?.apply { if (uid.isEmpty()) uid = child.key.orEmpty() }
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