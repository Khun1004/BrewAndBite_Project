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
import com.bumptech.glide.Glide
import com.example.brewandbite.MainActivity
import com.example.brewandbite.R
import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.auth.UserRepository
import com.example.brewandbite.cart.CartManager
import com.example.brewandbite.common.applyStatusBarPadding
import com.example.brewandbite.data.Category
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.data.MenuRepository
import com.example.brewandbite.data.toWon
import com.example.brewandbite.databinding.FragmentHomeBinding
import com.example.brewandbite.favorite.FavoriteManager
import com.example.brewandbite.menu.MenuDetailActivity
import com.example.brewandbite.menu.MenuListActivity
import com.example.brewandbite.mypage.MyDetailActivity
import com.example.brewandbite.mypage.ProfilePhoto
import com.example.brewandbite.notice.NoticeActivity
import com.example.brewandbite.notice.NoticeManager
import com.example.brewandbite.notice.bindNoticeDot
import com.example.brewandbite.search.SearchActivity
import java.util.Calendar

/** Home 탭: 고정 헤더 + 검색 / 스크롤: 오늘의 추천 + 카테고리 + 인기 메뉴 + 추천 메뉴 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var menuAdapter: MenuAdapter
    private var selectedCategory: Category = MenuRepository.categories.first()

    /** 찜이 바뀌면 (다른 화면에서 바꿔도) 카드의 ♡ 를 다시 그린다 */
    private val favoriteListener = FavoriteManager.FavoriteListener {
        val b = _binding ?: return@FavoriteListener
        b.recyclerPopular.adapter?.notifyDataSetChanged()
        b.recyclerMenu.adapter?.notifyDataSetChanged()
    }

    /** Firebase 에서 메뉴를 (다시) 불러오면 카테고리 · 인기 · 추천 · 오늘의 추천 다시 그리기 */
    private val menuListener = MenuRepository.MenuListener {
        if (_binding == null) return@MenuListener
        selectedCategory = MenuRepository.findCategory(selectedCategory.id)
        setupCategories()
        setupPopular()
        showTodayPick()
        if (binding.editSearch.text.isNullOrBlank()) showCategoryItems()
    }

    /** 새 알림이 오면 🔔 빨간 점 다시 그리기 */
    private val noticeListener = NoticeManager.NoticeListener { _binding?.dotBell?.bindNoticeDot() }

    override fun onStart() {
        super.onStart()
        NoticeManager.addListener(noticeListener)
        MenuRepository.addListener(menuListener)
        binding.dotBell.bindNoticeDot()
        FavoriteManager.addListener(favoriteListener)
        favoriteListener.onFavoritesChanged()
        showProfile()
    }

    /** 다른 탭(My Page)에서 사진/이름을 바꾸고 돌아오면 다시 그리기 */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) showProfile()
    }

    /**
     * 헤더 왼쪽 프로필: My Page 와 같은 사진 + 밑에 이름
     *  사진 순서: My Page 에서 고른 사진 → Google 계정 사진 → 기본 그림
     */
    private fun showProfile() {
        if (!AuthManager.isLoggedIn()) {
            val b = _binding ?: return
            b.imageProfile.setImageResource(R.drawable.profile)
            b.textProfileName.setText(R.string.home_profile_login)
            return
        }
        // 불러오는 동안 이메일 앞부분이라도 먼저
        _binding?.textProfileName?.text = AuthManager.currentEmail()?.substringBefore("@").orEmpty()
        UserRepository.loadMyProfile { profile ->
            val b = _binding ?: return@loadMyProfile
            val name = profile?.name?.takeIf { it.isNotBlank() }
                ?: AuthManager.currentEmail()?.substringBefore("@").orEmpty()
            b.textProfileName.text = name

            val photo = profile?.photo?.takeIf { it.isNotBlank() }?.let { ProfilePhoto.decode(it) }
            val googlePhoto = AuthManager.currentPhotoUrl()
            when {
                photo != null -> b.imageProfile.setImageBitmap(photo)
                googlePhoto != null -> Glide.with(this).load(googlePhoto).centerCrop().into(b.imageProfile)
                else -> b.imageProfile.setImageResource(R.drawable.profile)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        NoticeManager.removeListener(noticeListener)
        MenuRepository.removeListener(menuListener)
        FavoriteManager.removeListener(favoriteListener)
    }

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

        showTodayPick()

        binding.imageProfile.setOnClickListener { (requireActivity() as MainActivity).showTab(MainActivity.TAB_MY) }
        // ♡ → 찜한 메뉴 화면 (로그인해야 볼 수 있음)
        binding.buttonFavorite.setOnClickListener {
            if (AuthManager.isLoggedIn()) {
                startActivity(MyDetailActivity.intent(requireContext(), MyDetailActivity.Section.FAVORITES))
            } else {
                Toast.makeText(requireContext(), R.string.fav_login_needed, Toast.LENGTH_SHORT).show()
            }
        }
        // 🔔 → 알림함
        binding.buttonBell.setOnClickListener { NoticeActivity.start(requireContext()) }
    }

    /** 오늘의 추천 카드: 날짜마다 다른 메뉴 하나 */
    private fun showTodayPick() {
        val all = MenuRepository.menuItems
        if (all.isEmpty()) return
        val today = all[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % all.size]
        binding.textTodayEmoji.text = today.emoji
        binding.textTodayName.text = today.name
        binding.textTodayPrice.text = today.price.toWon()
        binding.todayPick.setOnClickListener { MenuDetailActivity.start(requireContext(), today.id) }
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

    /** 🔍 검색창을 누르면 → 검색 화면 (최근 · 인기 검색어 · 한글로 찾기) / 필터 버튼 → 검색 화면 + 정렬 */
    private fun setupSearch() {
        binding.editSearch.isFocusable = false          // 홈에서는 글자를 치지 않고 검색 화면으로
        binding.editSearch.isCursorVisible = false
        binding.editSearch.setOnClickListener { SearchActivity.start(requireContext()) }
        (binding.editSearch.parent as? View)?.setOnClickListener { SearchActivity.start(requireContext()) }
        binding.buttonFilter.setOnClickListener { SearchActivity.start(requireContext(), openSort = true) }
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

    /** 메뉴 카드를 누르면 → 메뉴 상세 화면 (옵션 고르고 담기) */
    private fun openDetail(item: MenuItem) {
        MenuDetailActivity.start(requireContext(), item.id)
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