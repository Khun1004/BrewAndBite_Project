package com.example.brewandbite.notice

import android.view.View

/**
 * 🔔 버튼 위 빨간 점: 안 읽은 알림이 있으면 보이기.
 * 사용법: dotBell.bindNoticeDot()   (화면의 onStart 와 NoticeListener 안에서 호출)
 */
fun View.bindNoticeDot() {
    visibility = if (NoticeManager.unreadCount() > 0) View.VISIBLE else View.GONE
}