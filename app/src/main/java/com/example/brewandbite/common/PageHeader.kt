package com.example.brewandbite.common

import androidx.annotation.StringRes
import com.example.brewandbite.databinding.LayoutPageHeaderBinding

/**
 * 탭 공통 헤더(layout_page_header.xml)에 글자를 넣는다.
 * Cart / Store / Order / My Page 화면의 onViewCreated에서 한 번 호출.
 *
 * 예) binding.pageHeader.setup(R.string.header_store_eyebrow, R.string.header_store_title, "🏪",
 *                              getString(R.string.header_store_subtitle))
 */
fun LayoutPageHeaderBinding.setup(
    @StringRes eyebrow: Int,
    @StringRes title: Int,
    emoji: String,
    subtitle: CharSequence? = null
) {
    root.applyStatusBarPadding()   // 헤더가 상태바 아래까지 이어지도록
    textEyebrow.setText(eyebrow)
    textTitle.setText(title)
    textHeaderEmoji.text = emoji
    if (subtitle != null) textSubtitle.text = subtitle
}