package com.example.brewandbite

import android.app.Application
import com.example.brewandbite.cart.CartManager
import com.example.brewandbite.favorite.FavoriteManager
import com.example.brewandbite.notice.NoticeManager
import com.example.brewandbite.order.OrderManager
import com.example.brewandbite.reward.RewardManager
import com.example.brewandbite.review.ReviewManager
import com.google.firebase.auth.FirebaseAuth

/** 앱이 시작될 때 가장 먼저 실행되는 곳. 장바구니 · 주문 · 찜 목록 · 스탬프/쿠폰 · 알림 · 리뷰를 여기서 준비한다. */
class BrewBiteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CartManager.init(this)
        OrderManager.init(this)
        ReviewManager.start()   // 모든 메뉴의 리뷰 · 평균 별점 불러오기
        // 로그인 / 로그아웃 될 때마다 찜한 메뉴 · 스탬프·쿠폰 · 알림을 다시 불러온다
        FirebaseAuth.getInstance().addAuthStateListener {
            FavoriteManager.onAuthChanged()
            RewardManager.onAuthChanged()
            NoticeManager.onAuthChanged()
        }
    }
}