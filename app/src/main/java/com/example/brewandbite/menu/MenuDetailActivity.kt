package com.example.brewandbite.menu

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.brewandbite.MainActivity
import com.example.brewandbite.R
import com.example.brewandbite.cart.CartManager
import com.example.brewandbite.common.applyStatusBarPadding
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.data.MenuRepository
import com.example.brewandbite.data.toWon
import com.example.brewandbite.databinding.ActivityMenuDetailBinding
import com.example.brewandbite.favorite.FavoriteManager
import com.example.brewandbite.favorite.bindFavorite
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar

/**
 * 메뉴 상세 화면: 큰 메뉴 그림 · 설명 · 옵션(온도/사이즈/샷/데우기) · 재료 빼기/추가 · 요청사항 · 수량 → [담기]
 * 옵션 종류와 가격은 MenuOptions.kt 에서 정한다.
 *
 * 여는 법: MenuDetailActivity.start(context, item.id)
 */
class MenuDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuDetailBinding
    private lateinit var item: MenuItem

    private var selection = MenuOptions.Selection()
    private var quantity = 1

    private val cartListener = CartManager.CartListener { updateCartBadge() }
    private val favoriteListener = FavoriteManager.FavoriteListener { binding.buttonFavorite.bindFavorite(item) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val found = MenuRepository.findItem(intent.getStringExtra(EXTRA_MENU_ID).orEmpty())
        if (found == null) {
            finish()
            return
        }
        item = found

        enableEdgeToEdge()
        binding = ActivityMenuDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        binding.topBar.applyStatusBarPadding()
        // 아래 바가 내비게이션 바(제스처 바)·키보드에 가리지 않게 (요청사항 입력할 때 키보드 위로 올라감)
        val bottomPad = binding.bottomBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            binding.bottomBar.setPadding(
                binding.bottomBar.paddingLeft, binding.bottomBar.paddingTop,
                binding.bottomBar.paddingRight, bottomPad + bars.bottom
            )
            insets
        }

        showMenu()
        setupOptions()
        setupBottomBar()

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonCart.setOnClickListener { MainActivity.openTab(this, MainActivity.TAB_CART) }

        // 접시가 살짝 떠오르며 등장
        if (savedInstanceState == null) {
            binding.plate.scaleX = 0.85f
            binding.plate.scaleY = 0.85f
            binding.plate.alpha = 0f
            binding.plate.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(450).start()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!::binding.isInitialized) return
        CartManager.addListener(cartListener)
        FavoriteManager.addListener(favoriteListener)
        updateCartBadge()
        binding.buttonFavorite.bindFavorite(item)
    }

    override fun onStop() {
        super.onStop()
        CartManager.removeListener(cartListener)
        FavoriteManager.removeListener(favoriteListener)
    }

    // ───────── 메뉴 정보 ─────────

    private fun showMenu() {
        val category = MenuRepository.findCategory(item.categoryId)
        binding.textEmoji.text = item.emoji
        binding.textCategory.text = "${category.emoji} ${category.nameKo}"
        binding.textRating.text = item.rating.toString()
        binding.textName.text = item.name
        binding.textDescription.text = item.description
        binding.textBasePrice.text = item.price.toWon()
    }

    // ───────── 옵션 ─────────

    private fun setupOptions() {
        val b = binding
        b.groupTemperature.visibility = visibleIf(MenuOptions.hasTemperature(item))
        b.groupSize.visibility = visibleIf(MenuOptions.hasSize(item))
        b.groupShot.visibility = visibleIf(MenuOptions.hasShot(item))
        b.groupWarm.visibility = visibleIf(MenuOptions.canWarm(item))
        // 재료 빼기 / 추가 칩 (메뉴마다 다름)
        val ingredients = MenuOptions.ingredients(item)
        b.groupRemove.visibility = visibleIf(ingredients.removable.isNotEmpty())
        b.groupExtra.visibility = visibleIf(ingredients.extras.isNotEmpty())
        ingredients.removable.forEach { name ->
            addChip(b.chipsRemove, getString(R.string.opt_remove_chip, name)) { checked ->
                selection = selection.copy(removed = if (checked) selection.removed + name else selection.removed - name)
                updateTotal()
            }
        }
        ingredients.extras.forEach { extra ->
            val price = if (extra.price == 0) getString(R.string.opt_extra_free) else "+${extra.price.toWon()}"
            addChip(b.chipsExtra, getString(R.string.opt_extra_chip, extra.name, price)) { checked ->
                selection = selection.copy(extras = if (checked) selection.extras + extra.name else selection.extras - extra.name)
                updateTotal()
            }
        }
        // 요청사항은 모든 메뉴에 있으므로 '옵션 없음' 안내는 쓰지 않음
        b.textNoOption.visibility = View.GONE

        // 온도
        b.optionHot.setOnClickListener { update(selection.copy(iced = false)) }
        b.optionIce.setOnClickListener { update(selection.copy(iced = true)) }

        // 사이즈 (두 줄: 이름 + 추가 금액)
        val sizes = listOf(
            b.optionRegular to MenuOptions.Size.REGULAR,
            b.optionLarge to MenuOptions.Size.LARGE,
            b.optionGrande to MenuOptions.Size.GRANDE
        )
        sizes.forEach { (view, size) ->
            view.text = getString(R.string.opt_size_label, size.label, size.desc, "+${size.extraPrice.toWon()}")
            view.setOnClickListener { update(selection.copy(size = size)) }
        }

        // 샷
        b.buttonShotMinus.setOnClickListener {
            if (selection.shots > 0) update(selection.copy(shots = selection.shots - 1))
        }
        b.buttonShotPlus.setOnClickListener {
            if (selection.shots < MenuOptions.MAX_SHOTS) update(selection.copy(shots = selection.shots + 1))
        }

        // 데우기
        b.optionAsIs.setOnClickListener { update(selection.copy(warm = false)) }
        b.optionWarm.setOnClickListener { update(selection.copy(warm = true)) }

        update(selection)
    }

    private fun update(newSelection: MenuOptions.Selection) {
        selection = newSelection
        val b = binding
        mark(b.optionHot, !selection.iced)
        mark(b.optionIce, selection.iced)
        mark(b.optionRegular, selection.size == MenuOptions.Size.REGULAR)
        mark(b.optionLarge, selection.size == MenuOptions.Size.LARGE)
        mark(b.optionGrande, selection.size == MenuOptions.Size.GRANDE)
        mark(b.optionAsIs, !selection.warm)
        mark(b.optionWarm, selection.warm)

        b.textShotCount.text = selection.shots.toString()
        b.textShotDesc.text =
            if (selection.shots == 0) getString(R.string.opt_shot_none)
            else getString(R.string.opt_shot_desc, selection.shots, (selection.shots * MenuOptions.SHOT_PRICE).toWon())
        b.buttonShotMinus.alpha = if (selection.shots > 0) 1f else 0.4f
        b.buttonShotPlus.alpha = if (selection.shots < MenuOptions.MAX_SHOTS) 1f else 0.4f

        updateTotal()
    }

    /** 선택된 칸은 에스프레소 바탕 + 크림 글자, 아니면 흰 바탕 */
    private fun mark(view: TextView, selected: Boolean) {
        view.setBackgroundResource(if (selected) R.drawable.bg_option_selected else R.drawable.bg_option)
        view.setTextColor(ContextCompat.getColor(this, if (selected) R.color.bb_cream else R.color.bb_espresso))
    }

    private fun visibleIf(show: Boolean) = if (show) View.VISIBLE else View.GONE

    /** 누르면 켜졌다 꺼지는 칩 (켜지면 에스프레소 바탕 + 크림 글자) */
    private fun addChip(group: ChipGroup, label: String, onChange: (Boolean) -> Unit) {
        val checkedStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        fun colors(on: Int, off: Int) = ColorStateList(
            checkedStates, intArrayOf(ContextCompat.getColor(this, on), ContextCompat.getColor(this, off))
        )
        val chip = Chip(this).apply {
            text = label
            isCheckable = true
            isCheckedIconVisible = false
            chipBackgroundColor = colors(R.color.bb_espresso, R.color.white)
            setTextColor(colors(R.color.bb_cream, R.color.bb_espresso))
            chipStrokeColor = colors(R.color.bb_nav_active, R.color.bb_beige)
            chipStrokeWidth = resources.displayMetrics.density * 1.5f
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        group.addView(chip)
    }

    // ───────── 수량 / 담기 ─────────

    private fun setupBottomBar() {
        binding.buttonMinus.setOnClickListener {
            if (quantity > 1) {
                quantity--
                updateTotal()
            }
        }
        binding.buttonPlus.setOnClickListener {
            if (quantity < MAX_QUANTITY) {
                quantity++
                updateTotal()
            }
        }
        binding.buttonAddCart.setOnClickListener { addToCart() }
    }

    private fun updateTotal() {
        val total = MenuOptions.unitPrice(item, selection) * quantity
        binding.textQuantity.text = quantity.toString()
        binding.buttonMinus.alpha = if (quantity > 1) 1f else 0.4f
        binding.buttonAddCart.text = getString(R.string.opt_add_cart, total.toWon())
    }

    private fun addToCart() {
        CartManager.add(
            menu = item,
            quantity = quantity,
            options = MenuOptions.describe(item, selectionWithRequest()),
            unitPrice = MenuOptions.unitPrice(item, selection)
        )
        Snackbar.make(binding.root, getString(R.string.opt_added, item.name, quantity), Snackbar.LENGTH_LONG)
            .setAnchorView(binding.bottomBar)
            .setAction(R.string.opt_view_cart) { MainActivity.openTab(this, MainActivity.TAB_CART) }
            .show()
        // 담은 뒤 수량은 1로
        quantity = 1
        updateTotal()
    }

    /** 요청사항 칸의 글자를 옵션에 넣기 */
    private fun selectionWithRequest(): MenuOptions.Selection =
        selection.copy(request = binding.editRequest.text?.toString()?.trim().orEmpty().take(MenuOptions.MAX_REQUEST_LENGTH))

    private fun updateCartBadge() {
        val count = CartManager.totalCount()
        binding.badgeCart.visibility = if (count > 0) View.VISIBLE else View.GONE
        binding.badgeCart.text = if (count > 99) "99+" else count.toString()
    }

    companion object {
        private const val EXTRA_MENU_ID = "menu_id"
        private const val MAX_QUANTITY = 20

        fun start(context: Context, menuId: String) {
            context.startActivity(Intent(context, MenuDetailActivity::class.java).putExtra(EXTRA_MENU_ID, menuId))
        }
    }
}