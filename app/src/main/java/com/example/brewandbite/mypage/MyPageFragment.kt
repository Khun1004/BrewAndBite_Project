package com.example.brewandbite.mypage

import android.app.Activity
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.brewandbite.R
import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.auth.LoginActivity
import com.example.brewandbite.auth.PaymentMethod
import com.example.brewandbite.auth.PaymentRepository
import com.example.brewandbite.auth.UserProfile
import com.example.brewandbite.auth.UserRepository
import com.example.brewandbite.common.setup
import com.example.brewandbite.databinding.DialogAddPaymentBinding
import com.example.brewandbite.databinding.DialogTwoFieldsBinding
import com.example.brewandbite.databinding.FragmentMypageBinding
import com.example.brewandbite.databinding.ItemPaymentMethodBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * My Page 탭
 *  - 로그인 전: 로그인 안내 카드 + [로그인 / 회원가입]
 *  - 로그인 후: 프로필 카드 + 목록 (왼쪽 아이콘 · 가운데 글 · 오른쪽 >)
 *      > 를 누르면 아래로 내용이 펼쳐진다 (한 번 더 누르면 접힘)
 *      👤 내 정보   : 이름·이메일·연락처 + [정보 수정]
 *      📍 배달 주소 : 주소·상세 주소 + [주소 변경]
 *      💳 결제 수단 : 목록(눌러서 기본 설정, ✕ 삭제) + [결제 수단 추가]
 *      🧾 주문 기록 / ♡ 찜한 메뉴 : 다음 단계
 */
class MyPageFragment : Fragment() {

    private var _binding: FragmentMypageBinding? = null
    private val binding get() = _binding!!

    /** 마지막으로 불러온 회원 정보 (수정 창에 미리 채워 넣기용) */
    private var profile: UserProfile? = null

    /** 펼치는 목록 한 칸: 누르는 줄, 펼쳐지는 내용, > 화살표 */
    private data class Section(val row: View, val content: View, val chevron: ImageView)

    private lateinit var sections: List<Section>

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

        // 펼치는 목록
        sections = listOf(
            Section(binding.rowInfo, binding.contentInfo, binding.chevronInfo),
            Section(binding.rowAddress, binding.contentAddress, binding.chevronAddress),
            Section(binding.rowPayment, binding.contentPayment, binding.chevronPayment),
            Section(binding.rowOrders, binding.contentOrders, binding.chevronOrders),
            Section(binding.rowFavorites, binding.contentFavorites, binding.chevronFavorites)
        )
        sections.forEach { section -> section.row.setOnClickListener { toggle(section) } }

        binding.buttonEditInfo.setOnClickListener { showEditInfoDialog() }
        binding.buttonEditAddress.setOnClickListener { showEditAddressDialog() }
        binding.buttonAddPayment.setOnClickListener { showAddPaymentDialog() }
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

    // ───────── 펼치기 / 접기 ─────────

    /** > 를 누르면 내용이 부드럽게 펼쳐지고, 화살표가 아래(⌄)로 돌아간다 */
    private fun toggle(section: Section) {
        val expand = section.content.visibility != View.VISIBLE
        TransitionManager.beginDelayedTransition(binding.loggedInView, AutoTransition().setDuration(200))
        section.content.visibility = if (expand) View.VISIBLE else View.GONE
        section.chevron.animate().rotation(if (expand) 90f else 0f).setDuration(200).start()
    }

    /** 로그아웃할 때 모두 접어 두기 */
    private fun collapseAll() {
        sections.forEach {
            it.content.visibility = View.GONE
            it.chevron.rotation = 0f
        }
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
            collapseAll()
            b.pageHeader.textSubtitle.setText(R.string.header_my_subtitle)
            return
        }

        // 서버에서 불러오는 동안 이메일이라도 먼저 보여 주기
        if (profile == null) b.textName.setText(R.string.my_loading)
        b.textEmail.text = AuthManager.currentEmail().orEmpty()
        UserRepository.loadMyProfile { showProfile(it) }
        loadPayments()

        b.textOrdersSummary.text = getString(R.string.my_menu_soon)
        b.textFavoritesSummary.text = getString(R.string.my_menu_soon)
    }

    private fun showProfile(loaded: UserProfile?) {
        val b = _binding ?: return
        profile = loaded
        val empty = getString(R.string.my_empty_value)
        val email = loaded?.email?.takeIf { it.isNotBlank() } ?: AuthManager.currentEmail().orEmpty()
        val name = loaded?.name?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
        val phone = loaded?.phone?.takeIf { it.isNotBlank() } ?: empty
        val address = loaded?.address?.takeIf { it.isNotBlank() } ?: empty
        val detail = loaded?.addressDetail?.takeIf { it.isNotBlank() } ?: empty

        // 프로필 카드
        b.textAvatar.text = name.take(1).uppercase()
        b.textName.text = name
        b.textEmail.text = email
        b.pageHeader.textSubtitle.text = getString(R.string.my_hello, name)

        // 👤 내 정보
        b.textInfoSummary.text = phone
        b.textInfoName.text = name
        b.textInfoEmail.text = email
        b.textInfoPhone.text = phone

        // 📍 배달 주소
        val fullAddress = "${loaded?.address.orEmpty()} ${loaded?.addressDetail.orEmpty()}".trim()
        b.textAddressSummary.text = fullAddress.ifBlank { empty }
        b.textInfoAddress.text = address
        b.textInfoAddressDetail.text = detail
    }

    // ───────── 💳 결제 수단 ─────────

    private fun loadPayments() {
        PaymentRepository.loadMyPayments { showPayments(it) }
    }

    private fun showPayments(list: List<PaymentMethod>) {
        val b = _binding ?: return
        b.paymentList.removeAllViews()

        val default = list.firstOrNull { it.defaultMethod } ?: list.firstOrNull()
        b.textPaymentSummary.text =
            if (default == null) getString(R.string.my_no_payment)
            else getString(R.string.my_payment_count, list.size, default.displayName())
        b.textNoPayment.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        b.textPaymentTip.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE

        list.forEach { method ->
            val item = ItemPaymentMethodBinding.inflate(layoutInflater, b.paymentList, false)
            item.textPayEmoji.text = method.emoji()
            item.textPayName.text = method.displayName()
            item.textPayDefault.visibility = if (method.defaultMethod) View.VISIBLE else View.GONE
            // 줄을 누르면 기본 결제 수단으로
            item.root.setOnClickListener {
                if (method.defaultMethod) return@setOnClickListener
                PaymentRepository.setDefault(method.id) { ok ->
                    toast(if (ok) R.string.my_payment_set_default else R.string.my_save_failed)
                    if (ok) loadPayments()
                }
            }
            item.buttonPayDelete.setOnClickListener { confirmDeletePayment(method) }
            b.paymentList.addView(item.root)
        }
    }

    private fun confirmDeletePayment(method: PaymentMethod) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.my_payment_delete_title)
            .setMessage(method.displayName())
            .setNegativeButton(R.string.cart_cancel, null)
            .setPositiveButton(R.string.my_delete) { _, _ ->
                PaymentRepository.remove(method.id) { ok ->
                    toast(if (ok) R.string.my_payment_deleted else R.string.my_save_failed)
                    if (ok) loadPayments()
                }
            }
            .show()
    }

    /** 결제 수단 추가 창: 종류 고르기 → (카드면) 카드사·끝 4자리 → 기본 설정 여부 */
    private fun showAddPaymentDialog() {
        val d = DialogAddPaymentBinding.inflate(layoutInflater)
        d.chipGroupType.setOnCheckedStateChangeListener { _, _ ->
            d.cardFields.visibility = if (d.chipCard.isChecked) View.VISIBLE else View.GONE
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pay_dialog_title)
            .setView(d.root)
            .setNegativeButton(R.string.cart_cancel, null)
            .setPositiveButton(R.string.my_save, null)   // 아래에서 직접 처리 (입력이 틀리면 창이 안 닫히게)
            .show()

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val type = when (d.chipGroupType.checkedChipId) {
                R.id.chipKakao -> PaymentMethod.TYPE_KAKAO
                R.id.chipNaver -> PaymentMethod.TYPE_NAVER
                R.id.chipToss -> PaymentMethod.TYPE_TOSS
                R.id.chipCash -> PaymentMethod.TYPE_CASH
                else -> PaymentMethod.TYPE_CARD
            }
            var company = ""
            var last4 = ""
            if (type == PaymentMethod.TYPE_CARD) {
                company = d.editCardCompany.text?.toString()?.trim().orEmpty().ifBlank { "카드" }
                last4 = d.editLast4.text?.toString()?.trim().orEmpty()
                if (last4.length != 4 || !last4.all { it.isDigit() }) {
                    d.layoutLast4.error = getString(R.string.pay_err_last4)
                    return@setOnClickListener
                }
                d.layoutLast4.error = null
            }

            val method = PaymentMethod(type = type, cardCompany = company, last4 = last4)
            PaymentRepository.add(method, d.checkDefault.isChecked) { ok ->
                toast(if (ok) R.string.my_payment_added else R.string.my_save_failed)
                if (ok) {
                    dialog.dismiss()
                    loadPayments()
                }
            }
        }
    }

    // ───────── 👤 내 정보 / 📍 주소 수정 ─────────

    private fun showEditInfoDialog() {
        val d = DialogTwoFieldsBinding.inflate(layoutInflater)
        d.layoutFirst.hint = getString(R.string.my_label_name)
        d.editFirst.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        d.editFirst.setText(profile?.name.orEmpty())
        d.layoutSecond.hint = getString(R.string.my_label_phone)
        d.editSecond.inputType = InputType.TYPE_CLASS_PHONE
        d.editSecond.setText(profile?.phone.orEmpty())

        showTwoFieldDialog(R.string.my_edit_info, d) { first, second ->
            mapOf("name" to first, "phone" to second)
        }
    }

    private fun showEditAddressDialog() {
        val d = DialogTwoFieldsBinding.inflate(layoutInflater)
        d.layoutFirst.hint = getString(R.string.my_label_address)
        d.editFirst.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
        d.editFirst.setText(profile?.address.orEmpty())
        d.layoutSecond.hint = getString(R.string.my_label_address_detail)
        d.editSecond.inputType = InputType.TYPE_CLASS_TEXT
        d.editSecond.setText(profile?.addressDetail.orEmpty())

        showTwoFieldDialog(R.string.my_edit_address, d) { first, second ->
            mapOf("address" to first, "addressDetail" to second)
        }
    }

    /** 입력 칸 2개짜리 창을 띄우고, [저장]을 누르면 Firebase users/{uid} 에 그 값만 저장 */
    private fun showTwoFieldDialog(
        title: Int,
        d: DialogTwoFieldsBinding,
        toFields: (first: String, second: String) -> Map<String, Any>
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(d.root)
            .setNegativeButton(R.string.cart_cancel, null)
            .setPositiveButton(R.string.my_save) { _, _ ->
                val first = d.editFirst.text?.toString()?.trim().orEmpty()
                val second = d.editSecond.text?.toString()?.trim().orEmpty()
                UserRepository.updateMyFields(toFields(first, second)) { ok ->
                    toast(if (ok) R.string.my_saved else R.string.my_save_failed)
                    if (ok) UserRepository.loadMyProfile { showProfile(it) }
                }
            }
            .show()
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