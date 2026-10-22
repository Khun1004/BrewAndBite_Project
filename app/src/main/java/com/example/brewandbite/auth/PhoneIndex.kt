package com.example.brewandbite.auth

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/** phoneIndex/{번호} 에 저장되는 값: 이 번호의 회원 ID, 로그인용 이메일, 가입 방법 */
data class PhoneEntry(
    var uid: String = "",
    var email: String = "",
    var provider: String = PROVIDER_PASSWORD   // "password" = 번호+비밀번호, "google" = Google 계정
) {
    companion object {
        const val PROVIDER_PASSWORD = "password"
        const val PROVIDER_GOOGLE = "google"
    }
}

/**
 * 휴대폰 번호로 로그인하기 위한 '번호 → 계정' 목록 (Realtime Database 의 phoneIndex/{번호}).
 *
 * Firebase 로그인은 이메일 + 비밀번호로 하고, 사용자는 휴대폰 번호만 입력한다.
 * 로그인할 때 번호로 이메일을 찾아서 대신 넣어 주는 방식.
 * 키는 숫자만 사용 (010-1234-5678 → "01012345678").
 */
object PhoneIndex {

    private const val TAG = "PhoneIndex"

    private val ref get() = FirebaseDatabase.getInstance().getReference("phoneIndex")

    fun key(phone: String): String = phone.filter { it.isDigit() }

    /** 서버가 이 시간 안에 답하지 않으면 실패로 처리 (인터넷이 끊기면 Firebase 는 끝없이 기다리기 때문) */
    private const val TIMEOUT_MS = 10_000L
    private val main = Handler(Looper.getMainLooper())

    /** 번호로 계정 찾기. 없으면 entry = null, 서버 오류·시간 초과면 failed = true */
    fun lookup(phone: String, onResult: (entry: PhoneEntry?, failed: Boolean) -> Unit) {
        val k = key(phone)
        if (k.isEmpty()) return onResult(null, false)
        val query = ref.child(k)
        var finished = false
        lateinit var listener: ValueEventListener
        val timeout = Runnable {
            if (finished) return@Runnable
            finished = true
            query.removeEventListener(listener)
            Log.w(TAG, "phoneIndex 응답 없음 (${TIMEOUT_MS / 1000}초) — 인터넷 연결 또는 database URL 확인")
            onResult(null, true)
        }
        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (finished) return
                finished = true
                main.removeCallbacks(timeout)
                val entry = snapshot.getValue(PhoneEntry::class.java)
                onResult(entry?.takeIf { it.email.isNotBlank() }, false)
            }

            override fun onCancelled(error: DatabaseError) {
                if (finished) return
                finished = true
                main.removeCallbacks(timeout)
                Log.w(TAG, "phoneIndex 읽기 실패: ${error.message}")
                onResult(null, true)
            }
        }
        main.postDelayed(timeout, TIMEOUT_MS)
        query.addListenerForSingleValueEvent(listener)
    }

    /** 이 번호를 내 계정으로 등록 (회원가입 직후, 번호 변경, 예전 계정 보정). 가입 방법은 자동으로 기록 */
    fun claim(phone: String, uid: String, email: String, onDone: (Boolean) -> Unit = {}) {
        val k = key(phone)
        if (k.isEmpty() || uid.isEmpty() || email.isEmpty()) return onDone(false)
        val provider = if (AuthManager.hasPassword()) PhoneEntry.PROVIDER_PASSWORD else PhoneEntry.PROVIDER_GOOGLE
        ref.child(k).setValue(PhoneEntry(uid, email, provider))
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    /** 번호 등록 해제 (번호를 바꿨을 때 예전 번호) */
    fun release(phone: String, onDone: (Boolean) -> Unit = {}) {
        val k = key(phone)
        if (k.isEmpty()) return onDone(false)
        ref.child(k).removeValue()
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }
}