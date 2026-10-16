package com.example.brewandbite.data

import java.util.Calendar

/**
 * 매장 정보 (Store 화면에서 사용).
 * ⚠️ 아래 값은 예시입니다. 실제 매장 정보로 바꿔서 쓰세요. 이 파일만 고치면 화면이 바뀝니다.
 */
object StoreInfo {

    const val NAME = "Brew & Bite 영진점"
    const val ADDRESS = "대구광역시 북구 복현로 35"
    const val ADDRESS_DETAIL = "영진전문대학교 정문 앞 1층"
    const val PHONE = "053-000-0000"

    /** 요일별 운영 시간 (시간은 24시간제 "HH:mm") */
    data class Hours(val label: String, val days: List<Int>, val open: String, val close: String)

    val hours = listOf(
        Hours(
            "월 – 금",
            listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY),
            "08:00", "22:00"
        ),
        Hours("토요일", listOf(Calendar.SATURDAY), "09:00", "22:00"),
        Hours("일요일", listOf(Calendar.SUNDAY), "10:00", "21:00")
    )

    // 배달 정보
    const val DELIVERY_AVAILABLE = true
    const val DELIVERY_AREA = "매장 반경 3km"
    const val DELIVERY_MIN_ORDER = 12000
    const val DELIVERY_FEE = 2500
    const val DELIVERY_FREE_OVER = 25000
    const val DELIVERY_TIME = "30 ~ 40분"

    /** 오늘 요일의 운영 시간 */
    fun todayHours(now: Calendar = Calendar.getInstance()): Hours {
        val day = now.get(Calendar.DAY_OF_WEEK)
        return hours.first { day in it.days }
    }

    /** 지금 영업 중인지 */
    fun isOpenNow(now: Calendar = Calendar.getInstance()): Boolean {
        val today = todayHours(now)
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return minutes in toMinutes(today.open) until toMinutes(today.close)
    }

    private fun toMinutes(time: String): Int {
        val (h, m) = time.split(":").map { it.toInt() }
        return h * 60 + m
    }
}