package com.example.brewandbite.home

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.brewandbite.MainActivity
import com.example.brewandbite.R
import com.example.brewandbite.cart.CartManager
import com.example.brewandbite.common.applyStatusBarPadding
import com.example.brewandbite.data.Category
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.data.MenuRepository
import com.example.brewandbite.data.toWon
import com.example.brewandbite.databinding.FragmentHomeBinding
import com.example.brewandbite.menu.MenuListActivity
import java.util.Calendar

/** Home 탭: 고정 헤더 + 검색 / 스크롤: 오늘의 추천 + 카테고리 + 인기 메뉴 + 추천 메뉴 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var menuAdapter: MenuAdapter
    private var selectedCategory: Category = MenuRepository.categories.first()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 헤더가 상태바 아래까지 이어지도록, 헤더 안쪽에만 상태바 높이만큼 여백 (헤더는 스크롤해도 고정)
        binding.headerBox.applyStatusBarPadding()

        setupHeader()
        setupCategories()
        setupPopular()
        setupRecommend()
        setupSearch()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** MainActivity에서 Home 탭을 다시 누르면 호출 */
    fun scrollToTop() {
        _binding?.scrollView?.smoothScrollTo(0, 0)
    }

    // ───────── 헤더 ─────────

    private fun setupHeader() {
        // 시간대별 인사말
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.textGreeting.setText(
            when (hour) {
                in 5..10 -> R.string.greeting_morning
                in 11..16 -> R.string.greeting_afternoon
                in 17..21 -> R.string.greeting_evening
                else -> R.string.greeting_night
            }
        )

        // BREW & BITE 의 '&'만 라이트브라운으로
        val brand = getString(R.string.brand_name)
        val ampIndex = brand.indexOf('&')
        if (ampIndex >= 0) {
            val spannable = SpannableString(brand)
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.bb_nav_active)),
                ampIndex, ampIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            binding.textBrand.text = spannable
        }

        // 오늘의 추천 카드: 날짜마다 다른 메뉴 하나
        val all = MenuRepository.menuItems
        val today = all[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % all.size]
        binding.textTodayEmoji.text = today.emoji
        binding.textTodayName.text = today.name
        binding.textTodayPrice.text = today.price.toWon()
        binding.todayPick.setOnClickListener { MenuListActivity.start(requireContext(), today.categoryId) }

        binding.imageProfile.setOnClickListener { (requireActivity() as MainActivity).showTab(MainActivity.TAB_MY) }
        // TODO: Favorite(찜한 메뉴) 화면을 만들면 여기서 이동
        binding.buttonFavorite.setOnClickListener { showComingSoon(getString(R.string.nav_favorite)) }
        binding.buttonBell.setOnClickListener { showComingSoon("알림") }
    }

    // ───────── 카테고리 / 인기 / 추천 / 검색 ─────────

    private fun setupCategories() {
        binding.recyclerCategory.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerCategory.adapter = CategoryAdapter(MenuRepository.categories) { category ->
            selectedCategory = category
            binding.editSearch.text?.clear()   // 카테고리를 누르면 검색은 초기화
            showCategoryItems()
        }
    }

    private fun setupPopular() {
        binding.recyclerPopular.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerPopular.adapter = PopularAdapter(
            MenuRepository.popularItems(),
            onItemClick = ::openDetail,
            onAddClick = ::addToCart
        )
        // 인기 메뉴 '전체 보기' → 메뉴 목록 화면 (인기)
        binding.textSeeAll.setOnClickListener {
            MenuListActivity.start(requireContext(), MenuRepository.POPULAR_ID)
        }
    }

    private fun setupRecommend() {
        menuAdapter = MenuAdapter(onItemClick = ::openDetail, onAddClick = ::addToCart)
        binding.recyclerMenu.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMenu.adapter = menuAdapter
        // 추천 메뉴 '전체 보기' → 선택한 카테고리의 메뉴 목록 화면
        binding.textRecommendSeeAll.setOnClickListener {
            MenuListActivity.start(requireContext(), selectedCategory.id)
        }
        showCategoryItems()
    }

    private fun setupSearch() {
        binding.editSearch.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            if (query.isBlank()) {
                showCategoryItems()
            } else {
                val results = MenuRepository.search(query)
                binding.textRecommendTitle.text = getString(R.string.section_search_result, results.size)
                binding.textRecommendSeeAll.visibility = View.GONE
                submitMenu(results)
            }
        }
        binding.buttonFilter.setOnClickListener { showComingSoon("필터") }
    }

    private fun showCategoryItems() {
        binding.textRecommendTitle.text = getString(R.string.section_recommend, selectedCategory.name)
        binding.textRecommendSeeAll.visibility = View.VISIBLE
        // 홈에는 4개까지만, 나머지는 '전체 보기'에서
        submitMenu(MenuRepository.itemsByCategory(selectedCategory.id).take(HOME_PREVIEW_COUNT))
    }

    private fun submitMenu(items: List<MenuItem>) {
        menuAdapter.submitList(items)
        binding.textEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    // ───────── 메뉴 동작 ─────────

    // TODO: 메뉴 상세 화면을 만들면 Intent로 이동
    private fun openDetail(item: MenuItem) {
        Toast.makeText(requireContext(), "${item.name} · ${MenuRepository.categoryName(item.categoryId)}", Toast.LENGTH_SHORT).show()
    }

    /** + 버튼: 장바구니에 담기 (탭 바의 뱃지는 자동으로 갱신) */
    private fun addToCart(item: MenuItem) {
        CartManager.add(item)
        Toast.makeText(
            requireContext(),
            getString(R.string.added_to_cart, item.name, CartManager.totalCount()),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showComingSoon(name: String) {
        Toast.makeText(requireContext(), getString(R.string.coming_soon, name), Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val HOME_PREVIEW_COUNT = 4
    }
}