package com.example.brewandbite.mypage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * 프로필 사진 도우미.
 *
 * Firebase Storage 는 새 프로젝트에서 유료(Blaze) 요금제가 필요해서,
 * 사진을 320×320 정사각형 JPEG(약 20~40KB)로 줄인 뒤 Base64 글자로 바꿔
 * Realtime Database 의 users/{uid}/photo 에 저장한다.
 */
object ProfilePhoto {

    private const val SIZE = 320          // 저장할 사진 크기 (px)
    private const val QUALITY = 80        // JPEG 품질

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** 앨범에서 고른 사진 → 정사각형으로 자르고 줄여서 Base64 글자로 (백그라운드에서 처리) */
    fun encode(context: Context, uri: Uri, onResult: (String?) -> Unit) {
        val appContext = context.applicationContext
        worker.execute {
            val result = try {
                val bitmap = decodeSmall(appContext, uri)
                val square = cropSquare(bitmap, SIZE)
                val out = ByteArrayOutputStream()
                square.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            } catch (e: Exception) {
                null
            }
            main.post { onResult(result) }
        }
    }

    /** 저장된 Base64 글자 → 화면에 보여 줄 이미지 (잘못된 값이면 null) */
    fun decode(base64: String): Bitmap? = try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }

    /** 큰 사진을 처음부터 작게 읽기 (메모리 절약). API 28+ 는 사진 회전 정보도 자동 반영 */
    private fun decodeSmall(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = SIZE * 2f / min(w, h)          // 짧은 쪽이 SIZE*2 정도가 되게
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        // API 24~27: 크기만 먼저 읽고, 알맞은 배율로 다시 읽기
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalStateException("decode failed")
    }

    /** 가운데를 정사각형으로 잘라 size×size 로 */
    private fun cropSquare(src: Bitmap, size: Int): Bitmap {
        val side = min(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val square = Bitmap.createBitmap(src, x, y, side, side)
        return if (side == size) square else Bitmap.createScaledBitmap(square, size, size, true)
    }
}