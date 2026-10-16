package com.example.brewandbite.auth

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * 회원 정보 저장 / 불러오기 (users/{회원ID}).
 *
 * ───────── 팀원용 사용법 (Order 화면에서 주문자 정보 자동 입력) ─────────
 *  UserRepository.loadMyProfile { profile ->
 *      if (profile != null) {
 *          editName.setText(profile.name)
 *          editPhone.setText(profile.phone)
 *          editAddress.setText(profile.address)
 *      }
 *  }
 * ────────────────────────────────────────────────────────────────
 */
object UserRepository {

    private val usersRef get() = FirebaseDatabase.getInstance().getReference("users")

    /** 회원 정보 저장 (회원가입 직후, 내 정보 수정 시) */
    fun saveProfile(profile: UserProfile, onDone: (success: Boolean) -> Unit = {}) {
        if (profile.uid.isEmpty()) {
            onDone(false)
            return
        }
        usersRef.child(profile.uid).setValue(profile)
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    /**
     * 내 회원 정보 일부만 바꾸기 (예: 이름·연락처, 주소).
     * 예) UserRepository.updateMyFields(mapOf("address" to "대구 북구 ...", "addressDetail" to "101호"))
     */
    fun updateMyFields(fields: Map<String, Any>, onDone: (success: Boolean) -> Unit = {}) {
        val uid = AuthManager.currentUid() ?: return onDone(false)
        usersRef.child(uid).updateChildren(fields)
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    /** 로그인한 내 회원 정보 불러오기 (로그인 안 했으면 null) */
    fun loadMyProfile(onResult: (UserProfile?) -> Unit) {
        val uid = AuthManager.currentUid()
        if (uid == null) {
            onResult(null)
            return
        }
        usersRef.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onResult(snapshot.getValue(UserProfile::class.java))
            }

            override fun onCancelled(error: DatabaseError) {
                onResult(null)
            }
        })
    }
}