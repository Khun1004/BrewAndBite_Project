package com.example.brewandbite.favorite

import android.widget.ImageView
import android.widget.Toast
import com.example.brewandbite.R
import com.example.brewandbite.data.MenuItem

/**
 * 메뉴 카드의 ♡ 버튼을 연결한다. (어댑터의 onBindViewHolder 에서 호출)
 *   buttonFavorite.bindFavorite(item)
 * 누르면 찜 / 찜 해제, 하트가 톡 튀는 애니메이션. 로그인 안 했으면 안내.
 */
fun ImageView.bindFavorite(item: MenuItem) {
    showFavorite(FavoriteManager.isFavorite(item.id))
    contentDescription = context.getString(R.string.fav_button_desc, item.name)

    setOnClickListener {
        val willBeFavorite = !FavoriteManager.isFavorite(item.id)
        val started = FavoriteManager.toggle(item.id) { ok ->
            if (!ok) Toast.makeText(context, R.string.fav_failed, Toast.LENGTH_SHORT).show()
        }
        if (!started) {
            Toast.makeText(context, R.string.fav_login_needed, Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }
        showFavorite(willBeFavorite)
        // 톡! 커졌다가 돌아오기
        animate().cancel()
        scaleX = 0.7f
        scaleY = 0.7f
        animate().scaleX(1f).scaleY(1f).setDuration(220).start()
        if (willBeFavorite) {
            Toast.makeText(context, context.getString(R.string.fav_added, item.name), Toast.LENGTH_SHORT).show()
        }
    }
}

private fun ImageView.showFavorite(favorite: Boolean) {
    setImageResource(if (favorite) R.drawable.ic_heart else R.drawable.ic_heart_outline)
}