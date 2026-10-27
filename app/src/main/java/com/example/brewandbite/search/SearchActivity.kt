package com.example.brewandbite.search

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.brewandbite.R
import com.example.brewandbite.cart.CartManager
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.data.MenuRepository
import com.example.brewandbite.databinding.ActivitySearchBinding
import com.example.brewandbite.favorite.FavoriteManager
import com.example.brewandbite.home.MenuAdapter
import com.example.brewandbite.menu.MenuDetailActivity
import com.example.brewandbite.menu.MenuListActivity
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 🔍 메뉴 검색 화면 — 홈의 검색창(또는 필터 버튼)을 누르면 열린다.
 *
 *  검색어가 없을 때
 *   - 🕘 최근 검색어 (누르면 다시 검색 · ✕ 로 하나 지우기 · [전체 삭제])
 *   - 🔥 인기 검색어 (1 아메리카노 · 2 라떼 …)
 *   - ☕ 카테고리로 찾기 (누르면 메뉴 목록 화면)
 *  검색어가 있을 때 (글자를 칠 때마다 바로)
 *   - 'xx' 검색 결과 N개 · 카테고리 칩(전체 · 커피 · 음료 …) · 정렬(추천순 · 낮은 가격 · 높은 가격 · 별점)
 *   - 메뉴 카드 (누르면 메뉴 상세 · + 로 바로 담기 · ♡ 찜)
 *   - 없으면 "'xx'에 맞는 메뉴가 없어요"
 *
 * 한글 · 영어 · 재료로도 찾을 수 있다 (MenuSearch.kt)
 * 여는 법: SearchActivity.start(context)  /  정렬 창 먼저: SearchActivity.start(context, openSort = true)
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var adapter: MenuAdapter

    private var sort = MenuSearch.Sort.RECOMMEND
    private var categoryFilter: String? = null   // null = 전체

    private val favoriteListener = FavoriteManager.FavoriteListener { adapter.notifyDataSetChanged() }
    private val menuListener = MenuRepository.MenuListener { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            binding.headerBox.setPadding(
                binding.headerBox.paddingLeft, insets.getInsets(WindowInsetsCompat.Type.statusBars()).top + dp(10),
                binding.headerBox.paddingRight, binding.headerBox.paddingBottom
            )
            insets
        }
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonClear.setOnClickListener {
            binding.editSearch.text?.clear()
            showKeyboard()
        }
        binding.buttonSort.setOnClickListener { chooseSort() }

        // 결과 목록 (홈의 추천 메뉴와 같은 카드)
        adapter = MenuAdapter(onItemClick = ::openDetail, onAddClick = ::addToCart)
        binding.recyclerResult.layoutManager = LinearLayoutManager(this)
        binding.recyclerResult.adapter = adapter

        // 글자를 칠 때마다 바로 검색
        binding.editSearch.doAfterTextChanged {
            binding.buttonClear.visibility = if (it.isNullOrEmpty()) View.GONE else View.VISIBLE
            render()
        }
        // 키보드의 [검색] → 최근 검색어에 저장 + 키보드 내리기
        binding.editSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                MenuSearch.addRecent(this, query())
                hideKeyboard()
                true
            } else false
        }
        binding.buttonClearRecent.setOnClickListener {
            MenuSearch.clearRecent(this)
            renderSuggestions()
        }

        setupPopularChips()
        setupCategoryChips()
        setupFilterChips()
        updateSortLabel()
        render()

        if (savedInstanceState == null) {
            if (intent.getBooleanExtra(EXTRA_OPEN_SORT, false)) chooseSort() else showKeyboard()
        }
    }

    override fun onStart() {
        super.onStart()
        FavoriteManager.addListener(favoriteListener)
        MenuRepository.addListener(menuListener)
        renderSuggestions()
    }

    override fun onStop() {
        super.onStop()
        FavoriteManager.removeListener(favoriteListener)
        MenuRepository.removeListener(menuListener)
    }

    private fun query() = binding.editSearch.text?.toString()?.trim().orEmpty()

    // ───────── 화면 그리기 ─────────

    private fun render() {
        val q = query()
        binding.suggestBox.visibility = if (q.isEmpty()) View.VISIBLE else View.GONE
        binding.resultBox.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
        if (q.isEmpty()) {
            renderSuggestions()
            return
        }
        val results = MenuSearch.search(q, categoryFilter, sort)
        binding.textResultCount.text = getString(R.string.search_result_count, q, results.size)
        adapter.submitList(results)
        binding.recyclerResult.visibility = if (results.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyResult.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        binding.textEmpty.text = getString(R.string.search_empty, q)
    }

    /** 최근 검색어 칩 (누르면 검색 · ✕ 로 지우기) */
    private fun renderSuggestions() {
        val recent = MenuSearch.recent(this)
        binding.recentBox.visibility = if (recent.isEmpty()) View.GONE else View.VISIBLE
        binding.chipsRecent.removeAllViews()
        recent.forEach { word ->
            binding.chipsRecent.addView(chip(word).apply {
                isCloseIconVisible = true
                closeIconTint = ColorStateList.valueOf(ContextCompat.getColor(this@SearchActivity, R.color.bb_muted))
                setOnCloseIconClickListener {
                    MenuSearch.removeRecent(this@SearchActivity, word)
                    renderSuggestions()
                }
                setOnClickListener { searchFor(word) }
            })
        }
    }

    /** 🔥 인기 검색어: "1 아메리카노" (1~3위는 살구색 숫자) */
    private fun setupPopularChips() {
        MenuSearch.popularKeywords.forEachIndexed { i, word ->
            binding.chipsPopular.addView(chip("${i + 1}  $word").apply {
                if (i < 3) setTextColor(ContextCompat.getColor(this@SearchActivity, R.color.bb_espresso))
                chipStrokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this@SearchActivity, if (i < 3) R.color.bb_nav_active else R.color.bb_divider)
                )
                setOnClickListener { searchFor(word) }
            })
        }
    }

    /** ☕ 카테고리로 찾기 → 메뉴 목록 화면 */
    private fun setupCategoryChips() {
        binding.chipsCategory.removeAllViews()
        MenuRepository.categories.forEach { c ->
            binding.chipsCategory.addView(chip("${c.emoji} ${c.nameKo}").apply {
                setOnClickListener { MenuListActivity.start(this@SearchActivity, c.id) }
            })
        }
    }

    /** 결과 위 카테고리 필터 칩: 전체 · 커피 · 음료 … (고른 칩은 에스프레소 바탕) */
    private fun setupFilterChips() {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        fun colors(on: Int, off: Int) = ColorStateList(
            states, intArrayOf(ContextCompat.getColor(this, on), ContextCompat.getColor(this, off))
        )
        val options = listOf<Pair<String, String?>>(getString(R.string.notice_filter_all) to null) +
                MenuRepository.categories.map { "${it.emoji} ${it.nameKo}" to it.id }
        binding.chipsFilter.removeAllViews()
        options.forEach { (label, id) ->
            binding.chipsFilter.addView(Chip(this).apply {
                text = label
                isCheckable = true
                isCheckedIconVisible = false
                isChecked = id == categoryFilter
                chipBackgroundColor = colors(R.color.bb_espresso, R.color.white)
                setTextColor(colors(R.color.bb_cream, R.color.bb_brown))
                chipStrokeColor = colors(R.color.bb_espresso, R.color.bb_divider)
                chipStrokeWidth = resources.displayMetrics.density
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        categoryFilter = id
                        render()
                    }
                }
            })
        }
    }

    /** 흰 바탕 칩 (최근 · 인기 · 카테고리) */
    private fun chip(label: String) = Chip(this).apply {
        text = label
        chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(this@SearchActivity, R.color.white))
        setTextColor(ContextCompat.getColor(this@SearchActivity, R.color.bb_brown))
        chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@SearchActivity, R.color.bb_divider))
        chipStrokeWidth = resources.displayMetrics.density
    }

    // ───────── 동작 ─────────

    /** 최근 · 인기 검색어를 누르면 그 말로 검색 */
    private fun searchFor(word: String) {
        binding.editSearch.setText(word)
        binding.editSearch.setSelection(word.length)
        MenuSearch.addRecent(this, word)
        hideKeyboard()
    }

    /** 정렬: 추천순 · 낮은 가격순 · 높은 가격순 · 별점 높은 순 */
    private fun chooseSort() {
        val labels = arrayOf(
            getString(R.string.search_sort_recommend), getString(R.string.search_sort_price_low),
            getString(R.string.search_sort_price_high), getString(R.string.search_sort_rating)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.search_sort_title)
            .setSingleChoiceItems(labels, sort.ordinal) { dialog, which ->
                sort = MenuSearch.Sort.entries[which]
                updateSortLabel()
                render()
                dialog.dismiss()
                if (query().isEmpty()) showKeyboard()
            }
            .show()
    }

    private fun updateSortLabel() {
        binding.buttonSort.text = getString(
            R.string.search_sort_label,
            getString(
                when (sort) {
                    MenuSearch.Sort.RECOMMEND -> R.string.search_sort_recommend
                    MenuSearch.Sort.PRICE_LOW -> R.string.search_sort_price_low_short
                    MenuSearch.Sort.PRICE_HIGH -> R.string.search_sort_price_high_short
                    MenuSearch.Sort.RATING -> R.string.search_sort_rating_short
                }
            )
        )
    }

    /** 메뉴를 누르면 → 메뉴 상세 (검색어는 최근 검색어에 저장) */
    private fun openDetail(item: MenuItem) {
        MenuSearch.addRecent(this, query())
        MenuDetailActivity.start(this, item.id)
    }

    private fun addToCart(item: MenuItem) {
        MenuSearch.addRecent(this, query())
        CartManager.add(item)
        Toast.makeText(this, getString(R.string.added_to_cart, item.name, CartManager.totalCount()), Toast.LENGTH_SHORT).show()
    }

    private fun showKeyboard() {
        binding.editSearch.requestFocus()
        binding.editSearch.post {
            getSystemService(InputMethodManager::class.java)?.showSoftInput(binding.editSearch, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(binding.editSearch.windowToken, 0)
        binding.editSearch.clearFocus()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_OPEN_SORT = "open_sort"

        fun start(context: Context, openSort: Boolean = false) {
            context.startActivity(Intent(context, SearchActivity::class.java).putExtra(EXTRA_OPEN_SORT, openSort))
        }
    }
}