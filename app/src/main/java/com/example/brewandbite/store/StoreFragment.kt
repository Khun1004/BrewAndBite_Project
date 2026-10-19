package com.example.brewandbite.store

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.example.brewandbite.R
import com.example.brewandbite.common.setup
import com.example.brewandbite.data.StoreInfo
import com.example.brewandbite.data.toWon
import com.example.brewandbite.databinding.FragmentStoreBinding

/**
 * Store 탭: 영업 상태 / BREW & BITE 의미 / 주소·전화 / 운영 시간 / 배달 정보 / 매장 서비스
 * 매장 정보는 data/StoreInfo.kt 에서 바꿀 수 있다.
 */
class StoreFragment : Fragment() {

    private var _binding: FragmentStoreBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 공통 헤더 (Cart / Order / My Page와 같은 모양)
        binding.pageHeader.setup(
            R.string.header_store_eyebrow, R.string.header_store_title, R.drawable.ic_store,
            getString(R.string.header_store_subtitle)
        )

        setupContact()
        setupHoursTable()
        setupDelivery()
    }

    override fun onResume() {
        super.onResume()
        updateOpenStatus()   // 화면에 돌아올 때마다 영업 상태를 다시 계산
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) updateOpenStatus()   // 탭을 다시 눌렀을 때도
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ───────── ① 영업 상태 ─────────

    private fun updateOpenStatus() {
        val b = _binding ?: return
        val today = StoreInfo.todayHours()
        val open = StoreInfo.isOpenNow()

        val main = ContextCompat.getColor(requireContext(), if (open) R.color.bb_open else R.color.bb_closed)
        val soft = ContextCompat.getColor(requireContext(), if (open) R.color.bb_open_bg else R.color.bb_closed_bg)
        ViewCompat.setBackgroundTintList(b.dotStatus, ColorStateList.valueOf(main))
        ViewCompat.setBackgroundTintList(b.statusCircle, ColorStateList.valueOf(soft))

        b.textStatus.setText(if (open) R.string.store_open_now else R.string.store_closed_now)
        b.textStatus.setTextColor(main)
        b.textStatusDetail.text = when {
            open -> getString(R.string.store_closes_at, today.close)
            isBeforeOpen(today.open) -> getString(R.string.store_opens_at, today.open)
            else -> getString(R.string.store_closed_today)
        }
        b.textTodayHours.text = getString(R.string.store_today_hours, today.open, today.close)
    }

    private fun isBeforeOpen(openTime: String): Boolean {
        val now = java.util.Calendar.getInstance()
        val minutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val (h, m) = openTime.split(":").map { it.toInt() }
        return minutes < h * 60 + m
    }

    // ───────── ③ 주소 · 전화 ─────────

    private fun setupContact() {
        binding.textStoreName.text = StoreInfo.NAME
        binding.textAddress.text = StoreInfo.ADDRESS
        binding.textAddressDetail.text = StoreInfo.ADDRESS_DETAIL
        binding.textPhone.text = StoreInfo.PHONE

        // 주소 복사
        binding.buttonCopyAddress.setOnClickListener {
            val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("address", "${StoreInfo.ADDRESS} ${StoreInfo.ADDRESS_DETAIL}"))
            Toast.makeText(requireContext(), R.string.store_copied, Toast.LENGTH_SHORT).show()
        }

        // 지도 앱 열기
        binding.buttonMap.setOnClickListener {
            val uri = Uri.parse("geo:0,0?q=" + Uri.encode(StoreInfo.ADDRESS))
            openOrToast(Intent(Intent.ACTION_VIEW, uri), R.string.store_no_map_app)
        }

        // 전화 앱에 번호 넣기 (바로 걸리지 않고, 사용자가 통화 버튼을 누름)
        binding.buttonCall.setOnClickListener {
            openOrToast(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + StoreInfo.PHONE)), R.string.store_no_phone_app)
        }
    }

    private fun openOrToast(intent: Intent, failMessage: Int) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), failMessage, Toast.LENGTH_SHORT).show()
        }
    }

    // ───────── ④ 운영 시간 표 ─────────

    private fun setupHoursTable() {
        val context = requireContext()
        val today = StoreInfo.todayHours()
        val density = resources.displayMetrics.density
        val table = binding.hoursTable
        table.removeAllViews()

        StoreInfo.hours.forEachIndexed { index, hours ->
            val isToday = hours == today

            if (index > 0) {
                // 줄 사이 구분선
                table.addView(View(context).apply {
                    setBackgroundColor(ContextCompat.getColor(context, R.color.bb_divider))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * density).toInt())
                })
            }

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
            }

            val label = TextView(context).apply {
                text = hours.label
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, if (isToday) R.color.bb_brown else R.color.bb_espresso))
                if (isToday) setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(label)

            if (isToday) {
                row.addView(TextView(context).apply {
                    setText(R.string.store_today)
                    textSize = 11f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(context, R.color.bb_nav_active))
                    setBackgroundResource(R.drawable.bg_chip_selected)
                    setPadding((10 * density).toInt(), (3 * density).toInt(), (10 * density).toInt(), (3 * density).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = (10 * density).toInt() }
                })
            }

            row.addView(TextView(context).apply {
                text = getString(R.string.store_hours_value, hours.open, hours.close)
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, if (isToday) R.color.bb_brown else R.color.bb_muted))
                if (isToday) setTypeface(typeface, Typeface.BOLD)
            })

            table.addView(row)
        }
    }

    // ───────── ⑤ 배달 정보 ─────────

    private fun setupDelivery() {
        val context = requireContext()
        val ok = StoreInfo.DELIVERY_AVAILABLE

        binding.textDeliveryBadge.setText(if (ok) R.string.store_delivery_ok else R.string.store_delivery_no)
        binding.textDeliveryBadge.setTextColor(ContextCompat.getColor(context, if (ok) R.color.bb_open else R.color.bb_closed))
        binding.textDeliveryBadge.setBackgroundResource(R.drawable.bg_qty)
        ViewCompat.setBackgroundTintList(
            binding.textDeliveryBadge,
            ColorStateList.valueOf(ContextCompat.getColor(context, if (ok) R.color.bb_open_bg else R.color.bb_closed_bg))
        )

        binding.textDeliveryArea.text = StoreInfo.DELIVERY_AREA
        binding.textDeliveryTime.text = StoreInfo.DELIVERY_TIME
        binding.textDeliveryMin.text = StoreInfo.DELIVERY_MIN_ORDER.toWon()
        binding.textDeliveryFee.text = getString(
            R.string.store_delivery_fee_value,
            StoreInfo.DELIVERY_FEE.toWon(),
            StoreInfo.DELIVERY_FREE_OVER.toWon()
        )
    }
}