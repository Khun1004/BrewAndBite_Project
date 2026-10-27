package com.example.brewandbite.menu

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.brewandbite.MainActivity
import com.example.brewandbite.R
import com.example.brewandbite.cart.CartManager
import com.example.brewandbite.common.applyStatusBarPadding
import com.example.brewandbite.data.Category
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.data.MenuRepository
import com.example.brewandbite.databinding.ActivityMenuListBinding
import com.example.brewandbite.favorite.FavoriteManager

/** Menu 화면: 한 카테고리의 메뉴 전체를 2열 카드로 보여 준다 */
class MenuListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuListBinding
    private lateinit var gridAdapter: MenuGridAdapter

    /** 장바구니가 바뀌면 헤더의 숫자 뱃지를 다시 그린다 */
    private val cartListener = CartManager.CartListener { updateCartBadge() }

    /** 찜이 바뀌면 카드의 ♡ 를 다시 그린다 */
    private val favoriteListener = FavoriteManager.FavoriteListener { gridAdapter.notifyDataSetChanged() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMenuListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 헤더가 상태바 아래까지 이어지도록: 바깥은 좌우/아래만, 헤더 안쪽에 상태바 높이만큼
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }
        binding.headerBox.applyStatusBarPadding()
        // 헤더가 어두우므로 상태바 글자를 밝게
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val startId = intent.getStringExtra(EXTRA_CATEGORY_ID) ?: MenuRepository.categories.first().id

        binding.buttonBack.setOnClickListener { finish() }
        // 장바구니 버튼 → 메인 화면의 Cart 탭
        binding.buttonCart.setOnClickListener { MainActivity.openTab(this, MainActivity.TAB_CART) }

        // 메뉴 2열 그리드
        gridAdapter = MenuGridAdapter(onItemClick = ::openDetail, onAddClick = ::addToCart)
        binding.recyclerGrid.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerGrid.adapter = gridAdapter

        // 카테고리 칩: 인기 + 6개 카테고리
        binding.recyclerChips.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        setupChips(startId)
        showCategory(MenuRepository.findCategory(startId))
    }

    /** 지금 보고 있는 카테고리 */
    private var currentId = ""

    /** 카테고리 칩: 인기 + 카테고리들 (Firebase 메뉴가 바뀌면 다시 만듦) */
    private fun setupChips(selectedId: String) {
        val chips = listOf(MenuRepository.popularCategory) + MenuRepository.categories
        val chipAdapter = ChipAdapter(chips, selectedId) { category -> showCategory(category) }
        binding.recyclerChips.adapter = chipAdapter
        binding.recyclerChips.scrollToPosition(chipAdapter.selectedIndex())
    }

    /** Firebase 에서 메뉴를 (다시) 불러오면 칩 · 메뉴 다시 그리기 */
    private val menuListener = MenuRepository.MenuListener {
        setupChips(currentId)
        showCategory(MenuRepository.findCategory(currentId))
    }

    override fun onStart() {
        super.onStart()
        CartManager.addListener(cartListener)
        updateCartBadge()
        FavoriteManager.addListener(favoriteListener)
        MenuRepository.addListener(menuListener)
        gridAdapter.notifyDataSetChanged()
    }

    override fun onStop() {
        super.onStop()
        CartManager.removeListener(cartListener)
        FavoriteManager.removeListener(favoriteListener)
        MenuRepository.removeListener(menuListener)
    }

    /** 헤더의 장바구니 숫자 (0이면 숨김) */
    private fun updateCartBadge() {
        val count = CartManager.totalCount()
        binding.badgeCart.visibility = if (count > 0) View.VISIBLE else View.GONE
        binding.badgeCart.text = if (count > 99) "99+" else count.toString()
    }

    private fun showCategory(category: Category) {
        currentId = category.id
        val items = MenuRepository.itemsFor(category.id)
        binding.textCategoryEmoji.text = category.emoji
        binding.textTitle.text = category.name
        binding.textSubtitle.text = getString(R.string.menu_count, category.nameKo, items.size)
        gridAdapter.submitList(items) { binding.recyclerGrid.scrollToPosition(0) }
        binding.textEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    /** 메뉴 카드를 누르면 → 메뉴 상세 화면 */
    private fun openDetail(item: MenuItem) {
        MenuDetailActivity.start(this, item.id)
    }

    /** + 버튼: 장바구니에 담기 */
    private fun addToCart(item: MenuItem) {
        CartManager.add(item)
        Toast.makeText(this, getString(R.string.added_to_cart, item.name, CartManager.totalCount()), Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val EXTRA_CATEGORY_ID = "category_id"

        /** 다른 화면에서 메뉴 목록을 여는 함수. categoryId에 "popular"를 넣으면 인기 메뉴 */
        fun start(context: Context, categoryId: String) {
            context.startActivity(
                Intent(context, MenuListActivity::class.java).putExtra(EXTRA_CATEGORY_ID, categoryId)
            )
        }
    }
}