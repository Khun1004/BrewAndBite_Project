package com.example.brewandbite.mypage

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.brewandbite.R
import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.auth.LoginActivity
import com.example.brewandbite.auth.PaymentRepository
import com.example.brewandbite.auth.PhoneIndex
import com.example.brewandbite.auth.UserProfile
import com.example.brewandbite.auth.UserRepository
import com.example.brewandbite.common.setup
import com.example.brewandbite.databinding.FragmentMypageBinding
import com.example.brewandbite.favorite.FavoriteManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * My Page 탭
 *  - 프로필 사진: 동그라미를 누르면 앨범에서 선택 / 파일에서 선택 / 기본 이미지로 (ProfilePhoto.kt)
 *  - 로그인 전: 로그인 안내 카드 + [로그인 / 회원가입]
 *  - 로그인 후: 프로필 카드 + 메뉴 목록 (왼쪽 아이콘 · 가운데 글(요약) · 오른쪽 >)
 *      줄을 누르면 상세 화면(MyDetailActivity)이 새로 열린다
 *      👤 내 정보 · 📍 배달 주소 · 💳 결제 수단 · 🧾 주문 기록 · ♡ 찜한 메뉴 · 🔒 계정 보안
 */
class MyPageFragment : Fragment() {

    private var _binding: FragmentMypageBinding? = null
    private val binding get() = _binding!!

    /** 마지막으로 불러온 회원 정보 (수정 창에 미리 채워 넣기용) */
    private var profile: UserProfile? = null

    /** 앨범에서 사진 고르기 (권한 필요 없는 안드로이드 사진 선택기) */
    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) savePhoto(uri)
    }

    /** 파일에서 고르기 (다운로드 폴더 등 — 컴퓨터에서 에뮬레이터로 끌어다 놓은 사진이 여기에 있음) */
    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) savePhoto(uri)
    }

    /** 로그인 화면에서 돌아오면 다시 그리기 */
    private val loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) refresh()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMypageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 공통 헤더 (부제는 refresh()에서 로그인 상태에 따라 바뀜)
        binding.pageHeader.setup(
            R.string.header_my_eyebrow, R.string.header_my_title, "👤",
            getString(R.string.header_my_subtitle)
        )

        binding.buttonLogin.setOnClickListener { loginLauncher.launch(LoginActivity.intent(requireContext())) }
        binding.buttonLogout.setOnClickListener { confirmLogout() }

        // 메뉴 목록: 줄을 누르면 상세 화면으로
        val rows = mapOf(
            binding.rowInfo to MyDetailActivity.Section.INFO,
            binding.rowAddress to MyDetailActivity.Section.ADDRESS,
            binding.rowPayment to MyDetailActivity.Section.PAYMENT,
            binding.rowOrders to MyDetailActivity.Section.ORDERS,
            binding.rowFavorites to MyDetailActivity.Section.FAVORITES,
            binding.rowSecurity to MyDetailActivity.Section.SECURITY
        )
        rows.forEach { (row, section) ->
            row.setOnClickListener { startActivity(MyDetailActivity.intent(requireContext(), section)) }
        }

        binding.avatarBox.setOnClickListener { showPhotoMenu() }
    }

    /** 찜이 바뀌면 '찜한 메뉴' 줄의 개수도 바로 바꾼다 */
    private val favoriteListener = FavoriteManager.FavoriteListener { showFavoriteSummary() }

    override fun onStart() {
        super.onStart()
        FavoriteManager.addListener(favoriteListener)
        refresh()
    }

    override fun onStop() {
        super.onStop()
        FavoriteManager.removeListener(favoriteListener)
    }

    private fun showFavoriteSummary() {
        val b = _binding ?: return
        val count = FavoriteManager.count()
        b.textFavoritesSummary.text =
            if (count > 0) getString(R.string.my_favorites_summary, count) else getString(R.string.my_favorites_none)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refresh()   // 탭을 다시 눌렀을 때 (다른 화면에서 로그인/로그아웃 했을 수 있음)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ───────── 화면 그리기 ─────────

    /** 로그인 상태에 맞게 화면을 그린다 */
    private fun refresh() {
        val b = _binding ?: return
        val loggedIn = AuthManager.isLoggedIn()
        b.loggedOutView.visibility = if (loggedIn) View.GONE else View.VISIBLE
        b.loggedInView.visibility = if (loggedIn) View.VISIBLE else View.GONE

        if (!loggedIn) {
            profile = null
            b.pageHeader.textSubtitle.setText(R.string.header_my_subtitle)
            return
        }

        // 서버에서 불러오는 동안 이메일이라도 먼저 보여 주기
        if (profile == null) b.textName.setText(R.string.my_loading)
        b.textEmail.text = AuthManager.currentEmail().orEmpty()
        UserRepository.loadMyProfile { showProfile(it) }
        // 결제 수단 요약 (예: 2개 · 기본: 카카오페이)
        PaymentRepository.loadMyPayments { list ->
            val bb = _binding ?: return@loadMyPayments
            val default = list.firstOrNull { it.defaultMethod } ?: list.firstOrNull()
            bb.textPaymentSummary.text =
                if (default == null) getString(R.string.my_no_payment)
                else getString(R.string.my_payment_count, list.size, default.displayName())
        }

        b.textOrdersSummary.text = getString(R.string.my_menu_soon)
        showFavoriteSummary()
        b.textSecuritySummary.text = getString(
            if (AuthManager.hasPassword()) R.string.my_change_password else R.string.my_security_google_summary
        )
    }

    private fun showProfile(loaded: UserProfile?) {
        val b = _binding ?: return
        profile = loaded
        // 예전에 가입한 계정도 휴대폰 번호로 로그인할 수 있게 목록에 등록 (이미 있으면 그대로)
        val uid = AuthManager.currentUid()
        val myEmail = AuthManager.currentEmail()
        if (loaded != null && loaded.phone.isNotBlank() && uid != null && myEmail != null) {
            PhoneIndex.claim(loaded.phone, uid, myEmail)
        }
        val empty = getString(R.string.my_empty_value)
        val email = loaded?.email?.takeIf { it.isNotBlank() } ?: AuthManager.currentEmail().orEmpty()
        val name = loaded?.name?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
        val phone = loaded?.phone?.takeIf { it.isNotBlank() } ?: empty

        // 프로필 카드
        b.textAvatar.text = name.take(1).uppercase()
        showAvatar(loaded?.photo.orEmpty())
        b.textName.text = name
        b.textEmail.text = email
        b.pageHeader.textSubtitle.text = getString(R.string.my_hello, name)

        // 목록 요약
        b.textInfoSummary.text = phone
        val fullAddress = "${loaded?.address.orEmpty()} ${loaded?.addressDetail.orEmpty()}".trim()
        b.textAddressSummary.text = fullAddress.ifBlank { empty }
    }

    // ───────── 📷 프로필 사진 ─────────

    /**
     * 사진 보여 주기: 저장한 사진 → (없으면) Google 계정 사진 → (없으면) 이름 첫 글자
     */
    private fun showAvatar(photo: String) {
        val b = _binding ?: return
        val bitmap = if (photo.isNotBlank()) ProfilePhoto.decode(photo) else null
        val googlePhoto = AuthManager.currentPhotoUrl()
        when {
            bitmap != null -> {
                b.imageAvatar.setImageBitmap(bitmap)
                b.imageAvatar.visibility = View.VISIBLE
            }
            googlePhoto != null -> {
                Glide.with(this).load(googlePhoto).centerCrop().into(b.imageAvatar)
                b.imageAvatar.visibility = View.VISIBLE
            }
            else -> b.imageAvatar.visibility = View.GONE   // 첫 글자 동그라미가 보임
        }
    }

    private fun showPhotoMenu() {
        val hasPhoto = profile?.photo?.isNotBlank() == true
        val items = mutableListOf(getString(R.string.photo_pick), getString(R.string.photo_file))
        if (hasPhoto) items.add(getString(R.string.photo_remove))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.photo_change)
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    1 -> filePicker.launch("image/*")
                    else -> removePhoto()
                }
            }
            .show()
    }

    /** 고른 사진을 줄여서 저장하고 바로 화면에 반영 */
    private fun savePhoto(uri: android.net.Uri) {
        toast(R.string.photo_saving)
        ProfilePhoto.encode(requireContext(), uri) { base64 ->
            if (base64 == null) {
                toast(R.string.photo_failed)
                return@encode
            }
            UserRepository.updateMyFields(mapOf("photo" to base64)) { ok ->
                if (ok) {
                    profile?.photo = base64
                    showAvatar(base64)
                }
                toast(if (ok) R.string.photo_saved else R.string.my_save_failed)
            }
        }
    }

    private fun removePhoto() {
        UserRepository.updateMyFields(mapOf("photo" to "")) { ok ->
            if (ok) {
                profile?.photo = ""
                showAvatar("")
            }
            toast(if (ok) R.string.photo_removed else R.string.my_save_failed)
        }
    }

    // ───────── 기타 ─────────

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.my_logout_title)
            .setNegativeButton(R.string.cart_cancel, null)
            .setPositiveButton(R.string.my_logout) { _, _ ->
                AuthManager.signOut()
                toast(R.string.my_logout_done)
                refresh()
            }
            .show()
    }

    private fun toast(message: Int) {
        context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
    }
}