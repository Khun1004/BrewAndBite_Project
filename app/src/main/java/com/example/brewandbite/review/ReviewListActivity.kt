package com.example.brewandbite.review

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.brewandbite.R
import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.common.applyStatusBarPadding
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.data.MenuRepository
import com.example.brewandbite.databinding.ActivityReviewListBinding
import com.example.brewandbite.databinding.ItemReviewBinding
import com.example.brewandbite.order.OrderHistory
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * ⭐ 리뷰 전체 화면 — 메뉴의 별점(★ 4.6)이나 [리뷰 전체 보기]를 누르면 열린다.
 *  - 평균 별점 · 별점별 막대 · 리뷰 수 · 사진 리뷰 수
 *  - 정렬 칩: 최신순 · 별점 높은 순 · 별점 낮은 순 · 📷 사진 리뷰만
 *  - 다른 사람들의 리뷰 전부 (📷 사진을 누르면 크게) · 내 리뷰는 맨 위 + 수정 · 삭제
 *  - 헤더 오른쪽 [✏️ 쓰기] → 리뷰 쓰기 (주문한 메뉴만)
 *
 * 여는 법: ReviewListActivity.start(context, 메뉴ID)
 */
class ReviewListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewListBinding
    private lateinit var item: MenuItem

    private enum class Sort(val label: Int) {
        LATEST(R.string.review_sort_latest),
        HIGH(R.string.review_sort_high),
        LOW(R.string.review_sort_low),
        PHOTO(R.string.review_sort_photo)
    }

    private var sort = Sort.LATEST
    private val listener = ReviewManager.ReviewListener { render() }
    private val historyListener = OrderHistory.HistoryListener { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val found = MenuRepository.findItem(intent.getStringExtra(EXTRA_MENU_ID).orEmpty())
        if (found == null) {
            finish()
            return
        }
        item = found
        enableEdgeToEdge()
        binding = ActivityReviewListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
        binding.headerBox.applyStatusBarPadding()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        binding.buttonBack.setOnClickListener { finish() }
        binding.textSubtitle.text = "${item.emoji} ${item.name}"
        binding.headerRight.setOnClickListener { ReviewEditor.open(this, binding.root, item) }
        setupSortChips()
    }

    override fun onStart() {
        super.onStart()
        if (!::binding.isInitialized) return
        ReviewManager.addListener(listener)
        OrderHistory.addListener(historyListener)
        render()
    }

    override fun onStop() {
        super.onStop()
        ReviewManager.removeListener(listener)
        OrderHistory.removeListener(historyListener)
    }

    private fun setupSortChips() {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        fun colors(on: Int, off: Int) = ColorStateList(
            states, intArrayOf(ContextCompat.getColor(this, on), ContextCompat.getColor(this, off))
        )
        Sort.entries.forEach { s ->
            binding.chipsSort.addView(Chip(this).apply {
                setText(s.label)
                isCheckable = true
                isCheckedIconVisible = false
                isChecked = s == sort
                chipBackgroundColor = colors(R.color.bb_espresso, R.color.white)
                setTextColor(colors(R.color.bb_cream, R.color.bb_brown))
                chipStrokeColor = colors(R.color.bb_espresso, R.color.bb_divider)
                chipStrokeWidth = resources.displayMetrics.density
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        sort = s
                        render()
                    }
                }
            })
        }
    }

    private fun render() {
        val all = ReviewManager.reviews(item.id)
        val count = all.size
        val photoCount = all.count { it.photoCount > 0 }
        val uid = AuthManager.currentUid()

        binding.textTitle.text = getString(R.string.review_list_title, count)
        binding.headerRight.alpha = if (ReviewManager.canWrite(item.id)) 1f else 0.6f

        // 평균 + 막대
        binding.summaryBox.visibility = if (count > 0) View.VISIBLE else View.GONE
        if (count > 0) {
            binding.textAverage.text = ReviewManager.ratingText(item)
            binding.starsAverage.showStars(ReviewManager.average(item.id) ?: 0.0, 16)
            binding.textAverageCount.text = getString(R.string.review_list_count, count, photoCount)
            binding.barsContainer.drawRatingBars(ReviewManager.distribution(item.id), count)
        }

        // 정렬 · 사진만
        val sorted = when (sort) {
            Sort.LATEST -> all.sortedByDescending { it.createdAt }
            Sort.HIGH -> all.sortedWith(compareByDescending<Review> { it.rating }.thenByDescending { it.createdAt })
            Sort.LOW -> all.sortedWith(compareBy<Review> { it.rating }.thenByDescending { it.createdAt })
            Sort.PHOTO -> all.filter { it.photoCount > 0 }.sortedByDescending { it.createdAt }
        }
        // 내 리뷰는 맨 위
        val list = sorted.filter { it.uid == uid } + sorted.filter { it.uid != uid }

        binding.reviewList.removeAllViews()
        list.forEach { r ->
            val v = ItemReviewBinding.inflate(layoutInflater, binding.reviewList, false)
            v.bindReview(
                item.id, r, r.uid == uid,
                onEdit = { ReviewEditor.edit(this, item, r.id) },
                onDelete = { confirmDelete(r) }
            )
            binding.reviewList.addView(v.root)
        }
        binding.reviewList.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        binding.textEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.textEmpty.setText(
            when {
                count == 0 -> R.string.review_empty
                sort == Sort.PHOTO -> R.string.review_empty_photo
                else -> R.string.review_empty
            }
        )
    }

    private fun confirmDelete(r: Review) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.review_delete_title)
            .setNegativeButton(R.string.cart_cancel, null)
            .setPositiveButton(R.string.my_delete) { _, _ ->
                ReviewManager.delete(item.id, r.id) { ok ->
                    Toast.makeText(this, if (ok) R.string.review_deleted else R.string.review_failed, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    companion object {
        private const val EXTRA_MENU_ID = "menu_id"

        fun start(context: Context, menuId: String) {
            context.startActivity(Intent(context, ReviewListActivity::class.java).putExtra(EXTRA_MENU_ID, menuId))
        }
    }
}