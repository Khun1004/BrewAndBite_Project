package com.example.brewandbite.cart

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.brewandbite.R
import com.example.brewandbite.auth.UserRepository
import com.example.brewandbite.data.toWon
import com.example.brewandbite.databinding.SheetPaymentBinding
import com.example.brewandbite.order.OrderManager
import com.example.brewandbite.reward.Coupon
import com.example.brewandbite.reward.RewardManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar

/**
 * 모의 결제 화면. 실제 결제는 하지 않는다.
 * 카드는 카드사 · 브랜드 · 끝 4자리 · 유효기간만 저장하고, 전체 번호와 CVC는 저장하지 않는다.
 *
 *  ① 결제 수단 + 🎟 쿠폰 + 저장된 카드 선택 + 배달 주소 · 요청사항
 *  ② 처리 중 (1.8초)
 *  ③ 완료: 주문 저장(OrderManager) → 쿠폰 사용 처리 → 스탬프 적립 → 장바구니 비우기
 *
 * 결제 성공 후 닫히면 parent 에 RESULT_KEY 로 알린다 (CartFragment 가 Order 탭으로 이동).
 */
class PaymentBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetPaymentBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private var paid = false

    /** 결제 처리 중이면 true (버튼 두 번 눌러도 주문이 한 번만 생기게) */
    private var processing = false

    enum class Method { CARD, KAKAO, NAVER, TOSS }
    private var method = Method.CARD

    private val noteOptions = listOf(
        "문 앞에 놔주세요", "벨 누르지 말아주세요", "도착하면 전화해 주세요", "수저·포크는 필요 없어요"
    )
    private val noteChips = mutableListOf<Chip>()

    /** 고른 저장 카드 ID (null = 새 카드 입력) */
    private var selectedCardId: String? = null

    /** 새 카드에서 고른 카드사 (null = 아직 안 고름) */
    private var selectedIssuer: String? = null

    /** 해외 발행 카드면 true */
    private var foreignCard = false

    /** 고른 쿠폰 ID (없으면 null) */
    private var couponId: String? = null

    /** 결제 후 보여 줄 스탬프 수 (-1 = 아직 적립 중) */
    private var stampsEarned = -1

    /** 쿠폰·스탬프가 바뀌면(서버에서 늦게 불러온 경우 등) 금액 다시 그리기 */
    private val rewardListener = RewardManager.RewardListener { if (!paid) showPrice() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        paid = savedInstanceState?.getBoolean(KEY_PAID) ?: false
        couponId = savedInstanceState?.getString(KEY_COUPON)
        stampsEarned = savedInstanceState?.getInt(KEY_STAMPS, -1) ?: -1
        method = runCatching { Method.valueOf(savedInstanceState?.getString(KEY_METHOD) ?: "") }
            .getOrDefault(Method.CARD)
        selectedIssuer = savedInstanceState?.getString(KEY_ISSUER)
        foreignCard = savedInstanceState?.getBoolean(KEY_FOREIGN) ?: false

        SavedCardManager.init(requireContext())
        DeliveryAddress.init(requireContext())

        // 저장된 배달 주소가 없으면 회원 정보의 주소를 가져온다
        if (DeliveryAddress.get() == null) {
            UserRepository.loadMyProfile { profile ->
                if (_binding == null || profile == null) return@loadMyProfile
                if (profile.address.isNotBlank()) {
                    DeliveryAddress.save(DeliveryAddress.Address(profile.address, profile.addressDetail))
                    renderAddress()
                }
            }
        }

        selectedCardId = if (savedInstanceState != null) savedInstanceState.getString(KEY_CARD)
        else SavedCardManager.all().firstOrNull()?.id

        // 결제 수단 버튼
        binding.btnCard.setOnClickListener { method = Method.CARD; renderMethods() }
        binding.btnKakao.setOnClickListener { method = Method.KAKAO; renderMethods() }
        binding.btnNaver.setOnClickListener { method = Method.NAVER; renderMethods() }
        binding.btnToss.setOnClickListener { method = Method.TOSS; renderMethods() }

        // 국내 / 해외 발행 + 카드사
        binding.rowIssuer.setOnClickListener { showIssuerPicker() }
        binding.toggleOrigin.check(if (foreignCard) R.id.btnForeign else R.id.btnDomestic)
        binding.toggleOrigin.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            foreignCard = checkedId == R.id.btnForeign
            if (foreignCard) {
                selectedIssuer = null
                binding.textIssuer.text = "카드사를 선택해 주세요"
            }
            renderOrigin()
            updatePreview()
        }
        selectedIssuer?.let { binding.textIssuer.text = it }
        renderOrigin()

        // 입력 서식 (먼저) + 카드 그림 갱신 (나중)
        binding.editCardNumber.addTextChangedListener(formatWatcher(16, 4, " "))
        binding.editExpiry.addTextChangedListener(formatWatcher(4, 2, "/"))
        binding.editCardNumber.addTextChangedListener(afterChanged { updatePreview() })
        binding.editExpiry.addTextChangedListener(afterChanged { updatePreview() })

        binding.rowCoupon.setOnClickListener { showCouponPicker() }
        binding.rowAddress.setOnClickListener { showAddressDialog() }
        binding.buttonPay.setOnClickListener { onPayClicked() }
        binding.buttonDone.setOnClickListener { dismiss() }

        // 주소 · 요청사항은 배달일 때만
        binding.layoutDelivery.visibility =
            if (CartManager.fulfillment == CartManager.Fulfillment.DELIVERY) View.VISIBLE else View.GONE
        setupNoteChips()

        renderMethods()
        renderAddress()
        renderCards()
        if (paid) showSuccess() else showPrice()
    }

    override fun onStart() {
        super.onStart()
        RewardManager.addListener(rewardListener)
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onStop() {
        super.onStop()
        RewardManager.removeListener(rewardListener)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_PAID, paid)
        outState.putString(KEY_COUPON, couponId)
        outState.putInt(KEY_STAMPS, stampsEarned)
        outState.putString(KEY_CARD, selectedCardId)
        outState.putString(KEY_METHOD, method.name)
        outState.putString(KEY_ISSUER, selectedIssuer)
        outState.putBoolean(KEY_FOREIGN, foreignCard)
    }

    // ───────── 금액 · 쿠폰 ─────────

    /** 지금 고른 쿠폰 (이미 썼거나 조건이 안 맞게 됐으면 null) */
    private fun selectedCoupon(): Coupon? {
        val id = couponId ?: return null
        return RewardManager.usableCoupons(CartManager.totalPrice()).find { it.id == id }
    }

    private fun discount(): Int =
        selectedCoupon()?.let { RewardManager.discount(it, CartManager.totalPrice(), CartManager.getItems()) } ?: 0

    private fun payAmount(): Int = (CartManager.payableTotal() - discount()).coerceAtLeast(0)

    /** 결제 금액 · 금액 내역 · 쿠폰 칸 · 버튼 글자 */
    private fun showPrice() {
        val b = _binding ?: return
        val subtotal = CartManager.totalPrice()
        val fee = CartManager.deliveryFee()
        val coupon = selectedCoupon()
        val discount = discount()
        val amount = payAmount()

        b.textAmount.text = amount.toWon()
        b.buttonPay.text = getString(R.string.cart_pay_button, amount.toWon())

        b.textPriceDetail.text = buildList {
            add(getString(R.string.pay_detail_subtotal, subtotal.toWon()))
            if (fee > 0) add(getString(R.string.pay_detail_fee, fee.toWon()))
            if (discount > 0) add(getString(R.string.pay_detail_coupon, discount.toWon()))
        }.joinToString(" · ")

        val usable = RewardManager.usableCoupons(subtotal)
        b.textCouponValue.text = when {
            coupon != null -> getString(R.string.pay_coupon_applied, coupon.title, discount.toWon())
            usable.isNotEmpty() -> getString(R.string.pay_coupon_choose, usable.size)
            else -> getString(R.string.pay_coupon_none)
        }
        b.rowCoupon.isEnabled = usable.isNotEmpty()
        b.rowCoupon.alpha = if (usable.isNotEmpty()) 1f else 0.6f
    }

    /** 쓸 수 있는 쿠폰 목록 (맨 위 '쿠폰 사용 안 함') */
    private fun showCouponPicker() {
        val subtotal = CartManager.totalPrice()
        val items = CartManager.getItems()
        val coupons = RewardManager.usableCoupons(subtotal)
        if (coupons.isEmpty()) return

        val labels = listOf(getString(R.string.pay_coupon_not_use)) + coupons.map {
            getString(R.string.pay_coupon_item, it.title, RewardManager.discount(it, subtotal, items).toWon(), it.expiryText)
        }
        val checked = coupons.indexOfFirst { it.id == couponId } + 1   // 없으면 0 = 사용 안 함
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pay_coupon_title)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                couponId = if (which == 0) null else coupons[which - 1].id
                showPrice()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cart_cancel, null)
            .show()
    }

    // ───────── 저장된 카드 · 미리보기 ─────────

    /** 저장된 카드 목록 + '새 카드 추가' 줄을 다시 그린다 */
    private fun renderCards() {
        val b = _binding ?: return
        val cards = SavedCardManager.all()
        if (selectedCardId != null && cards.none { it.id == selectedCardId }) selectedCardId = null

        fun addRow(name: String, expiry: String, checked: Boolean, onDelete: (() -> Unit)? = null): View {
            val ctx = requireContext()
            val d = resources.displayMetrics.density
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = (52 * d).toInt()
                setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
                setBackgroundResource(R.drawable.bg_coupon_dday)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (8 * d).toInt() }
            }
            val radio = RadioButton(ctx).apply {
                isChecked = checked
                isClickable = false
                isFocusable = false
                buttonTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.bb_brown))
            }
            val nameView = TextView(ctx).apply {
                text = name
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.bb_espresso))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = (8 * d).toInt() }
            }
            val expiryView = TextView(ctx).apply {
                text = expiry
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.bb_muted))
            }
            row.addView(radio)
            row.addView(nameView)
            row.addView(expiryView)
            if (onDelete != null) {
                row.addView(TextView(ctx).apply {
                    text = "삭제"
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(0xFFC62828.toInt())
                    setPadding((14 * d).toInt(), (14 * d).toInt(), 0, (14 * d).toInt())
                    setOnClickListener { onDelete() }
                })
            }
            b.layoutSavedCards.addView(row)
            return row
        }

        b.layoutSavedCards.removeAllViews()
        cards.forEach { card ->
            val expiryText = if (card.foreign) "해외 · ${card.expiry}" else card.expiry
            val row = addRow(card.displayName(), expiryText, card.id == selectedCardId) {
                confirmDeleteCard(card)
            }
            row.setOnClickListener { selectedCardId = card.id; renderCards() }
        }
        if (cards.isNotEmpty()) {
            val add = addRow(getString(R.string.pay_card_add), "", selectedCardId == null)
            add.setOnClickListener { selectedCardId = null; renderCards() }
        }
        b.layoutCardForm.visibility = if (selectedCardId == null) View.VISIBLE else View.GONE
        updatePreview()
    }

    /** 카드 그림: 저장 카드를 골랐으면 그 카드, 아니면 입력 중인 내용 */
    private fun updatePreview() {
        val b = _binding ?: return
        val saved = SavedCardManager.all().find { it.id == selectedCardId }
        val brand: String
        val issuerName: String
        if (saved != null) {
            brand = saved.brand
            issuerName = saved.issuer
            b.textCardNumberPreview.text = "••••  ••••  ••••  ${saved.last4}"
            b.textCardExpiryPreview.text = saved.expiry.ifBlank { "MM/YY" }
        } else {
            val digits = b.editCardNumber.text.toString().filter(Char::isDigit)
            brand = SavedCardManager.brandOf(digits)
            issuerName = if (foreignCard) "" else selectedIssuer.orEmpty()
            b.textCardNumberPreview.text = digits.padEnd(16, '•').chunked(4).joinToString("  ")
            b.textCardExpiryPreview.text = b.editExpiry.text.toString().ifBlank { "MM/YY" }
        }
        b.textCardBrand.text = issuerName.ifBlank { brand }
        paintCard(brand, SavedCardManager.issuerOf(issuerName))
    }

    /** 카드사(없으면 브랜드)마다 카드 색을 다르게 */
    private fun paintCard(brand: String, issuer: SavedCardManager.Issuer? = null) {
        val ctx = requireContext()
        val colors = if (issuer != null) intArrayOf(issuer.dark, issuer.light) else when (brand) {
            "VISA" -> intArrayOf(0xFF1A1F71.toInt(), 0xFF3B5BDB.toInt())
            "MASTER" -> intArrayOf(0xFF212121.toInt(), 0xFFC62828.toInt())
            "AMEX" -> intArrayOf(0xFF00695C.toInt(), 0xFF26A69A.toInt())
            "JCB" -> intArrayOf(0xFF1B5E20.toInt(), 0xFF43A047.toInt())
            else -> intArrayOf(
                ContextCompat.getColor(ctx, R.color.bb_espresso),
                ContextCompat.getColor(ctx, R.color.bb_brown)
            )
        }
        binding.cardPreview.background =
            GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
                cornerRadius = 18 * resources.displayMetrics.density
            }
    }

    private fun confirmDeleteCard(card: SavedCardManager.SavedCard) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("저장된 카드 삭제")
            .setMessage("${card.displayName()} 카드를 삭제할까요?")
            .setNegativeButton(R.string.cart_cancel, null)
            .setPositiveButton("삭제") { _, _ ->
                SavedCardManager.remove(card.id)
                renderCards()
            }
            .show()
    }

    private fun showIssuerPicker() {
        val names = SavedCardManager.ISSUERS.map { it.name }.toTypedArray()
        val checked = names.indexOf(selectedIssuer)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("카드사 선택")
            .setSingleChoiceItems(names, checked) { dialog, which ->
                selectedIssuer = names[which]
                binding.textIssuer.text = names[which]
                updatePreview()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cart_cancel, null)
            .show()
    }

    /** 해외 발행이면 카드사 줄을 숨기고 안내 문구 표시 */
    private fun renderOrigin() {
        val b = _binding ?: return
        b.rowIssuer.visibility = if (foreignCard) View.GONE else View.VISIBLE
        b.textForeignNote.visibility = if (foreignCard) View.VISIBLE else View.GONE
    }

    // ───────── 결제 ─────────

    private fun onPayClicked() {
        if (processing) return                       // 중복 클릭 방지
        val isDelivery = CartManager.fulfillment == CartManager.Fulfillment.DELIVERY
        if (isDelivery && DeliveryAddress.get() == null) {   // 배달 주소가 없으면 먼저 입력
            showAddressDialog()
            return
        }
        val useCard = method == Method.CARD
        val saved = if (useCard) SavedCardManager.all().find { it.id == selectedCardId } else null
        if (useCard && saved == null && !validateCard()) return

        val digits = saved?.last4 ?: binding.editCardNumber.text.toString().filter(Char::isDigit)
        val label = saved?.displayName() ?: methodLabel(digits)
        val willFail = useCard && digits.endsWith("0000")
        val newCard = if (useCard && saved == null && binding.checkSaveCard.isChecked)
            SavedCardManager.SavedCard(
                brand = SavedCardManager.brandOf(digits),
                last4 = digits.takeLast(4),
                expiry = binding.editExpiry.text.toString(),
                issuer = if (foreignCard) "" else selectedIssuer.orEmpty(),
                foreign = foreignCard
            ) else null

        startPayment(label, willFail, newCard)
    }

    private fun startPayment(label: String, willFail: Boolean, newCard: SavedCardManager.SavedCard?) {
        if (_binding == null || processing) return
        processing = true
        binding.textPayError.visibility = View.GONE
        isCancelable = false                         // 처리 중에는 닫히지 않게
        binding.layoutSelect.visibility = View.GONE
        binding.layoutProcessing.visibility = View.VISIBLE

        // 가짜 승인 대기 (1.8초)
        handler.postDelayed({
            if (_binding == null) return@postDelayed
            if (willFail) {
                showFailure()
            } else {
                completeOrder(label)
                newCard?.let { SavedCardManager.add(it.brand, it.last4, it.expiry, it.issuer, it.foreign) }
            }
        }, 1800)
    }

    /** 카드 번호가 0000 으로 끝나면 실패 연습용 */
    private fun showFailure() {
        processing = false
        isCancelable = true
        binding.layoutProcessing.visibility = View.GONE
        binding.layoutSelect.visibility = View.VISIBLE
        binding.textPayError.visibility = View.VISIBLE
    }

    private fun methodLabel(cardDigits: String): String = when (method) {
        Method.CARD ->
            if (foreignCard) "${SavedCardManager.brandOf(cardDigits)} ****${cardDigits.takeLast(4)} (해외발행)"
            else "${selectedIssuer ?: SavedCardManager.brandOf(cardDigits)} ****${cardDigits.takeLast(4)}"
        Method.KAKAO -> "카카오페이"
        Method.NAVER -> "네이버페이"
        Method.TOSS -> "토스페이"
    }

    /** 주문 저장 → 쿠폰 사용 → 스탬프 적립 → 장바구니 비우기 (순서 중요) */
    private fun completeOrder(label: String) {
        val items = CartManager.getItems()
        val coupon = selectedCoupon()
        val discount = discount()
        val stamps = RewardManager.stampsFor(items)
        val isDelivery = CartManager.fulfillment == CartManager.Fulfillment.DELIVERY
        val note = requestNote()
        val address = if (isDelivery) DeliveryAddress.get()?.full().orEmpty() else ""

        OrderManager.place(
            items = items,
            isDelivery = isDelivery,
            subtotal = CartManager.totalPrice(),
            deliveryFee = CartManager.deliveryFee(),
            discount = discount,
            couponTitle = coupon?.title.orEmpty(),
            stampsEarned = stamps,
            paymentMethod = label,
            requestNote = note,
            address = address
        )
        if (coupon != null) RewardManager.useCoupon(coupon.id)
        RewardManager.addStamps(stamps) { newCoupons, _ ->
            stampsEarned = stamps
            if (_binding != null) showStampResult(newCoupons)
        }
        paid = true
        CartManager.clear()
        showSuccess()
    }

    private fun showSuccess() {
        isCancelable = true
        val b = binding
        b.layoutSelect.visibility = View.GONE
        b.layoutProcessing.visibility = View.GONE
        b.layoutSuccess.visibility = View.VISIBLE
        val order = OrderManager.current()
        b.textPaidAmount.text = (order?.total ?: 0).toWon()
        b.textOrderNo.text = getString(R.string.pay_order_no, order?.orderNo.orEmpty())
        if (stampsEarned >= 0) showStampResult(0)
    }

    /** ☕ 스탬프 N개 적립! (7 / 10) · 10개를 채웠으면 쿠폰 안내 */
    private fun showStampResult(newCoupons: Int) {
        val b = _binding ?: return
        val count = OrderManager.current()?.stampsEarned ?: 0
        if (count <= 0) {
            b.textStampEarned.visibility = View.GONE
            return
        }
        b.textStampEarned.visibility = View.VISIBLE
        b.textStampEarned.text =
            if (newCoupons > 0) getString(R.string.pay_stamp_coupon, count)
            else getString(R.string.pay_stamp_earned, count, RewardManager.stamps(), RewardManager.STAMPS_PER_COUPON)
    }

    // ───────── 결제 수단 · 주소 · 요청사항 ─────────

    /** 고른 수단만 또렷하게 (테두리 + 불투명), 카드 영역은 카드일 때만 */
    private fun renderMethods() {
        val b = _binding ?: return
        val d = resources.displayMetrics.density
        mapOf(
            Method.CARD to b.btnCard, Method.KAKAO to b.btnKakao,
            Method.NAVER to b.btnNaver, Method.TOSS to b.btnToss
        ).forEach { (m, button) ->
            val on = m == method
            button.alpha = if (on) 1f else 0.45f
            button.strokeWidth = if (on) (3 * d).toInt() else 0
        }
        b.layoutCardArea.visibility = if (method == Method.CARD) View.VISIBLE else View.GONE
    }

    private fun setupNoteChips() {
        val ctx = requireContext()
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        fun colors(on: Int, off: Int) = ColorStateList(
            states, intArrayOf(ContextCompat.getColor(ctx, on), ContextCompat.getColor(ctx, off))
        )
        noteChips.clear()
        binding.chipGroupNote.removeAllViews()
        (noteOptions + "직접 입력").forEach { label ->
            val chip = Chip(ctx).apply {
                text = label
                isCheckable = true
                isCheckedIconVisible = false
                chipBackgroundColor = colors(R.color.bb_espresso, R.color.white)
                setTextColor(colors(R.color.bb_cream, R.color.bb_brown))
                chipStrokeColor = colors(R.color.bb_espresso, R.color.bb_divider)
                chipStrokeWidth = resources.displayMetrics.density
            }
            binding.chipGroupNote.addView(chip)
            noteChips += chip
        }
        // '직접 입력'을 고르면 입력 칸 표시
        binding.chipGroupNote.setOnCheckedStateChangeListener { _, _ ->
            val i = noteChips.indexOfFirst { it.isChecked }
            binding.inputNoteLayout.visibility = if (i == noteOptions.size) View.VISIBLE else View.GONE
        }
    }

    /** 고른 요청사항 (포장이면 빈 글자) */
    private fun requestNote(): String {
        if (CartManager.fulfillment != CartManager.Fulfillment.DELIVERY) return ""
        val i = noteChips.indexOfFirst { it.isChecked }
        return when {
            i < 0 -> ""
            i < noteOptions.size -> noteOptions[i]
            else -> binding.editRequestNote.text.toString().trim()
        }
    }

    private fun renderAddress() {
        val b = _binding ?: return
        b.textAddress.text = DeliveryAddress.get()?.full() ?: "배달 주소를 입력해 주세요"
    }

    private fun showAddressDialog() {
        val ctx = requireContext()
        val pad = (24 * resources.displayMetrics.density).toInt()
        val current = DeliveryAddress.get()
        val base = TextInputEditText(ctx).apply {
            hint = "주소 (예: 서울시 강남구 테헤란로 123)"
            setText(current?.base.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val detail = TextInputEditText(ctx).apply {
            hint = "상세주소 (동 · 호수)"
            setText(current?.detail.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(base)
            addView(detail)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("배달 주소")
            .setView(box)
            .setNegativeButton(R.string.cart_cancel, null)
            .setPositiveButton("저장") { _, _ ->
                val address = base.text.toString().trim()
                if (address.isBlank()) {
                    Toast.makeText(ctx, "주소를 입력해 주세요", Toast.LENGTH_SHORT).show()
                } else {
                    val detailText = detail.text.toString().trim()
                    DeliveryAddress.save(DeliveryAddress.Address(address, detailText))
                    UserRepository.updateMyFields(
                        mapOf("address" to address, "addressDetail" to detailText)
                    )
                    renderAddress()
                }
            }
            .show()
    }

    // ───────── 입력 검사 ─────────

    private fun validateCard(): Boolean {
        var ok = true

        // 국내 발행 카드만 카드사를 꼭 골라야 한다
        if (!foreignCard && selectedIssuer == null) {
            Toast.makeText(requireContext(), "카드사를 선택해 주세요", Toast.LENGTH_SHORT).show()
            ok = false
        }

        val number = binding.editCardNumber.text.toString().filter(Char::isDigit)
        binding.inputCardNumberLayout.error =
            if (number.length != 16) "카드 번호 16자리를 입력해 주세요".also { ok = false } else null

        val expiry = binding.editExpiry.text.toString().filter(Char::isDigit)
        binding.inputExpiryLayout.error =
            if (!isExpiryValid(expiry)) "유효기간을 확인해 주세요".also { ok = false } else null

        val cvc = binding.editCvc.text.toString()
        binding.inputCvcLayout.error =
            if (cvc.length != 3) "CVC 3자리를 입력해 주세요".also { ok = false } else null

        return ok
    }

    private fun isExpiryValid(digits: String): Boolean {
        if (digits.length != 4) return false
        val month = digits.substring(0, 2).toInt()
        val year = 2000 + digits.substring(2, 4).toInt()
        if (month !in 1..12) return false
        val now = Calendar.getInstance()
        val nowYear = now.get(Calendar.YEAR)
        val nowMonth = now.get(Calendar.MONTH) + 1
        return year > nowYear || (year == nowYear && month >= nowMonth)
    }

    /** 숫자만 maxDigits개까지 받고, groupSize 자리마다 separator를 넣는다 */
    private fun formatWatcher(maxDigits: Int, groupSize: Int, separator: String) = object : TextWatcher {
        private var editing = false
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (editing || s == null) return
            editing = true
            val digits = s.toString().filter(Char::isDigit).take(maxDigits)
            s.replace(0, s.length, digits.chunked(groupSize).joinToString(separator))
            editing = false
        }
    }

    private fun afterChanged(block: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) = block()
    }

    // ───────── 닫힐 때 ─────────

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        if (paid) parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "payment_sheet"
        const val RESULT_KEY = "payment_result"
        private const val KEY_PAID = "paid"
        private const val KEY_COUPON = "coupon"
        private const val KEY_STAMPS = "stamps"
        private const val KEY_CARD = "card"
        private const val KEY_METHOD = "method"
        private const val KEY_ISSUER = "issuer"
        private const val KEY_FOREIGN = "foreign"
    }
}