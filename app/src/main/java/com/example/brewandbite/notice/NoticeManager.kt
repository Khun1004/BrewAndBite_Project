package com.example.brewandbite.notice

import com.example.brewandbite.auth.AuthManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * 알림함 (Firebase Realtime Database 의 notices/{회원ID}).
 *
 *   notices/{회원ID}/items/{알림ID} = Notice
 *   notices/{회원ID}/seeded        = 처음 환영 알림을 넣었는지
 *
 *  - 로그인하면 자동으로 불러오고(BrewBiteApp), 새 알림이 생기면 바로 화면에 반영된다.
 *  - 쿠폰 발급 · 스탬프 적립 알림은 RewardManager 가 자동으로 넣는다.
 *  - 이름을 NoticeManager 로 한 이유: 안드로이드에 이미 NotificationManager 가 있어서 헷갈리지 않게.
 *
 * ───────── 팀원용 사용법 (결제 완료 등에서 알림 보내기) ─────────
 *  NoticeManager.add(Notice.TYPE_ORDER, "주문이 접수됐어요 🧾", "주문번호 BB-20261023-1234 · 13,500원")
 *  (눌렀을 때 Order 탭으로 이동)
 * ─────────────────────────────────────────────────────────
 */
object NoticeManager {

    fun interface NoticeListener {
        fun onNoticesChanged()
    }

    /** 오래된 알림은 이 개수까지만 보여 줌 (최신 순) */
    private const val MAX_SHOWN = 50

    private var notices: List<Notice> = emptyList()
    private val listeners = mutableSetOf<NoticeListener>()

    private var loadedUid: String? = null
    private var ref: DatabaseReference? = null
    private var valueListener: ValueEventListener? = null

    // ───────── 읽기 ─────────

    /** 알림 목록 (최신 순) */
    fun all(): List<Notice> = notices

    /** 안 읽은 알림 수 (🔔 빨간 점 표시용) */
    fun unreadCount(): Int = notices.count { !it.read }

    // ───────── 쓰기 ─────────

    /** 알림 추가. 로그인 안 했으면 아무것도 안 함 */
    fun add(type: String, title: String, body: String, link: String = "") {
        val r = ref ?: return
        val item = r.child("items").push()
        item.setValue(
            Notice(
                id = item.key.orEmpty(), type = type, title = title, body = body, link = link,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    fun markRead(id: String) {
        val r = ref ?: return
        if (notices.find { it.id == id }?.read == true) return
        r.child("items").child(id).child("read").setValue(true)
    }

    /** 모두 읽음 */
    fun markAllRead() {
        val r = ref ?: return
        val updates = notices.filter { !it.read }.associate { "items/${it.id}/read" to true }
        if (updates.isNotEmpty()) r.updateChildren(updates)
    }

    fun delete(id: String) {
        ref?.child("items")?.child(id)?.removeValue()
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
        notices = emptyList()
        notifyChanged()
        if (uid == null) return

        val r = FirebaseDatabase.getInstance().getReference("notices").child(uid)
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (loadedUid != uid) return
                notices = snapshot.child("items").children
                    .mapNotNull { child -> child.getValue(Notice::class.java)?.apply { if (id.isEmpty()) id = child.key.orEmpty() } }
                    .sortedByDescending { it.createdAt }
                    .take(MAX_SHOWN)
                notifyChanged()
                if (snapshot.child("seeded").value != true) seed(r)
            }

            override fun onCancelled(error: DatabaseError) {
                // 규칙에 notices 가 없으면 여기로 옴 → 빈 알림함
                loadedUid = null
            }
        }
        ref = r
        valueListener = l
        r.addValueEventListener(l)
    }

    /** 처음 로그인한 회원에게 환영 · 안내 알림 */
    private fun seed(r: DatabaseReference) {
        val now = System.currentTimeMillis()
        val items = r.child("items")
        val welcome = items.push().key.orEmpty()
        val menu = items.push().key.orEmpty()
        r.updateChildren(
            mapOf(
                "seeded" to true,
                "items/$welcome" to Notice(
                    welcome, Notice.TYPE_EVENT, "Brew & Bite에 오신 걸 환영해요 ☕",
                    "음료 1잔마다 스탬프 1개! 10개를 모으면 음료 1잔이 무료예요.", "", now - 2_000
                ),
                "items/$menu" to Notice(
                    menu, Notice.TYPE_MENU, "이번 주 추천 메뉴 🍝",
                    "크림 파스타에 새우를 추가해 보세요. 재료 빼기 · 추가도 할 수 있어요.", "f4", now - 1_000
                )
            )
        )
    }

    fun addListener(listener: NoticeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: NoticeListener) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it.onNoticesChanged() }
    }
}