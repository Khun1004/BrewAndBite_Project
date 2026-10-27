package com.example.brewandbite.review

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.LruCache
import com.google.firebase.database.FirebaseDatabase
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * 📷 리뷰 사진 (최대 3장).
 *
 * Firebase Storage 는 유료(Blaze)라서 프로필 사진처럼 사진을 줄여서(긴 쪽 720px JPEG) Base64 글자로 바꿔
 * Realtime Database 의 reviewPhotos/{메뉴ID}/{리뷰ID} = ["사진1", "사진2", ...] 에 저장한다.
 * 리뷰 글(reviews)과 따로 두는 이유: 앱 시작 때 모든 리뷰를 불러오는데, 사진까지 같이 받으면 너무 무거워서.
 * 사진은 화면에 보일 때만 따로 불러오고, 한 번 불러온 사진은 메모리에 잠깐 보관(캐시)한다.
 */
object ReviewPhotos {

    const val MAX_PHOTOS = 3
    private const val LONG_SIDE = 720   // 긴 쪽 px
    private const val QUALITY = 75      // JPEG 품질

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** "메뉴ID/리뷰ID/수정시각" → 사진들 */
    private val cache = LruCache<String, List<Bitmap>>(24)

    private fun ref(menuId: String, reviewId: String) =
        FirebaseDatabase.getInstance().getReference("reviewPhotos").child(menuId).child(reviewId)

    /** Firebase 에 쓸 경로 (ReviewManager 가 리뷰와 한 번에 저장할 때 사용) */
    fun path(menuId: String, reviewId: String) = "reviewPhotos/$menuId/$reviewId"

    // ───────── 고른 사진 → Base64 ─────────

    /** 앨범에서 고른 사진을 줄여서 Base64 글자로 (백그라운드). 실패하면 null */
    fun encode(context: Context, uri: Uri, onResult: (String?) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val result = try {
                val bitmap = decodeScaled(app, uri)
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            } catch (e: Exception) {
                null
            }
            main.post { onResult(result) }
        }
    }

    fun decode(base64: String): Bitmap? = try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }

    /** 긴 쪽이 LONG_SIDE 가 되게 줄여서 읽기 (API 28+ 는 회전 정보도 반영) */
    private fun decodeScaled(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = LONG_SIDE.toFloat() / max(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LONG_SIDE) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalStateException("decode failed")
        val scale = LONG_SIDE.toFloat() / max(decoded.width, decoded.height)
        return if (scale >= 1f) decoded
        else Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
    }

    // ───────── 불러오기 ─────────

    /** 리뷰 사진을 Base64 글자 그대로 (리뷰 수정 화면에서 기존 사진 채우기) */
    fun loadRaw(menuId: String, reviewId: String, onResult: (List<String>) -> Unit) {
        ref(menuId, reviewId).get()
            .addOnSuccessListener { snap -> onResult(snap.children.mapNotNull { it.getValue(String::class.java) }) }
            .addOnFailureListener { onResult(emptyList()) }
    }

    /** 리뷰 사진 (화면에 보여 줄 그림). review.updatedAt 이 바뀌면 새로 불러옴 */
    fun load(menuId: String, review: Review, onResult: (List<Bitmap>) -> Unit) {
        if (review.photoCount <= 0) return onResult(emptyList())
        val key = "$menuId/${review.id}/${review.updatedAt}"
        cache.get(key)?.let { return onResult(it) }
        loadRaw(menuId, review.id) { list ->
            worker.execute {
                val bitmaps = list.mapNotNull { decode(it) }
                main.post {
                    cache.put(key, bitmaps)
                    onResult(bitmaps)
                }
            }
        }
    }
}