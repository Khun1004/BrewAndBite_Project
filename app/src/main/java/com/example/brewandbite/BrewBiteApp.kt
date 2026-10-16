package com.example.brewandbite

import android.app.Application
import com.example.brewandbite.cart.CartManager

/** 앱이 시작될 때 가장 먼저 실행되는 곳. 장바구니를 여기서 준비한다. */
class BrewBiteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CartManager.init(this)
    }
}