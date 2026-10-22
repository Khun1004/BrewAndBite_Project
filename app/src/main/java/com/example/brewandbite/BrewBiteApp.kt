package com.example.brewandbite

import android.app.Application
import com.example.brewandbite.cart.CartManager
import com.example.brewandbite.favorite.FavoriteManager
import com.google.firebase.auth.FirebaseAuth


/** 앱이 시작될 때 가장 먼저 실행되는 곳. 장바구니와 찜 목록을 여기서 준비한다. */
class BrewBiteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CartManager.init(this)
        // 로그인 / 로그아웃 될 때마다 찜한 메뉴를 다시 불러온다
        FirebaseAuth.getInstance().addAuthStateListener { FavoriteManager.onAuthChanged() }
    }
}
