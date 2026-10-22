package com.example.brewandbite.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider

/**
 * 로그인 / 회원가입 / 로그아웃 (Firebase Authentication).
 *  - 사용자는 '휴대폰 번호 + 비밀번호' 로 로그인한다.
 *    (Firebase 계정은 이메일로 만들고, phoneIndex 에서 번호 → 이메일을 찾아 로그인 — PhoneIndex.kt)
 *  - 휴대폰 인증은 앱 안에서 처리 (PhoneVerifier.kt) 하고, 인증된 번호만 저장한다.
 *  - 이메일은 비밀번호 재설정 메일을 받을 때 쓴다.
 *  - 'Google 계정으로 계속하기' 도 가능 (signInWithGoogle → 처음이면 추가 정보 입력 → completeGoogleProfile)
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

    /** Google 계정 사진 주소 (Google 로 로그인했을 때만, 없으면 null) */
    fun currentPhotoUrl(): String? = auth.currentUser?.photoUrl?.toString()

    /** 비밀번호로 로그인하는 계정인지 (Google 로만 가입했으면 false → 비밀번호 변경 불가) */
    fun hasPassword(): Boolean =
        auth.currentUser?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true

    private const val DB_FAILED_MSG = "서버에 연결하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요"

    private const val GOOGLE_ACCOUNT_MSG = "Google 계정으로 가입한 번호예요. 'Google 계정으로 계속하기'를 눌러 주세요"

    /** 로그인. 실패하면 화면에 보여 줄 한국어 메시지를 돌려준다. */
    fun signIn(email: String, password: String, onResult: (errorMessage: String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { onResult(toKoreanMessage(it)) }
    }

    /** 휴대폰 번호 + 비밀번호로 로그인 */
    fun signInWithPhone(phone: String, password: String, onResult: (errorMessage: String?) -> Unit) {
        PhoneIndex.lookup(phone) { entry, failed ->
            when {
                failed -> onResult(DB_FAILED_MSG)
                entry == null -> onResult("가입되지 않은 휴대폰 번호예요")
                entry.provider == PhoneEntry.PROVIDER_GOOGLE -> onResult(GOOGLE_ACCOUNT_MSG)
                else -> auth.signInWithEmailAndPassword(entry.email, password)
                    .addOnSuccessListener { onResult(null) }
                    .addOnFailureListener {
                        onResult(
                            if (it is FirebaseAuthInvalidCredentialsException || it is FirebaseAuthInvalidUserException)
                                "휴대폰 번호 또는 비밀번호가 맞지 않아요"
                            else toKoreanMessage(it)
                        )
                    }
            }
        }
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
        // 같은 휴대폰 번호로 이미 가입했는지 먼저 확인
        PhoneIndex.lookup(profile.phone) { existing, failed ->
            if (failed) return@lookup onResult(DB_FAILED_MSG)
            if (existing != null) return@lookup onResult("이미 가입된 휴대폰 번호예요. 로그인해 주세요")

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid.orEmpty()
                    val saved = profile.copy(uid = uid, email = email, createdAt = System.currentTimeMillis())
                    PhoneIndex.claim(saved.phone, uid, email)          // 번호로 로그인할 수 있게 등록
                    UserRepository.saveProfile(saved) { onResult(null) }
                }
                .addOnFailureListener { onResult(toKoreanMessage(it)) }
        }
    }

    /**
     * 비밀번호 재설정 메일 보내기 (Firebase가 메일 발송 · 무료).
     * 메일의 링크를 누르면 새 비밀번호를 정하는 웹 페이지가 열린다.
     * 보안상 가입되지 않은 이메일이어도 '보냈다'고 나올 수 있다.
     */
    fun sendPasswordReset(email: String, onResult: (errorMessage: String?) -> Unit) {
        auth.setLanguageCode("ko")   // 메일을 한국어로
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { onResult(toKoreanMessage(it)) }
    }

    // ───────── Google 계정 ─────────

    /**
     * Google 계정으로 로그인. 처음 온 사람(휴대폰·주소 정보가 없음)이면 needsProfile = true
     * → 추가 정보 입력 화면을 보여 주고 completeGoogleProfile() 로 마무리
     */
    fun signInWithGoogle(account: GoogleAccount, onResult: (needsProfile: Boolean, errorMessage: String?) -> Unit) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(account.idToken, null))
            .addOnSuccessListener {
                UserRepository.loadMyProfile { profile ->
                    onResult(profile == null || profile.phone.isBlank() || profile.address.isBlank(), null)
                }
            }
            .addOnFailureListener { onResult(false, toKoreanMessage(it)) }
    }

    /** Google 로 처음 가입한 사람의 추가 정보(이름·휴대폰·주소) 저장 */
    fun completeGoogleProfile(profile: UserProfile, onResult: (errorMessage: String?) -> Unit) {
        val user = auth.currentUser ?: return onResult("로그인이 필요해요")
        val email = user.email.orEmpty()
        PhoneIndex.lookup(profile.phone) { existing, failed ->
            if (failed) return@lookup onResult(DB_FAILED_MSG)
            if (existing != null && existing.uid != user.uid) return@lookup onResult("이미 가입된 휴대폰 번호예요")

            val saved = profile.copy(uid = user.uid, email = email, createdAt = System.currentTimeMillis())
            UserRepository.saveProfile(saved) { ok ->
                if (ok) {
                    PhoneIndex.claim(saved.phone, user.uid, email)
                    onResult(null)
                } else {
                    onResult("정보를 저장하지 못했어요. 잠시 후 다시 시도해 주세요")
                }
            }
        }
    }

    /** 휴대폰 번호로 계정을 찾아 그 이메일로 재설정 메일 보내기. 성공하면 가린 이메일(kh***@gmail.com)을 돌려준다 */
    fun sendPasswordResetByPhone(phone: String, onResult: (maskedEmail: String?, errorMessage: String?) -> Unit) {
        PhoneIndex.lookup(phone) { entry, failed ->
            when {
                failed -> onResult(null, "잠시 후 다시 시도해 주세요")
                entry == null -> onResult(null, "가입되지 않은 휴대폰 번호예요")
                entry.provider == PhoneEntry.PROVIDER_GOOGLE -> onResult(null, GOOGLE_ACCOUNT_MSG)
                else -> sendPasswordReset(entry.email) { error ->
                    if (error == null) onResult(maskEmail(entry.email), null) else onResult(null, error)
                }
            }
        }
    }

    /** khun1000@gmail.com → kh******@gmail.com */
    private fun maskEmail(email: String): String {
        val at = email.indexOf('@')
        if (at <= 2) return email
        return email.take(2) + "*".repeat(at - 2) + email.substring(at)
    }

    /**
     * 비밀번호 변경 (로그인한 상태에서).
     * 보안을 위해 현재 비밀번호로 한 번 더 확인(재인증)한 뒤 새 비밀번호로 바꾼다.
     */
    fun changePassword(currentPassword: String, newPassword: String, onResult: (errorMessage: String?) -> Unit) {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email == null) return onResult("로그인이 필요해요")

        user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword))
            .addOnSuccessListener {
                user.updatePassword(newPassword)
                    .addOnSuccessListener { onResult(null) }
                    .addOnFailureListener { onResult(toKoreanMessage(it)) }
            }
            .addOnFailureListener {
                onResult(
                    when (it) {
                        is FirebaseAuthInvalidCredentialsException -> "현재 비밀번호가 맞지 않아요"
                        is FirebaseTooManyRequestsException -> "시도가 너무 많아요. 잠시 후 다시 해 주세요"
                        else -> toKoreanMessage(it)
                    }
                )
            }
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