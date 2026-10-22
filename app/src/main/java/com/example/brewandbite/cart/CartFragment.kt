package com.example.brewandbite.cart

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.brewandbite.MainActivity
import com.example.brewandbite.R
import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.auth.LoginActivity
import com.example.brewandbite.common.setup
import com.example.brewandbite.data.toWon
import com.example.brewandbite.databinding.FragmentCartBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Cart 탭: 담은 메뉴 목록, 수량 조절, 삭제, 총 금액, 주문하기 */
class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!

    private lateinit var cartAdapter: CartAdapter

    /** 로그인 화면에서 로그인에 성공하고 돌아오면 바로 Order 탭으로 */
    private val loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) showPayment()
    }

    /** 장바구니가 바뀌면 화면 전체를 다시 그린다 */
    private val cartListener = CartManager.CartListener { refresh() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 공통 헤더: MY CART / Cart / 🛒  (부제는 refresh()에서 메뉴 수·총액으로)
        binding.pageHeader.setup(R.string.cart_eyebrow, R.string.header_cart_title, R.drawable.ic_cart)

        cartAdapter = CartAdapter(
            onIncrease = { item -> CartManager.increase(item.key) },
            onDecrease = { item -> CartManager.decrease(item.key) },   // 1개에서 줄이면 삭제됨
            onRemove = { item ->
                CartManager.remove(item.key)
                Toast.makeText(requireContext(), getString(R.string.cart_removed, item.name), Toast.LENGTH_SHORT).show()
            }
        )
        // 결제 완료 후 시트가 닫히면 Home 탭으로 (Order 탭으로 보내려면 TAB_ORDER)
        childFragmentManager.setFragmentResultListener(PaymentBottomSheet.RESULT_KEY, viewLifecycleOwner) { _, _ ->
            mainActivity().showTab(MainActivity.TAB_HOME)
        }
        binding.recyclerCart.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerCart.adapter = cartAdapter

        // 비어 있을 때 '메뉴 보러 가기' → Home 탭
        binding.buttonGoMenu.setOnClickListener { mainActivity().showTab(MainActivity.TAB_HOME) }
        // 헤더 아래 줄의 전체 삭제 버튼
        binding.textClear.setOnClickListener { confirmClear() }
        // 주문하기 → (로그인 안 했으면 로그인 먼저) → Order 탭 (팀원이 작성)
        binding.toggleFulfillment.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            CartManager.setFulfillment(
                if (checkedId == R.id.buttonPickup) CartManager.Fulfillment.PICKUP
                else CartManager.Fulfillment.DELIVERY
            )
        }
        binding.buttonOrder.setOnClickListener {
            if (!CartManager.isMinOrderReached()) return@setOnClickListener
            if (AuthManager.isLoggedIn()) {
                showPayment()
            } else {
                loginLauncher.launch(LoginActivity.intent(requireContext()))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        CartManager.addListener(cartListener)
        refresh()
    }

    override fun onStop() {
        super.onStop()
        CartManager.removeListener(cartListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun mainActivity() = requireActivity() as MainActivity

    /** CartManager의 현재 내용으로 화면을 다시 그린다 */
    private fun refresh() {
        val b = _binding ?: return
        val items = CartManager.getItems()
        val isEmpty = items.isEmpty()

        // 목록이 바뀐 뒤 줄마다 이미지 좌우 배치를 다시 맞춤
        cartAdapter.submitList(items) { cartAdapter.refreshSides() }

        b.emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        b.recyclerCart.visibility = if (isEmpty) View.GONE else View.VISIBLE
        b.summaryBox.visibility = if (isEmpty) View.GONE else View.VISIBLE
        b.cartToolbar.visibility = if (isEmpty) View.GONE else View.VISIBLE
        b.textItemCount.text = getString(R.string.cart_item_count, items.size)

        val subtotal = CartManager.totalPrice().toWon()
        b.pageHeader.textSubtitle.text = if (isEmpty) {
            getString(R.string.cart_empty)
        } else {
            getString(R.string.cart_header_summary, items.size, subtotal)
        }
        b.textTotalCount.text = getString(R.string.cart_count_value, CartManager.totalCount())

// 수령 방법 토글 (이미 같은 상태면 건드리지 않음)
        val targetId = if (CartManager.fulfillment == CartManager.Fulfillment.PICKUP)
            R.id.buttonPickup else R.id.buttonDelivery
        if (b.toggleFulfillment.checkedButtonId != targetId) b.toggleFulfillment.check(targetId)

// 금액
        val isDelivery = CartManager.fulfillment == CartManager.Fulfillment.DELIVERY
        val payable = CartManager.payableTotal().toWon()
        b.textSubtotal.text = subtotal
        b.rowDeliveryFee.visibility = if (isDelivery) View.VISIBLE else View.GONE
        b.textDeliveryFee.text = CartManager.deliveryFee().toWon()
        b.textTotalPrice.text = payable

// 최소 주문 금액 안내 + 결제하기 버튼 활성화
        val reached = CartManager.isMinOrderReached()
        b.textMinOrderHint.visibility = if (reached || isEmpty) View.GONE else View.VISIBLE
        b.textMinOrderHint.text = getString(
            R.string.cart_min_order_hint,
            CartManager.MIN_ORDER_AMOUNT.toWon(),
            CartManager.shortfall().toWon()
        )
        b.buttonOrder.isEnabled = reached
        b.buttonOrder.text = getString(R.string.cart_pay_button, payable)
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cart_clear_title)
            .setMessage(R.string.cart_clear_message)
            .setNegativeButton(R.string.cart_cancel, null)
            .setPositiveButton(R.string.cart_clear_ok) { _, _ -> CartManager.clear() }
            .show()
    }
    private fun showPayment() {
        if (childFragmentManager.findFragmentByTag(PaymentBottomSheet.TAG) != null) return
        PaymentBottomSheet().show(childFragmentManager, PaymentBottomSheet.TAG)
    }
}