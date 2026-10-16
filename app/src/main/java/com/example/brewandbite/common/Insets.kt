package com.example.brewandbite.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding

/**
 * 상태바(맨 위 시계/배터리 줄) 높이만큼 위쪽 여백을 더해 준다.
 * 탭 화면(Fragment)의 맨 바깥 View에서 한 번 호출하면 된다.
 */
fun View.applyStatusBarPadding() {
    val originalTop = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        v.updatePadding(top = originalTop + statusBar)
        insets
    }
    doOnAttach { ViewCompat.requestApplyInsets(it) }
}