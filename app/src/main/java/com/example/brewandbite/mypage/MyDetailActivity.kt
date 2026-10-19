package com.example.brewandbite.mypage

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.brewandbite.MainActivity
import com.example.brewandbite.R
import com.example.brewandbite.address.AddressResult
import com.example.brewandbite.address.AddressSearchActivity
import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.auth.PaymentMethod
import com.example.brewandbite.auth.PaymentRepository
import com.example.brewandbite.auth.PhoneIndex
import com.example.brewandbite.auth.PhoneVerifier
import com.example.brewandbite.auth.UserProfile
import com.example.brewandbite.auth.UserRepository
import com.example.brewandbite.cart.CartManager
import com.example.brewandbite.common.applyStatusBarPadding
import com.example.brewandbite.databinding.ActivityMyDetailBinding
import com.example.brewandbite.databinding.DialogAddPaymentBinding
import com.example.brewandbite.databinding.DialogChangePasswordBinding
import com.example.brewandbite.databinding.DialogTwoFieldsBinding
import com.example.brewandbite.databinding.ItemPaymentMethodBinding
import com.example.brewandbite.databinding.LayoutPhoneVerifyBinding
import com.example.brewandbite.databinding.SectionMyAddressBinding
import com.example.brewandbite.databinding.SectionMyFavoritesBinding
import com.example.brewandbite.databinding.SectionMyInfoBinding
import com.example.brewandbite.databinding.SectionMyPaymentBinding
import com.example.brewandbite.databinding.SectionMySecurityBinding
import com.example.brewandbite.favorite.FavoriteManager
import com.example.brewandbite.home.MenuAdapter
import com.example.brewandbite.menu.MenuListActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * My Page 상세 화면. My Page 목록에서 > 를 누르면 열린다.
 * 헤더: [<] 뒤로 (왼쪽) · MY PAGE / 섹션 이름 (가운데) · [♡ 찜한 메뉴][🔔 알림] (오른쪽)
 *
 *  👤 내 정보   : 이름·이메일·연락처 + [이름 수정] [휴대폰 번호 변경(인증번호 확인)]
 *  📍 배달 주소 : 주소·상세 주소 + [주소 검색] → 상세 주소 입력
 *  💳 결제 수단 : 목록(눌러서 기본 설정, ✕ 삭제) + [결제 수단 추가]
 *  🧾 주문 기록 / ♡ 찜한 메뉴 : 다음 단계
 *  🔒 계정 보안 : [비밀번호 변경] (현재 비밀번호 확인 → 새 비밀번호)
 *
 * 여는 법: startActivity(MyDetailActivity.intent(context, MyDetailActivity.Section.INFO))
 */
class MyDetailActivity : AppCompatActivity() {

    /** 상세 화면 종류: 제목 · 이모지 */
    enum class Section(@StringRes val title: Int, val emoji: String) {
        INFO(R.string.my_section_info, "👤"),
        ADDRESS(R.string.my_section_address, "📍"),
        PAYMENT(R.string.my_section_payment, "💳"),
        ORDERS(R.string.my_section_orders, "🧾"),
        FAVORITES(R.string.my_section_favorites, "♡"),
        SECURITY(R.string.my_section_security, "🔒")
    }

    private lateinit var binding: ActivityMyDetailBinding
    private lateinit var section: Section

    // 섹션별 화면 (해당 섹션일 때만 값이 있음)
    private var info: SectionMyInfoBinding? = null
    private var address: SectionMyAddressBinding? = null
    private var payment: SectionMyPaymentBinding? = null
    private var security: SectionMySecurityBinding? = null
    private var favorites: SectionMyFavoritesBinding? = null
    private var favoriteAdapter: MenuAdapter? = null

    /** 찜이 바뀌면 (♡ 해제 등) 목록을 다시 그린다 */
    private val favoriteListener = FavoriteManager.FavoriteListener { showFavorites() }

    /** 마지막으로 불러온 회원 정보 (수정 창에 미리 채워 넣기용) */
    private var profile: UserProfile? = null

    /** 주소 검색 화면에서 주소를 고르면 → 상세 주소 입력 창 */
    private val addressLauncher = registerForActivityResult(AddressSearchActivity.Contract()) { result ->
        if (result != null) showAddressDetailDialog(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMyDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
        binding.headerBox.applyStatusBarPadding()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        section = Section.entries.getOrNull(intent.getIntExtra(EXTRA_SECTION, 0)) ?: Section.INFO
        binding.textTitle.text = "${section.emoji} ${getString(section.title)}"
        binding.buttonBack.setOnClickListener { finish() }
        // ♡ → 찜한 메뉴 화면 (이미 찜한 메뉴 화면이면 그대로)
        binding.buttonFavorite.setOnClickListener {
            if (section != Section.FAVORITES) startActivity(MyDetailActivity.intent(this, Section.FAVORITES))
        }
        // 🔔 → 알림 (다음 단계)
        binding.buttonBell.setOnClickListener { toast(R.string.my_notifications_soon) }

        // 섹션 내용을 카드 안에 넣기
        val parent = binding.container
        when (section) {
            Section.INFO -> info = SectionMyInfoBinding.inflate(layoutInflater, parent, true).apply {
                buttonEditName.setOnClickListener { showEditNameDialog() }
                buttonEditPhone.setOnClickListener { showEditPhoneDialog() }
            }
            Section.ADDRESS -> address = SectionMyAddressBinding.inflate(layoutInflater, parent, true).apply {
                buttonEditAddress.setOnClickListener { addressLauncher.launch(Unit) }
            }
            Section.PAYMENT -> payment = SectionMyPaymentBinding.inflate(layoutInflater, parent, true).apply {
                buttonAddPayment.setOnClickListener { showAddPaymentDialog() }
            }
            Section.ORDERS -> addNote(R.string.my_orders_empty)
            Section.FAVORITES -> setupFavorites()
            Section.SECURITY -> security = SectionMySecurityBinding.inflate(layoutInflater, parent, true).apply {
                buttonChangePassword.setOnClickListener { showChangePasswordDialog() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 로그아웃된 상태면 닫기
        if (!AuthManager.isLoggedIn()) {
            finish()
            return
        }
        when (section) {
            Section.INFO, Section.ADDRESS -> UserRepository.loadMyProfile { showProfile(it) }
            Section.PAYMENT -> loadPayments()
            Section.SECURITY -> showSecurity()
            Section.FAVORITES -> {
                FavoriteManager.addListener(favoriteListener)
                showFavorites()
            }
            else -> Unit
        }
    }

    override fun onStop() {
        super.onStop()
        FavoriteManager.removeListener(favoriteListener)
    }

    /** 주문 기록 · 찜한 메뉴: 아직 준비 중이라 안내 글자만 (다음 단계에서 목록으로 바꿀 예정) */
    private fun addNote(@StringRes text: Int) {
        val note = TextView(this).apply {
            setText(text)
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MyDetailActivity, R.color.bb_muted))
            setLineSpacing(4f, 1f)
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(0, pad, 0, pad)
        }
        binding.container.addView(note)
    }

    // ───────── ♡ 찜한 메뉴 ─────────

    private fun setupFavorites() {
        val f = SectionMyFavoritesBinding.inflate(layoutInflater, binding.container, true)
        favorites = f
        favoriteAdapter = MenuAdapter(
            onItemClick = { item -> MenuListActivity.start(this, item.categoryId) },   // 그 메뉴의 카테고리 목록으로
            onAddClick = { item ->
                CartManager.add(item)
                Toast.makeText(
                    this, getString(R.string.added_to_cart, item.name, CartManager.totalCount()), Toast.LENGTH_SHORT
                ).show()
            }
        )
        f.recyclerFavorites.layoutManager = LinearLayoutManager(this)
        f.recyclerFavorites.adapter = favoriteAdapter
        f.buttonGoMenu.setOnClickListener {
            MainActivity.openTab(this, MainActivity.TAB_HOME)
            finish()
        }
    }

    private fun showFavorites() {
        val f = favorites ?: return
        val items = FavoriteManager.favoriteItems()
        favoriteAdapter?.submitList(items)
        favoriteAdapter?.notifyDataSetChanged()   // ♡ 모양도 새로
        f.textFavCount.text = getString(R.string.fav_count, items.size)
        f.textFavCount.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        f.emptyFavorites.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    // ───────── 👤 내 정보 / 📍 배달 주소 ─────────

    private fun showProfile(loaded: UserProfile?) {
        if (isFinishing || isDestroyed) return
        profile = loaded
        val empty = getString(R.string.my_empty_value)
        val email = loaded?.email?.takeIf { it.isNotBlank() } ?: AuthManager.currentEmail().orEmpty()

        info?.apply {
            textInfoName.text = loaded?.name?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
            textInfoEmail.text = email
            textInfoPhone.text = loaded?.phone?.takeIf { it.isNotBlank() } ?: empty
        }
        address?.apply {
            val addr = loaded?.address?.takeIf { it.isNotBlank() } ?: empty
            val zip = loaded?.zipCode.orEmpty()
            textInfoAddress.text = if (zip.isNotBlank() && addr != empty) "[$zip] $addr" else addr
            textInfoAddressDetail.text = loaded?.addressDetail?.takeIf { it.isNotBlank() } ?: empty
        }
    }

    private fun reloadProfile() = UserRepository.loadMyProfile { showProfile(it) }

    /** 이름 수정 (입력 칸 1개만 사용) */
    private fun showEditNameDialog() {
        val d = DialogTwoFieldsBinding.inflate(layoutInflater)
        d.layoutFirst.hint = getString(R.string.my_label_name)
        d.editFirst.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        d.editFirst.setText(profile?.name.orEmpty())
        d.layoutSecond.visibility = View.GONE

        showTwoFieldDialog(R.string.my_edit_name, d) { first, _ -> mapOf("name" to first) }
    }

    /** 휴대폰 번호 변경: 새 번호로 인증을 마치면 저장하고 창이 닫힌다 */
    private fun showEditPhoneDialog() {
        val v = LayoutPhoneVerifyBinding.inflate(layoutInflater)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this).apply {
            setPadding(pad, pad / 3, pad, 0)
            addView(v.root)
        }

        lateinit var verifier: PhoneVerifier
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.my_edit_phone)
            .setMessage(R.string.my_edit_phone_desc)
            .setView(box)
            .setNegativeButton(R.string.auth_close, null)
            .setOnDismissListener { verifier.release() }
            .create()

        verifier = PhoneVerifier(
            activity = this,
            v = v,
            onVerified = { phone -> saveNewPhone(phone) { dialog.dismiss() } }
        )
        dialog.show()
    }

    /**
     * 새 휴대폰 번호 저장. 번호가 로그인 아이디이기도 해서
     * 다른 회원이 쓰는 번호인지 확인 → 회원 정보 저장 → 로그인용 목록(phoneIndex)도 새 번호로 바꾼다
     */
    private fun saveNewPhone(phone: String, onFinished: () -> Unit) {
        val uid = AuthManager.currentUid() ?: return onFinished()
        val email = AuthManager.currentEmail().orEmpty()
        val oldPhone = profile?.phone.orEmpty()

        PhoneIndex.lookup(phone) { existing, failed ->
            if (failed || (existing != null && existing.uid != uid)) {
                toast(if (failed) R.string.my_save_failed else R.string.my_phone_taken)
                onFinished()
                return@lookup
            }
            UserRepository.updateMyFields(mapOf("phone" to phone)) { ok ->
                if (ok) {
                    if (PhoneIndex.key(oldPhone) != PhoneIndex.key(phone)) PhoneIndex.release(oldPhone)
                    PhoneIndex.claim(phone, uid, email)
                    reloadProfile()
                }
                toast(if (ok) R.string.my_saved else R.string.my_save_failed)
                onFinished()
            }
        }
    }

    /** 주소 검색 결과 + 상세 주소 입력 → 저장 */
    private fun showAddressDetailDialog(result: AddressResult) {
        val d = DialogTwoFieldsBinding.inflate(layoutInflater)
        d.layoutFirst.hint = getString(R.string.addr_zip, result.zipCode)
        d.editFirst.setText(result.address)
        d.editFirst.isEnabled = false          // 검색한 주소는 그대로
        d.editFirst.maxLines = 3
        d.layoutSecond.hint = getString(R.string.my_label_address_detail)
        d.editSecond.inputType = InputType.TYPE_CLASS_TEXT
        // 같은 주소를 다시 고른 경우에만 예전 상세 주소 채워 두기
        if (profile?.address == result.address) d.editSecond.setText(profile?.addressDetail.orEmpty())
        d.editSecond.requestFocus()

        showTwoFieldDialog(R.string.my_edit_address, d) { _, second ->
            mapOf("zipCode" to result.zipCode, "address" to result.address, "addressDetail" to second)
        }
    }

    /** 입력 칸 2개짜리 창을 띄우고, [저장]을 누르면 Firebase users/{uid} 에 그 값만 저장 */
    private fun showTwoFieldDialog(
        title: Int,
        d: DialogTwoFieldsBinding,
        toFields: (first: String, second: String) -> Map<String, Any>
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(d.root)
            .setNegativeButton(R.string.cart_cancel, null)
            .setPositiveButton(R.string.my_save) { _, _ ->
                val first = d.editFirst.text?.toString()?.trim().orEmpty()
                val second = d.editSecond.text?.toString()?.trim().orEmpty()
                UserRepository.updateMyFields(toFields(first, second)) { ok ->
                    toast(if (ok) R.string.my_saved else R.string.my_save_failed)
                    if (ok) reloadProfile()
                }
            }
            .show()
    }

    // ───────── 💳 결제 수단 ─────────

    private fun loadPayments() {
        PaymentRepository.loadMyPayments { showPayments(it) }
    }

    private fun showPayments(list: List<PaymentMethod>) {
        val p = payment ?: return
        if (isFinishing || isDestroyed) return
        p.paymentList.removeAllViews()
        p.textNoPayment.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        p.textPaymentTip.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE

        list.forEach { method ->
            val item = ItemPaymentMethodBinding.inflate(layoutInflater, p.paymentList, false)
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
            p.paymentList.addView(item.root)
        }
    }

    private fun confirmDeletePayment(method: PaymentMethod) {
        MaterialAlertDialogBuilder(this)
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

        val dialog = MaterialAlertDialogBuilder(this)
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

    // ───────── 🔒 계정 보안 ─────────

    /** Google 로만 가입한 계정은 비밀번호가 없어서 변경 버튼을 숨김 */
    private fun showSecurity() {
        val s = security ?: return
        val hasPassword = AuthManager.hasPassword()
        s.textSecurityDesc.setText(if (hasPassword) R.string.my_security_desc else R.string.my_security_google)
        s.buttonChangePassword.visibility = if (hasPassword) View.VISIBLE else View.GONE
    }

    private fun showChangePasswordDialog() {
        val d = DialogChangePasswordBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.my_change_password)
            .setView(d.root)
            .setNegativeButton(R.string.cart_cancel, null)
            .setPositiveButton(R.string.pw_change, null)   // 아래에서 직접 처리 (틀리면 창이 안 닫히게)
            .show()

        val changeButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        changeButton.setOnClickListener {
            val current = d.editCurrentPassword.text?.toString().orEmpty()
            val newPw = d.editNewPassword.text?.toString().orEmpty()
            val confirm = d.editNewPasswordConfirm.text?.toString().orEmpty()

            d.layoutCurrentPassword.error = null
            d.layoutNewPassword.error = null
            d.layoutNewPasswordConfirm.error = null
            when {
                current.isEmpty() -> d.layoutCurrentPassword.error = getString(R.string.auth_err_required)
                newPw.length < 6 -> d.layoutNewPassword.error = getString(R.string.auth_err_password)
                newPw == current -> d.layoutNewPassword.error = getString(R.string.pw_err_same)
                confirm != newPw -> d.layoutNewPasswordConfirm.error = getString(R.string.auth_err_password_match)
                else -> {
                    changeButton.isEnabled = false
                    AuthManager.changePassword(current, newPw) { error ->
                        changeButton.isEnabled = true
                        if (error == null) {
                            dialog.dismiss()
                            toast(R.string.pw_changed)
                        } else if (error.contains("현재 비밀번호")) {
                            d.layoutCurrentPassword.error = error
                        } else {
                            d.layoutNewPassword.error = error
                        }
                    }
                }
            }
        }
    }

    private fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val EXTRA_SECTION = "section"

        fun intent(context: Context, section: Section): Intent =
            Intent(context, MyDetailActivity::class.java).putExtra(EXTRA_SECTION, section.ordinal)
    }
}