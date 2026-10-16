package com.example.brewandbite.menu

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.brewandbite.R
import com.example.brewandbite.cart.CartManager
import com.example.brewandbite.data.Category
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.data.MenuRepository
import com.example.brewandbite.databinding.ActivityMenuListBinding

/** Menu 화면: 한 카테고리의 메뉴 전체를 2열 카드로 보여 준다 */
class MenuListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuListBinding
    private lateinit var gridAdapter: MenuGridAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMenuListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val startId = intent.getStringExtra(EXTRA_CATEGORY_ID) ?: MenuRepository.categories.first().id

        binding.buttonBack.setOnClickListener { finish() }

        // 메뉴 2열 그리드
        gridAdapter = MenuGridAdapter(onItemClick = ::openDetail, onAddClick = ::addToCart)
        binding.recyclerGrid.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerGrid.adapter = gridAdapter

        // 카테고리 칩: 인기 + 6개 카테고리
        val chips = listOf(MenuRepository.popularCategory) + MenuRepository.categories
        val chipAdapter = ChipAdapter(chips, startId) { category -> showCategory(category) }
        binding.recyclerChips.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerChips.adapter = chipAdapter
        binding.recyclerChips.scrollToPosition(chipAdapter.selectedIndex())

        showCategory(MenuRepository.findCategory(startId))
    }

    private fun showCategory(category: Category) {
        val items = MenuRepository.itemsFor(category.id)
        binding.textTitle.text = category.name
        binding.textSubtitle.text = getString(R.string.menu_count, category.nameKo, items.size)
        gridAdapter.submitList(items) { binding.recyclerGrid.scrollToPosition(0) }
        binding.textEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    // TODO: 메뉴 상세 화면을 만들면 Intent로 이동
    private fun openDetail(item: MenuItem) {
        Toast.makeText(this, "${item.name} · ${MenuRepository.categoryName(item.categoryId)}", Toast.LENGTH_SHORT).show()
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