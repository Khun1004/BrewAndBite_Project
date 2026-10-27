package com.example.brewandbite.cart

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.brewandbite.R
import com.example.brewandbite.data.toWon
import com.example.brewandbite.databinding.SheetPaymentBinding
import com.example.brewandbite.order.OrderManager
import com.example.brewandbite.reward.Coupon
import com.example.brewandbite.reward.RewardManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Calendar

/**
 * 모의 결제 화면. 실제 결제는 하지 않고, 입력한 카드 정보는 저장/전송하지 않는다.
 *
 *  ① 결제 수단 + 🎟 쿠폰 선택 (쿠폰은 RewardManager 에서 쓸 수 있는 것만)
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

        // 카드 선택 시에만 입력 폼 표시
        binding.groupMethod.setOnCheckedChangeListener { _, checkedId ->
            binding.layoutCardForm.visibility =
                if (checkedId == binding.rbCard.id) View.VISIBLE else View.GONE
        }

        binding.editCardNumber.addTextChangedListener(formatWatcher(16, 4, " "))
        binding.editExpiry.addTextChangedListener(formatWatcher(4, 2, "/"))

        binding.rowCoupon.setOnClickListener { showCouponPicker() }
        binding.buttonPay.setOnClickListener { onPayClicked() }
        binding.buttonDone.setOnClickListener { dismiss() }

        if (paid) showSuccess() else showPrice()
    }

    override fun onStart() {
        super.onStart()
        RewardManager.addListener(rewardListener)
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

    // ───────── 결제 ─────────

    private fun onPayClicked() {
        if (binding.rbCard.isChecked && !validateCard()) return

        // 처리 중에는 닫히지 않게
        isCancelable = false
        binding.layoutSelect.visibility = View.GONE
        binding.layoutProcessing.visibility = View.VISIBLE

        // 가짜 승인 대기 (1.8초)
        handler.postDelayed({
            if (_binding == null) return@postDelayed
            completeOrder()
        }, 1800)
    }

    /** 주문 저장 → 쿠폰 사용 → 스탬프 적립 → 장바구니 비우기 (순서 중요) */
    private fun completeOrder() {
        val items = CartManager.getItems()
        val coupon = selectedCoupon()
        val discount = discount()
        val stamps = RewardManager.stampsFor(items)

        OrderManager.place(
            items = items,
            isDelivery = CartManager.fulfillment == CartManager.Fulfillment.DELIVERY,
            subtotal = CartManager.totalPrice(),
            deliveryFee = CartManager.deliveryFee(),
            discount = discount,
            couponTitle = coupon?.title.orEmpty(),
            stampsEarned = stamps
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

    // ───────── 입력 검사 ─────────

    private fun validateCard(): Boolean {
        var ok = true

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
    }
}