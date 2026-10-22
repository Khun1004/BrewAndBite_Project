package com.example.brewandbite.cart

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.brewandbite.data.toWon
import com.example.brewandbite.databinding.SheetPaymentBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.Calendar

/**
 * 모의 결제 화면. 실제 결제는 하지 않고, 입력한 카드 정보는 저장/전송하지 않는다.
 * 결제 성공 후 닫히면 parent에 RESULT_KEY 로 알린다.
 */
class PaymentBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetPaymentBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private var amount = 0
    private var paid = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 장바구니가 비워진 뒤에도 금액이 유지되도록 처음에 저장
        amount = savedInstanceState?.getInt(KEY_AMOUNT) ?: CartManager.payableTotal()
        paid = savedInstanceState?.getBoolean(KEY_PAID) ?: false

        binding.textAmount.text = amount.toWon()
        binding.buttonPay.text = "${amount.toWon()} 결제하기"

        // 카드 선택 시에만 입력 폼 표시
        binding.groupMethod.setOnCheckedChangeListener { _, checkedId ->
            binding.layoutCardForm.visibility =
                if (checkedId == binding.rbCard.id) View.VISIBLE else View.GONE
        }

        binding.editCardNumber.addTextChangedListener(formatWatcher(16, 4, " "))
        binding.editExpiry.addTextChangedListener(formatWatcher(4, 2, "/"))

        binding.buttonPay.setOnClickListener { onPayClicked() }
        binding.buttonDone.setOnClickListener { dismiss() }

        if (paid) showSuccess()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_AMOUNT, amount)
        outState.putBoolean(KEY_PAID, paid)
    }

    private fun onPayClicked() {
        if (binding.rbCard.isChecked && !validateCard()) return

        // 처리 중에는 닫히지 않게
        isCancelable = false
        binding.layoutSelect.visibility = View.GONE
        binding.layoutProcessing.visibility = View.VISIBLE

        // 가짜 승인 대기 (1.8초)
        handler.postDelayed({
            if (_binding == null) return@postDelayed
            paid = true
            CartManager.clear()     // 결제 성공 → 장바구니 비우기
            showSuccess()
        }, 1800)
    }

    private fun showSuccess() {
        isCancelable = true
        binding.layoutSelect.visibility = View.GONE
        binding.layoutProcessing.visibility = View.GONE
        binding.layoutSuccess.visibility = View.VISIBLE
        binding.textPaidAmount.text = amount.toWon()
        val orderNo = savedOrderNo ?: "BB-%tY%<tm%<td-%04d".format(Calendar.getInstance(), (1000..9999).random())
            .also { savedOrderNo = it }
        binding.textOrderNo.text = "주문번호 $orderNo"
    }

    private var savedOrderNo: String? = null

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
        private const val KEY_AMOUNT = "amount"
        private const val KEY_PAID = "paid"
    }
}