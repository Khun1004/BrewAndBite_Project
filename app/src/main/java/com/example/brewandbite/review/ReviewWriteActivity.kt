package com.example.brewandbite.review

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.brewandbite.databinding.ActivityReviewWriteBinding
import com.example.brewandbite.order.OrderHistory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * ✏️ 리뷰 쓰기 / 수정 화면 (activity_review_write.xml)
 *  ① 메뉴 · 주문 정보
 *  ② 별 5개 (누르면 별점 + "최고예요! 😍")
 *  ③ 📷 사진 최대 3장: [📷 추가] → 앨범 또는 파일에서 고르기 · 사진의 ✕ 로 빼기
 *  ④ 글 10~200자
 *  [등록하기] → ReviewManager.save (글 + 사진을 한 번에 저장) → 닫기
 *
 * 직접 열지 말고 ReviewEditor.open(...) 을 쓰면 로그인 · 주문 여부를 먼저 확인해 준다.
 */
class ReviewWriteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewWriteBinding
    private lateinit var item: MenuItem
    private var orderNo: String? = null

    private var rating = 0
    private val stars = mutableListOf<ImageView>()

    /** 고른 사진 (Base64 글자 · 화면용 그림) */
    private val photos = mutableListOf<String>()
    private val previews = mutableListOf<Bitmap>()
    private var saving = false

    /** 앨범에서 여러 장 고르기 (남은 칸만큼) */
    private val albumPicker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ReviewPhotos.MAX_PHOTOS)
    ) { uris -> addPhotos(uris) }

    /** 파일에서 고르기 (컴퓨터에서 에뮬레이터로 끌어다 놓은 사진은 다운로드 폴더에 있음) */
    private val filePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        addPhotos(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val found = MenuRepository.findItem(intent.getStringExtra(EXTRA_MENU_ID).orEmpty())
        if (found == null || !AuthManager.isLoggedIn()) {
            finish()
            return
        }
        item = found
        orderNo = intent.getStringExtra(EXTRA_ORDER_NO)

        enableEdgeToEdge()
        binding = ActivityReviewWriteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 키보드가 올라오면 아래 [등록하기]도 같이 올라오게
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
        binding.headerBox.applyStatusBarPadding()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        binding.buttonBack.setOnClickListener { finish() }

        val old = ReviewManager.myReview(item.id)
        binding.textTitle.setText(if (old == null) R.string.review_dialog_title else R.string.review_dialog_edit_title)
        binding.textSubtitle.text = "${item.emoji} ${item.name}"
        binding.buttonSubmit.setText(if (old == null) R.string.review_submit_long else R.string.review_edit_long)

        // ① 메뉴 · 주문 정보
        binding.textEmoji.text = item.emoji
        binding.textMenu.text = item.name
        val order = orderNo?.let { OrderHistory.find(it) }
            ?: old?.orderNo?.takeIf { it.isNotEmpty() }?.let { OrderHistory.find(it) }
            ?: OrderHistory.reviewableOrder(item.id)
        val options = order?.items?.firstOrNull { it.menuId == item.id }?.options.orEmpty()
            .substringBefore("요청:").trim().removeSuffix("·").trim()
        binding.textOrder.text = when {
            order == null -> ""
            options.isBlank() -> getString(R.string.review_order_info, order.dateText)
            else -> getString(R.string.review_order_info_options, order.dateText, options)
        }
        binding.textOrder.visibility = if (order == null) View.GONE else View.VISIBLE

        // ② 별점
        buildStars()
        rating = old?.rating ?: 0
        paintStars()

        // ④ 글
        binding.editReview.setText(old?.text.orEmpty())

        // ③ 사진: 수정이면 예전 사진 불러오기
        renderPhotos()
        if (old != null && old.photoCount > 0) {
            ReviewPhotos.loadRaw(item.id, old.uid) { list ->
                if (photos.isNotEmpty()) return@loadRaw   // 그 사이 새로 고른 사진이 있으면 그대로
                list.take(ReviewPhotos.MAX_PHOTOS).forEach { b64 ->
                    val bmp = ReviewPhotos.decode(b64) ?: return@forEach
                    photos += b64
                    previews += bmp
                }
                renderPhotos()
            }
        }

        binding.buttonSubmit.setOnClickListener { submit() }
    }

    // ───────── ② 별점 ─────────

    private fun buildStars() {
        val d = resources.displayMetrics.density
        for (score in 1..5) {
            val star = ImageView(this).apply {
                val pad = (4 * d).toInt()
                setPadding(pad, pad, pad, pad)
                contentDescription = getString(R.string.review_star_desc, score)
                setOnClickListener {
                    rating = score
                    paintStars()
                    // 눌린 별이 톡 튀어 오르기
                    animate().scaleX(1.25f).scaleY(1.25f).setDuration(110)
                        .withEndAction { animate().scaleX(1f).scaleY(1f).setDuration(110).start() }.start()
                }
            }
            stars += star
            binding.starPicker.addView(star, LinearLayout.LayoutParams((50 * d).toInt(), (50 * d).toInt()))
        }
    }

    private fun paintStars() {
        stars.forEachIndexed { i, star ->
            val on = i < rating
            star.setImageResource(if (on) R.drawable.ic_star else R.drawable.ic_star_outline)
            star.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, if (on) R.color.bb_star else R.color.bb_beige)
            )
        }
        binding.textRatingLabel.setText(RATING_LABELS[rating])
    }

    // ───────── ③ 사진 ─────────

    /** [📷 추가 1/3] 칸 + 고른 사진 (✕ 로 빼기 · 누르면 크게) */
    private fun renderPhotos() {
        val d = resources.displayMetrics.density
        val size = (88 * d).toInt()
        val gap = (10 * d).toInt()
        binding.photoList.removeAllViews()

        // [📷 추가] 칸 (3장이면 숨김)
        if (photos.size < ReviewPhotos.MAX_PHOTOS) {
            val add = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_photo_add)
                setOnClickListener { choosePhotoSource() }
            }
            add.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_camera)
                imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@ReviewWriteActivity, R.color.bb_brown))
            }, LinearLayout.LayoutParams((26 * d).toInt(), (26 * d).toInt()))
            add.addView(TextView(this).apply {
                text = getString(R.string.review_photo_count, photos.size, ReviewPhotos.MAX_PHOTOS)
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@ReviewWriteActivity, R.color.bb_brown))
            })
            binding.photoList.addView(add, LinearLayout.LayoutParams(size, size).apply { marginEnd = gap })
        }

        // 고른 사진들
        previews.forEachIndexed { index, bmp ->
            val box = FrameLayout(this)
            box.addView(ShapeableImageView(this).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.CENTER_CROP
                shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(14 * d).build()
                setOnClickListener { showPhotoLarge(this@ReviewWriteActivity, bmp) }
            }, FrameLayout.LayoutParams(size, size))
            // ✕ 빼기
            box.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_close)
                setBackgroundResource(R.drawable.bg_header_button)
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@ReviewWriteActivity, R.color.bb_espresso))
                imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@ReviewWriteActivity, R.color.bb_cream))
                val pad = (4 * d).toInt()
                setPadding(pad, pad, pad, pad)
                contentDescription = getString(R.string.review_photo_remove)
                setOnClickListener {
                    photos.removeAt(index)
                    previews.removeAt(index)
                    renderPhotos()
                }
            }, FrameLayout.LayoutParams((24 * d).toInt(), (24 * d).toInt(), Gravity.TOP or Gravity.END).apply {
                topMargin = (4 * d).toInt()
                marginEnd = (4 * d).toInt()
            })
            binding.photoList.addView(box, LinearLayout.LayoutParams(size, size).apply { marginEnd = gap })
        }
    }

    /** 앨범 / 파일 중에 고르기 (프로필 사진과 같은 방식) */
    private fun choosePhotoSource() {
        val items = arrayOf(getString(R.string.photo_pick), getString(R.string.photo_file))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.review_photo_title)
            .setItems(items) { _, which ->
                if (which == 0) albumPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                else filePicker.launch("image/*")
            }
            .show()
    }

    /** 고른 사진들을 줄여서 추가 (남은 칸까지만) */
    private fun addPhotos(uris: List<Uri>) {
        val room = ReviewPhotos.MAX_PHOTOS - photos.size
        if (uris.isEmpty() || room <= 0) return
        if (uris.size > room) Toast.makeText(this, getString(R.string.review_photo_max, ReviewPhotos.MAX_PHOTOS), Toast.LENGTH_SHORT).show()
        uris.take(room).forEach { uri ->
            ReviewPhotos.encode(this, uri) { b64 ->
                if (b64 == null) {
                    Toast.makeText(this, R.string.photo_failed, Toast.LENGTH_SHORT).show()
                    return@encode
                }
                val bmp = ReviewPhotos.decode(b64) ?: return@encode
                if (photos.size >= ReviewPhotos.MAX_PHOTOS) return@encode
                photos += b64
                previews += bmp
                renderPhotos()
            }
        }
    }

    // ───────── 등록 ─────────

    private fun submit() {
        if (saving) return
        val text = binding.editReview.text?.toString()?.trim().orEmpty()
        when {
            rating == 0 -> {
                binding.textRatingLabel.setText(R.string.review_err_rating)
                binding.scrollWrite.smoothScrollTo(0, 0)
                return
            }
            text.length < ReviewManager.MIN_LENGTH -> {
                binding.layoutReview.error = getString(R.string.review_err_short)
                return
            }
        }
        binding.layoutReview.error = null
        saving = true
        binding.buttonSubmit.isEnabled = false
        binding.buttonSubmit.setText(R.string.review_saving)
        ReviewManager.save(item.id, rating, text, orderNo, photos.toList()) { ok ->
            saving = false
            Toast.makeText(this, if (ok) R.string.review_saved else R.string.review_failed, Toast.LENGTH_SHORT).show()
            if (ok) finish() else {
                binding.buttonSubmit.isEnabled = true
                binding.buttonSubmit.setText(R.string.review_submit_long)
            }
        }
    }

    companion object {
        private const val EXTRA_MENU_ID = "menu_id"
        private const val EXTRA_ORDER_NO = "order_no"

        private val RATING_LABELS = intArrayOf(
            R.string.review_rating_0, R.string.review_rating_1, R.string.review_rating_2,
            R.string.review_rating_3, R.string.review_rating_4, R.string.review_rating_5
        )

        fun start(context: Context, menuId: String, orderNo: String? = null) {
            context.startActivity(
                Intent(context, ReviewWriteActivity::class.java)
                    .putExtra(EXTRA_MENU_ID, menuId)
                    .putExtra(EXTRA_ORDER_NO, orderNo)
            )
        }
    }
}