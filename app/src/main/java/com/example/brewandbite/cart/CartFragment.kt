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
        if (it.resultCode == Activity.RESULT_OK) mainActivity().showTab(MainActivity.TAB_ORDER)
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
        binding.pageHeader.setup(R.string.cart_eyebrow, R.string.header_cart_title, "🛒")

        cartAdapter = CartAdapter(
            onIncrease = { item -> CartManager.increase(item.menuId) },
            onDecrease = { item -> CartManager.decrease(item.menuId) },   // 1개에서 줄이면 삭제됨
            onRemove = { item ->
                CartManager.remove(item.menuId)
                Toast.makeText(requireContext(), getString(R.string.cart_removed, item.name), Toast.LENGTH_SHORT).show()
            }
        )
        binding.recyclerCart.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerCart.adapter = cartAdapter

        // 비어 있을 때 '메뉴 보러 가기' → Home 탭
        binding.buttonGoMenu.setOnClickListener { mainActivity().showTab(MainActivity.TAB_HOME) }
        // 헤더 아래 줄의 전체 삭제 버튼
        binding.textClear.setOnClickListener { confirmClear() }
        // 주문하기 → (로그인 안 했으면 로그인 먼저) → Order 탭 (팀원이 작성)
        binding.buttonOrder.setOnClickListener {
            if (AuthManager.isLoggedIn()) {
                mainActivity().showTab(MainActivity.TAB_ORDER)
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

        val totalPrice = CartManager.totalPrice().toWon()
        b.pageHeader.textSubtitle.text = if (isEmpty) {
            getString(R.string.cart_empty)
        } else {
            getString(R.string.cart_header_summary, items.size, totalPrice)
        }
        b.textTotalCount.text = getString(R.string.cart_count_value, CartManager.totalCount())

        b.textTotalPrice.text = totalPrice
        b.buttonOrder.text = getString(R.string.cart_order_button, totalPrice)
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cart_clear_title)
            .setMessage(R.string.cart_clear_message)
            .setNegativeButton(R.string.cart_cancel, null)
            .setPositiveButton(R.string.cart_clear_ok) { _, _ -> CartManager.clear() }
            .show()
    }
}