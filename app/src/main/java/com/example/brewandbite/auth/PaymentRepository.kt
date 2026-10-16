package com.example.brewandbite.auth

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * 결제 수단 저장 / 불러오기 (payments/{회원ID}/{결제수단ID}).
 *
 * ───────── 팀원용 사용법 (Order 화면에서 결제 수단 고르기) ─────────
 *  PaymentRepository.loadMyPayments { list ->
 *      val defaultPay = list.firstOrNull { it.defaultMethod } ?: list.firstOrNull()
 *      textPayment.text = defaultPay?.displayName() ?: "결제 수단을 추가해 주세요"
 *  }
 * ─────────────────────────────────────────────────────────────
 */
object PaymentRepository {

    private fun myRef() = AuthManager.currentUid()?.let {
        FirebaseDatabase.getInstance().getReference("payments").child(it)
    }

    /** 내 결제 수단 목록 (기본 결제 수단이 맨 위) */
    fun loadMyPayments(onResult: (List<PaymentMethod>) -> Unit) {
        val ref = myRef() ?: return onResult(emptyList())
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { it.getValue(PaymentMethod::class.java) }
                onResult(list.sortedWith(compareByDescending<PaymentMethod> { it.defaultMethod }.thenBy { it.createdAt }))
            }

            override fun onCancelled(error: DatabaseError) {
                onResult(emptyList())
            }
        })
    }

    /** 결제 수단 추가. 처음 추가하는 것이거나 makeDefault=true 이면 기본으로 설정 */
    fun add(method: PaymentMethod, makeDefault: Boolean, onDone: (Boolean) -> Unit = {}) {
        val ref = myRef() ?: return onDone(false)
        loadMyPayments { current ->
            val id = ref.push().key ?: return@loadMyPayments onDone(false)
            val asDefault = makeDefault || current.isEmpty()
            val newItem = method.copy(id = id, defaultMethod = asDefault, createdAt = System.currentTimeMillis())

            val updates = HashMap<String, Any>()
            if (asDefault) current.forEach { updates["${it.id}/defaultMethod"] = false }
            updates[id] = newItem
            ref.updateChildren(updates)
                .addOnSuccessListener { onDone(true) }
                .addOnFailureListener { onDone(false) }
        }
    }

    /** 기본 결제 수단 바꾸기 */
    fun setDefault(id: String, onDone: (Boolean) -> Unit = {}) {
        val ref = myRef() ?: return onDone(false)
        loadMyPayments { current ->
            val updates = HashMap<String, Any>()
            current.forEach { updates["${it.id}/defaultMethod"] = (it.id == id) }
            ref.updateChildren(updates)
                .addOnSuccessListener { onDone(true) }
                .addOnFailureListener { onDone(false) }
        }
    }

    /** 결제 수단 삭제 (기본을 지우면 남은 것 중 첫 번째가 기본이 됨) */
    fun remove(id: String, onDone: (Boolean) -> Unit = {}) {
        val ref = myRef() ?: return onDone(false)
        loadMyPayments { current ->
            val removed = current.firstOrNull { it.id == id }
            val rest = current.filter { it.id != id }
            val updates = HashMap<String, Any?>()
            updates[id] = null
            if (removed?.defaultMethod == true && rest.isNotEmpty()) updates["${rest.first().id}/defaultMethod"] = true
            @Suppress("UNCHECKED_CAST")   // 값이 null 이면 Firebase에서 삭제됨
            val changes = updates as Map<String, Any>
            ref.updateChildren(changes)
                .addOnSuccessListener { onDone(true) }
                .addOnFailureListener { onDone(false) }
        }
    }
}