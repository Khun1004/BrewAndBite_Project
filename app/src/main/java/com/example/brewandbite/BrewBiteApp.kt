package com.example.brewandbite

import android.app.Application
import com.example.brewandbite.cart.CartManager
import com.example.brewandbite.data.MenuRepository
import com.example.brewandbite.favorite.FavoriteManager
import com.example.brewandbite.notice.NoticeManager
import com.example.brewandbite.order.OrderHistory
import com.example.brewandbite.order.OrderManager
import com.example.brewandbite.reward.RewardManager
import com.example.brewandbite.review.ReviewManager
import com.google.firebase.auth.FirebaseAuth

/**
 * 앱이 시작될 때 가장 먼저 실행되는 곳. 메뉴 · 장바구니 · 주문 · 찜 목록 · 스탬프/쿠폰 · 알림 · 리뷰를 여기서 준비한다.
 * 로그아웃하면 휴대폰에 남은 장바구니 · 진행 중 주문도 지운다 (다시 로그인하면 주문 기록은 Firebase 에서 다시 불러옴).
 */
class BrewBiteApp : Application() {

    /** 바로 전에 로그인해 있던 회원 (로그아웃 · 다른 계정 로그인 알아채기) */
    private var lastUid: String? = null

    override fun onCreate() {
        super.onCreate()
        CartManager.init(this)
        OrderManager.init(this)
        MenuRepository.start()   // 메뉴 (Firebase menu → 없으면 앱 안의 기본 메뉴)
        ReviewManager.start()   // 모든 메뉴의 리뷰 · 평균 별점 불러오기

        // 로그인 안 한 채로 앱을 켰는데 휴대폰에 예전 주문이 남아 있으면 지우기 (주문은 로그인해야 할 수 있음)
        lastUid = FirebaseAuth.getInstance().currentUser?.uid
        if (lastUid == null) OrderManager.clear()

        // 로그인 / 로그아웃 될 때마다 찜한 메뉴 · 스탬프·쿠폰 · 알림 · 주문 기록을 다시 불러온다
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            // 로그아웃했거나 다른 계정으로 바뀌면 → 이 휴대폰에 남은 장바구니 · 진행 중 주문 지우기
            if (lastUid != null && lastUid != uid) clearLocalData()
            lastUid = uid

            FavoriteManager.onAuthChanged()
            RewardManager.onAuthChanged()
            NoticeManager.onAuthChanged()
            OrderHistory.onAuthChanged()
        }
    }

    /** 휴대폰에만 저장된 회원 데이터 (Firebase 데이터는 계정에 그대로 남아 있음) */
    private fun clearLocalData() {
        CartManager.clear()     // 장바구니
        OrderManager.clear()    // Order 탭의 진행 중 주문
    }
}