package com.example.brewandbite

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import com.example.brewandbite.cart.CartFragment
import com.example.brewandbite.cart.CartManager
import com.example.brewandbite.databinding.ActivityMainBinding
import com.example.brewandbite.home.HomeFragment
import com.example.brewandbite.mypage.MyPageFragment
import com.example.brewandbite.order.OrderFragment
import com.example.brewandbite.store.StoreFragment

/**
 * 앱의 메인 틀.
 * 아래쪽 탭 바는 항상 보이고, 위쪽 화면만 탭에 따라 Home / Cart / Store / Order / My Page 로 바뀐다.
 * 다른 화면에서 탭을 바꾸고 싶으면: (requireActivity() as MainActivity).showTab(MainActivity.TAB_HOME)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentTab = -1

    /** 하단 탭 하나 (눌리는 영역, 동그라미, 아이콘, 글자) */
    private data class NavTab(val root: View, val circle: FrameLayout, val icon: ImageView, val label: TextView)

    private lateinit var navTabs: List<NavTab>

    /** 장바구니가 바뀌면 Cart 뱃지 숫자를 다시 그린다 */
    private val cartListener = CartManager.CartListener { updateCartBadge() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 위쪽 여백은 각 탭 화면이 직접 처리 (홈 헤더가 상태바 아래까지 이어지도록)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        setupBottomNav()
        val startTab = savedInstanceState?.getInt(KEY_TAB, TAB_HOME)
            ?: intent.getIntExtra(EXTRA_TAB, TAB_HOME)
        showTab(startTab)
    }

    /** 이미 열려 있을 때 다른 화면에서 openTab()으로 돌아오면 여기로 들어온다 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra(EXTRA_TAB)) showTab(intent.getIntExtra(EXTRA_TAB, TAB_HOME))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, currentTab)
    }

    override fun onStart() {
        super.onStart()
        CartManager.addListener(cartListener)
        updateCartBadge()
    }

    override fun onStop() {
        super.onStop()
        CartManager.removeListener(cartListener)
    }

    // ───────── 탭 전환 ─────────

    private fun setupBottomNav() {
        // 탭 순서: Cart, Store, Home(가운데), Order, My Page
        navTabs = listOf(
            NavTab(binding.navCart, binding.circleCart, binding.iconCart, binding.labelCart),
            NavTab(binding.navStore, binding.circleStore, binding.iconStore, binding.labelStore),
            NavTab(binding.navHome, binding.circleHome, binding.iconHome, binding.labelHome),
            NavTab(binding.navOrder, binding.circleOrder, binding.iconOrder, binding.labelOrder),
            NavTab(binding.navMy, binding.circleMy, binding.iconMy, binding.labelMy)
        )
        navTabs.forEachIndexed { index, tab ->
            tab.root.setOnClickListener { showTab(index) }
        }
    }

    /** 탭을 바꾼다. 아래 탭 바는 그대로, 위쪽 화면만 교체. */
    fun showTab(index: Int) {
        if (index == currentTab) {
            // 지금 보고 있는 Home을 다시 누르면 맨 위로
            if (index == TAB_HOME) (findTabFragment(TAB_HOME) as? HomeFragment)?.scrollToTop()
            return
        }

        val transaction = supportFragmentManager.beginTransaction().setReorderingAllowed(true)
        for (i in navTabs.indices) {
            val fragment = findTabFragment(i)
            if (i == index) {
                if (fragment == null) transaction.add(R.id.fragmentContainer, createTabFragment(i), tabTag(i))
                else transaction.show(fragment)
            } else if (fragment != null) {
                transaction.hide(fragment)
            }
        }
        transaction.commit()

        currentTab = index
        updateTabUi(index)

        // 모든 탭의 헤더가 어두운 커피색이라 상태바 글자(시계·배터리)는 항상 밝게
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
    }

    private fun createTabFragment(index: Int): Fragment = when (index) {
        TAB_CART -> CartFragment()
        TAB_STORE -> StoreFragment()
        TAB_HOME -> HomeFragment()
        TAB_ORDER -> OrderFragment()
        else -> MyPageFragment()
    }

    private fun tabTag(index: Int) = "tab_$index"

    private fun findTabFragment(index: Int): Fragment? =
        supportFragmentManager.findFragmentByTag(tabTag(index))

    // ───────── 탭 바 모양 ─────────

    /**
     * 선택된 탭: 아이콘이 라이트브라운 동그라미 안에 들어가 위로 솟아오른다.
     * 선택 안 된 탭: 동그라미 없이 회갈색 아이콘.
     */
    private fun updateTabUi(selectedIndex: Int) {
        val activeColor = ContextCompat.getColor(this, R.color.bb_nav_active)
        val inactiveColor = ContextCompat.getColor(this, R.color.bb_nav_inactive)
        val iconOnCircle = ContextCompat.getColor(this, R.color.bb_espresso)
        val lift = -22f * resources.displayMetrics.density
        val raisedElevation = 8f * resources.displayMetrics.density

        navTabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex

            tab.circle.background =
                if (selected) ContextCompat.getDrawable(this, R.drawable.bg_tab_selected) else null
            tab.circle.elevation = if (selected) raisedElevation else 0f
            tab.circle.animate()
                .translationY(if (selected) lift else 0f)
                .scaleX(if (selected) 1.06f else 1f)
                .scaleY(if (selected) 1.06f else 1f)
                .setDuration(220)
                .start()

            ImageViewCompat.setImageTintList(
                tab.icon, ColorStateList.valueOf(if (selected) iconOnCircle else inactiveColor)
            )
            tab.label.setTextColor(if (selected) activeColor else inactiveColor)
            tab.label.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    /** Cart 동그라미 위 숫자 (0이면 숨김, 99개 넘으면 99+) */
    private fun updateCartBadge() {
        val count = CartManager.totalCount()
        binding.badgeCart.visibility = if (count > 0) View.VISIBLE else View.GONE
        binding.badgeCart.text = if (count > 99) "99+" else count.toString()
    }

    companion object {
        const val TAB_CART = 0
        const val TAB_STORE = 1
        const val TAB_HOME = 2
        const val TAB_ORDER = 3
        const val TAB_MY = 4

        private const val KEY_TAB = "current_tab"
        private const val EXTRA_TAB = "extra_tab"

        /** 다른 화면(예: 메뉴 목록)에서 특정 탭으로 돌아가기 */
        fun openTab(context: Context, tab: Int) {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_TAB, tab)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }
}