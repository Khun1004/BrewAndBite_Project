package com.example.brewandbite.favorite

import com.example.brewandbite.auth.AuthManager
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.data.MenuRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * 찜한 메뉴 (Firebase Realtime Database 의 favorites/{회원ID}/{메뉴ID} = 찜한 시각).
 *
 * 로그인하면 자동으로 불러오고(BrewBiteApp 에서 연결), 로그아웃하면 비운다.
 * 화면은 FavoriteManager.addListener { ... } 로 바뀔 때마다 다시 그리면 된다.
 *
 * ───────── 사용법 ─────────
 *  FavoriteManager.isFavorite(item.id)      찜했는지
 *  FavoriteManager.toggle(item.id) { ok -> } 찜 / 찜 해제
 *  FavoriteManager.favoriteItems()          찜한 메뉴 목록 (최근에 찜한 것이 위)
 *  FavoriteManager.count()                  찜한 개수
 * ─────────────────────────
 */
object FavoriteManager {

    fun interface FavoriteListener {
        fun onFavoritesChanged()
    }

    /** 메뉴ID → 찜한 시각 */
    private val favorites = LinkedHashMap<String, Long>()
    private val listeners = mutableSetOf<FavoriteListener>()
    private var loadedUid: String? = null

    private fun myRef() = AuthManager.currentUid()?.let {
        FirebaseDatabase.getInstance().getReference("favorites").child(it)
    }

    fun isFavorite(menuId: String): Boolean = favorites.containsKey(menuId)

    fun count(): Int = favorites.size

    /** 찜한 메뉴 목록 (최근에 찜한 것이 위, 메뉴판에서 없어진 메뉴는 빼고) */
    fun favoriteItems(): List<MenuItem> =
        favorites.entries.sortedByDescending { it.value }
            .mapNotNull { MenuRepository.findItem(it.key) }

    /** 로그인 / 로그아웃 될 때 호출 (BrewBiteApp) */
    fun onAuthChanged() {
        val uid = AuthManager.currentUid()
        if (uid == null) {
            loadedUid = null
            favorites.clear()
            notifyChanged()
            return
        }
        if (uid == loadedUid) return
        loadedUid = uid
        myRef()?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (loadedUid != uid) return   // 그 사이 다른 계정으로 바뀜
                favorites.clear()
                snapshot.children.forEach { child ->
                    val key = child.key ?: return@forEach
                    favorites[key] = (child.value as? Long) ?: 0L
                }
                notifyChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                loadedUid = null   // 다음에 다시 시도
            }
        })
    }

    /**
     * 찜 / 찜 해제. 화면은 바로 바뀌고(먼저 반영), 저장에 실패하면 원래대로 돌린다.
     * @return false = 로그인이 안 되어 있어서 못 함
     */
    fun toggle(menuId: String, onDone: (success: Boolean) -> Unit = {}): Boolean {
        val ref = myRef() ?: return false
        val wasFavorite = isFavorite(menuId)
        if (wasFavorite) {
            favorites.remove(menuId)
        } else {
            favorites[menuId] = System.currentTimeMillis()
        }
        notifyChanged()

        val task = if (wasFavorite) ref.child(menuId).removeValue()
        else ref.child(menuId).setValue(favorites[menuId])
        task.addOnSuccessListener { onDone(true) }
            .addOnFailureListener {
                // 실패하면 되돌리기
                if (wasFavorite) favorites[menuId] = System.currentTimeMillis() else favorites.remove(menuId)
                notifyChanged()
                onDone(false)
            }
        return true
    }

    fun addListener(listener: FavoriteListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: FavoriteListener) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it.onFavoritesChanged() }
    }
}