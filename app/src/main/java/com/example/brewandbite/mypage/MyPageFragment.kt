package com.example.brewandbite.mypage

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.brewandbite.R
import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.auth.LoginActivity
import com.example.brewandbite.auth.UserProfile
import com.example.brewandbite.auth.UserRepository
import com.example.brewandbite.common.setup
import com.example.brewandbite.databinding.FragmentMypageBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * My Page 탭
 *  - 로그인 전: 로그인 안내 카드 + [로그인 / 회원가입]
 *  - 로그인 후: 프로필(이름·이메일·연락처·주소) + 주문 기록 / 찜한 메뉴(다음 단계) + 로그아웃
 */
class MyPageFragment : Fragment() {

    private var _binding: FragmentMypageBinding? = null
    private val binding get() = _binding!!

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
        // TODO: 다음 단계에서 주문 기록 / 찜한 메뉴 화면 연결
        binding.rowOrders.setOnClickListener { showSoon() }
        binding.rowFavorites.setOnClickListener { showSoon() }
    }

    override fun onStart() {
        super.onStart()
        refresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refresh()   // 탭을 다시 눌렀을 때 (다른 화면에서 로그인/로그아웃 했을 수 있음)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** 로그인 상태에 맞게 화면을 그린다 */
    private fun refresh() {
        val b = _binding ?: return
        val loggedIn = AuthManager.isLoggedIn()
        b.loggedOutView.visibility = if (loggedIn) View.GONE else View.VISIBLE
        b.loggedInView.visibility = if (loggedIn) View.VISIBLE else View.GONE

        if (!loggedIn) {
            b.pageHeader.textSubtitle.setText(R.string.header_my_subtitle)
            return
        }

        // 서버에서 불러오는 동안 이메일이라도 먼저 보여 주기
        b.textName.setText(R.string.my_loading)
        b.textEmail.text = AuthManager.currentEmail().orEmpty()
        UserRepository.loadMyProfile { profile -> showProfile(profile) }
    }

    private fun showProfile(profile: UserProfile?) {
        val b = _binding ?: return
        val empty = getString(R.string.my_empty_value)
        val name = profile?.name?.takeIf { it.isNotBlank() } ?: AuthManager.currentEmail()?.substringBefore("@").orEmpty()

        b.textAvatar.text = name.take(1).uppercase()
        b.textName.text = name
        b.textEmail.text = profile?.email?.takeIf { it.isNotBlank() } ?: AuthManager.currentEmail().orEmpty()
        b.textPhone.text = profile?.phone?.takeIf { it.isNotBlank() } ?: empty
        b.textAddress.text = profile?.let { "${it.address} ${it.addressDetail}".trim() }?.takeIf { it.isNotBlank() } ?: empty
        b.pageHeader.textSubtitle.text = getString(R.string.my_hello, name)
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.my_logout_title)
            .setNegativeButton(R.string.cart_cancel, null)
            .setPositiveButton(R.string.my_logout) { _, _ ->
                AuthManager.signOut()
                Toast.makeText(requireContext(), R.string.my_logout_done, Toast.LENGTH_SHORT).show()
                refresh()
            }
            .show()
    }

    private fun showSoon() {
        Toast.makeText(requireContext(), R.string.my_menu_soon, Toast.LENGTH_SHORT).show()
    }
}