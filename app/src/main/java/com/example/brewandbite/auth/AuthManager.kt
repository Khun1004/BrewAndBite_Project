package com.example.brewandbite.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

/**
 * 로그인 / 회원가입 / 로그아웃 (Firebase Authentication, 이메일 + 비밀번호).
 * 휴대폰 인증은 앱 안에서 처리 (PhoneVerifier.kt) 하고, 인증된 번호만 회원 정보에 저장한다.
 *
 * ───────── 팀원용 사용법 ─────────
 *  로그인 했는지      AuthManager.isLoggedIn()
 *  내 회원 ID        AuthManager.currentUid()      (주문 저장 시 orders/{회원ID} 에 사용)
 *  내 이메일         AuthManager.currentEmail()
 *  로그아웃          AuthManager.signOut()
 * ───────────────────────────────
 */
object AuthManager {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun currentUid(): String? = auth.currentUser?.uid

    fun currentEmail(): String? = auth.currentUser?.email

    /** 로그인. 실패하면 화면에 보여 줄 한국어 메시지를 돌려준다. */
    fun signIn(email: String, password: String, onResult: (errorMessage: String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { onResult(toKoreanMessage(it)) }
    }

    /**
     * 회원가입: 계정을 만들고, 이름·연락처·주소를 users/{회원ID} 에 저장한다.
     * 계정은 만들어졌는데 정보 저장만 실패해도 가입은 된 것으로 본다 (My Page에서 다시 입력 가능).
     */
    fun signUp(
        email: String,
        password: String,
        profile: UserProfile,
        onResult: (errorMessage: String?) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid.orEmpty()
                val saved = profile.copy(uid = uid, email = email, createdAt = System.currentTimeMillis())
                UserRepository.saveProfile(saved) { onResult(null) }
            }
            .addOnFailureListener { onResult(toKoreanMessage(it)) }
    }

    fun signOut() {
        auth.signOut()
    }

    /** Firebase 오류 → 사용자에게 보여 줄 문장 */
    private fun toKoreanMessage(e: Exception): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "비밀번호는 6자 이상으로 만들어 주세요"
        is FirebaseAuthUserCollisionException -> "이미 가입된 이메일이에요. 로그인해 주세요"
        is FirebaseAuthInvalidUserException -> "가입되지 않은 이메일이에요"
        is FirebaseAuthInvalidCredentialsException -> "이메일 또는 비밀번호가 맞지 않아요"
        is FirebaseNetworkException -> "인터넷 연결을 확인해 주세요"
        else -> "잠시 후 다시 시도해 주세요 (${e.localizedMessage ?: "알 수 없는 오류"})"
    }
}