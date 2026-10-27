package com.example.brewandbite.search

import android.content.Context
import com.example.brewandbite.data.MenuItem
import com.example.brewandbite.data.MenuRepository
import com.example.brewandbite.menu.MenuOptions
import com.example.brewandbite.review.ReviewManager

/**
 * 🔍 메뉴 검색 엔진 + 최근 검색어.
 *
 *  - 한글로도 찾을 수 있게 메뉴마다 한글 이름 · 별명을 붙였다 (예: "아메리카노" → Americano, "크로와상" → Croissant)
 *  - 띄어쓰기 · 대소문자 상관없이 찾는다 ("카페 라떼" = "카페라떼" = "caffe latte")
 *  - 메뉴 이름 · 한글 이름 · 카테고리 · 설명 · 재료(빼기/추가)까지 찾는다 (예: "새우" → Cream Pasta)
 *  - 이름이 맞을수록 위에 (추천순). 가격 · 별점 순으로도 정렬 가능
 */
object MenuSearch {

    enum class Sort { RECOMMEND, PRICE_LOW, PRICE_HIGH, RATING }

    /** 메뉴ID → 한글 이름 · 별명 (Firebase 메뉴가 바뀌어도 ID 가 같으면 그대로 사용) */
    private val aliases = mapOf(
        "c1" to "아메리카노 아아 뜨아 블랙커피",
        "c2" to "카페라떼 라떼 카페 라테",
        "c3" to "카푸치노",
        "c4" to "바닐라라떼 바닐라 라떼",
        "c5" to "에스프레소 샷",
        "c6" to "카라멜마끼아또 캬라멜 마키아토",
        "c7" to "카페모카 모카 초코커피",
        "c8" to "콜드브루 더치커피",
        "c9" to "플랫화이트",
        "c10" to "아인슈페너 비엔나커피 크림커피",
        "d1" to "레몬에이드 레모네이드 에이드",
        "d2" to "딸기스무디 스무디 딸기",
        "d3" to "오렌지주스 주스 생과일",
        "t1" to "얼그레이 홍차",
        "t2" to "캐모마일 카모마일 허브티",
        "t3" to "페퍼민트 민트 허브티",
        "s1" to "뉴욕치즈케이크 치즈케이크 케이크",
        "s2" to "티라미수 케이크",
        "s3" to "딸기케이크 생크림케이크 케이크",
        "b1" to "크루아상 크로와상 버터",
        "b2" to "초코칩쿠키 쿠키 초코",
        "b3" to "베이글 크림치즈",
        "f1" to "클럽샌드위치 샌드위치",
        "f2" to "마르게리타피자 마르게리따 피자",
        "f3" to "클래식버거 버거 햄버거 수제버거",
        "f4" to "크림파스타 파스타 까르보나라",
        "f5" to "시저샐러드 샐러드"
    )

    /** 인기 검색어 (검색 화면 처음에 보여 줌) */
    val popularKeywords = listOf("아메리카노", "라떼", "치즈케이크", "크루아상", "콜드브루", "파스타", "레몬에이드", "버거")

    /** 띄어쓰기 · 대소문자 · 악센트(è, ä) 무시 */
    private fun norm(s: String): String = s.lowercase()
        .replace(" ", "")
        .replace("è", "e").replace("é", "e").replace("ä", "a")

    /** 이 메뉴의 한글 이름 (없으면 빈 글자) — 검색 결과에 "아메리카노" 처럼 보여 줄 때 */
    fun koreanName(item: MenuItem): String = aliases[item.id]?.substringBefore(" ").orEmpty()

    /**
     * 검색. 점수가 높은 순 (이름 > 한글 이름 > 카테고리 > 재료 > 설명)
     * @param categoryId null 이면 전체
     */
    fun search(query: String, categoryId: String? = null, sort: Sort = Sort.RECOMMEND): List<MenuItem> {
        val words = query.trim().split(" ").map { norm(it) }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val scored = MenuRepository.menuItems
            .filter { categoryId == null || it.categoryId == categoryId }
            .mapNotNull { item -> score(item, words)?.let { item to it } }
        return when (sort) {
            Sort.RECOMMEND -> scored.sortedWith(compareByDescending<Pair<MenuItem, Int>> { it.second }
                .thenByDescending { ReviewManager.ratingText(it.first).toDoubleOrNull() ?: it.first.rating })
            Sort.PRICE_LOW -> scored.sortedBy { it.first.price }
            Sort.PRICE_HIGH -> scored.sortedByDescending { it.first.price }
            Sort.RATING -> scored.sortedByDescending { ReviewManager.ratingText(it.first).toDoubleOrNull() ?: it.first.rating }
        }.map { it.first }
    }

    /** 검색어가 모두 어딘가에 들어 있어야 결과에 나옴. 어디에 맞았는지에 따라 점수 */
    private fun score(item: MenuItem, words: List<String>): Int? {
        val name = norm(item.name)
        val alias = norm(aliases[item.id].orEmpty())
        val category = MenuRepository.findCategory(item.categoryId).let { norm(it.name + it.nameKo) }
        val ing = MenuOptions.ingredients(item)
        val ingredients = norm((ing.removable + ing.extras.map { it.name }).joinToString(""))
        val description = norm(item.description)
        var total = 0
        for (w in words) {
            total += when {
                name.startsWith(w) || alias.startsWith(w) -> 100
                name.contains(w) -> 80
                alias.contains(w) -> 70
                category.contains(w) -> 40
                ingredients.contains(w) -> 30
                description.contains(w) -> 20
                else -> return null
            }
        }
        if (item.popular) total += 5
        return total
    }

    // ───────── 최근 검색어 (휴대폰에 저장) ─────────

    private const val PREF = "brewbite_search"
    private const val KEY_RECENT = "recent"
    private const val MAX_RECENT = 10
    private const val SEP = "\u001F"

    fun recent(context: Context): List<String> =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_RECENT, null)
            ?.split(SEP)?.filter { it.isNotBlank() }.orEmpty()

    /** 최근 검색어 맨 앞에 추가 (같은 말은 하나만) */
    fun addRecent(context: Context, query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val list = (listOf(q) + recent(context).filter { it != q }).take(MAX_RECENT)
        save(context, list)
    }

    fun removeRecent(context: Context, query: String) = save(context, recent(context).filter { it != query })

    fun clearRecent(context: Context) = save(context, emptyList())

    private fun save(context: Context, list: List<String>) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_RECENT, list.joinToString(SEP)).apply()
    }
}