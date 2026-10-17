package com.example.brewandbite.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Google 계정 선택 결과 */
data class GoogleAccount(val idToken: String, val name: String, val email: String)

/**
 * 'Google 계정으로 계속하기' — 휴대폰의 Google 계정 선택 창을 띄우고 로그인용 토큰을 받는다.
 * (Android Credential Manager 사용, 무료)
 *
 * 필요한 설정
 *  1) Firebase 콘솔 → Authentication → Sign-in method → Google 사용 설정
 *  2) 프로젝트 설정에 SHA-1 등록 + google-services.json 다시 받기
 *     → 빌드하면 R.string.default_web_client_id 가 자동으로 생긴다
 */
object GoogleSignInHelper {

    /**
     * @param onResult 성공: (계정, null) / 사용자가 취소: (null, null) / 실패: (null, 안내 문장)
     */
    fun pickAccount(activity: Activity, onResult: (GoogleAccount?, String?) -> Unit) {
        val clientId = webClientId(activity)
        if (clientId == null) {
            onResult(null, "Firebase에서 Google 로그인을 켜고 google-services.json 을 다시 받아 주세요")
            return
        }

        val option = GetSignInWithGoogleOption.Builder(clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        CredentialManager.create(activity).getCredentialAsync(
            activity,
            request,
            CancellationSignal(),
            ContextCompat.getMainExecutor(activity),
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    val credential = result.credential
                    if (credential is CustomCredential &&
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    ) {
                        try {
                            val google = GoogleIdTokenCredential.createFrom(credential.data)
                            onResult(
                                GoogleAccount(google.idToken, google.displayName.orEmpty(), google.id),
                                null
                            )
                        } catch (e: Exception) {
                            onResult(null, "Google 계정 정보를 읽지 못했어요")
                        }
                    } else {
                        onResult(null, "Google 계정 정보를 읽지 못했어요")
                    }
                }

                override fun onError(e: GetCredentialException) {
                    when (e) {
                        is GetCredentialCancellationException -> onResult(null, null)   // 사용자가 닫음
                        is NoCredentialException ->
                            onResult(null, "휴대폰(에뮬레이터) 설정에서 Google 계정을 먼저 추가해 주세요")
                        else -> onResult(null, "Google 로그인에 실패했어요 (${e.message ?: e.type})")
                    }
                }
            }
        )
    }

    /** google-services.json 에서 자동으로 만들어지는 웹 클라이언트 ID (없으면 null) */
    @SuppressLint("DiscouragedApi")   // 설정 전에도 빌드되도록 이름으로 찾는다
    private fun webClientId(activity: Activity): String? {
        val id = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        return if (id == 0) null else activity.getString(id)
    }
}