package com.example.brewandbite.data

import java.text.NumberFormat
import java.util.Locale

/**
 * 홈 화면에서 쓰는 샘플 데이터.
 * 지금은 앱 안에 고정된 데이터를 쓰고, 이후 Firebase 연동 시 이 파일만 바꾸면 된다.
 */
object MenuRepository {

    val categories = listOf(
        Category("coffee", "Coffee", "커피", "☕"),
        Category("drinks", "Drinks", "음료", "🥤"),
        Category("tea", "Tea", "차", "🍵"),
        Category("dessert", "Dessert", "디저트", "🍰"),
        Category("bakery", "Bakery", "베이커리", "🥐"),
        Category("food", "Food", "식사", "🍔")
    )

    val menuItems = listOf(
        // Coffee
        MenuItem("c1", "Americano", "깔끔하고 진한 에스프레소에 물을 더한 기본 커피", 4500, 4.7, "coffee", "☕", popular = true),
        MenuItem("c2", "Caffè Latte", "부드러운 우유와 에스프레소의 조화", 5000, 4.8, "coffee", "🥛", popular = true),
        MenuItem("c3", "Cappuccino", "풍성한 우유 거품을 올린 진한 커피", 5000, 4.6, "coffee", "☕"),
        MenuItem("c4", "Vanilla Latte", "달콤한 바닐라 시럽을 더한 라떼", 5500, 4.7, "coffee", "🍦"),
        MenuItem("c5", "Espresso", "진하고 묵직한 한 잔의 에스프레소 샷", 4000, 4.5, "coffee", "☕"),
        MenuItem("c6", "Caramel Macchiato", "우유 거품 위에 카라멜 소스를 듬뿍", 5800, 4.8, "coffee", "🍯"),
        MenuItem("c7", "Café Mocha", "초콜릿과 에스프레소, 휘핑크림의 달콤한 조합", 5800, 4.6, "coffee", "🍫"),
        MenuItem("c8", "Cold Brew", "찬물로 오래 우려낸 부드러운 콜드브루", 5000, 4.7, "coffee", "🧊"),
        MenuItem("c9", "Flat White", "리스트레토 샷과 얇은 벨벳 우유 거품", 5300, 4.6, "coffee", "🥛"),
        MenuItem("c10", "Einspänner", "진한 커피 위에 달콤한 크림을 올린 비엔나 커피", 6000, 4.7, "coffee", "☕"),

        // Drinks
        MenuItem("d1", "Lemon Ade", "상큼한 생레몬과 탄산수", 5500, 4.6, "drinks", "🍋", popular = true),
        MenuItem("d2", "Strawberry Smoothie", "딸기를 통째로 갈아 만든 스무디", 6000, 4.7, "drinks", "🍓"),
        MenuItem("d3", "Orange Juice", "매일 직접 짜는 생오렌지 주스", 5500, 4.5, "drinks", "🍊"),

        // Tea
        MenuItem("t1", "Earl Grey Tea", "베르가못 향이 은은한 홍차", 4500, 4.5, "tea", "🍵"),
        MenuItem("t2", "Chamomile Herbal Tea", "편안한 휴식을 위한 카모마일 허브티", 5000, 4.6, "tea", "🌼"),
        MenuItem("t3", "Peppermint Herbal Tea", "입안이 상쾌해지는 페퍼민트 허브티", 5000, 4.4, "tea", "🌿"),

        // Dessert
        MenuItem("s1", "New York Cheesecake", "꾸덕하고 진한 정통 치즈케이크", 6500, 4.9, "dessert", "🍰", popular = true),
        MenuItem("s2", "Tiramisu", "마스카포네 크림과 에스프레소 시트", 6800, 4.8, "dessert", "🍮"),
        MenuItem("s3", "Seasonal Strawberry Cake", "제철 딸기를 듬뿍 올린 계절 케이크", 7000, 4.7, "dessert", "🎂"),

        // Bakery
        MenuItem("b1", "Butter Croissant", "겹겹이 바삭한 프랑스식 버터 크루아상", 3800, 4.8, "bakery", "🥐", popular = true),
        MenuItem("b2", "Chocolate Chip Cookie", "겉은 바삭, 속은 촉촉한 초코칩 쿠키", 3000, 4.6, "bakery", "🍪"),
        MenuItem("b3", "Plain Bagel & Cream Cheese", "쫄깃한 베이글과 크림치즈", 4200, 4.4, "bakery", "🍞"),

        // Food
        MenuItem("f1", "Club Sandwich", "햄, 치즈, 신선한 채소를 가득 넣은 샌드위치", 8500, 4.6, "food", "🥪"),
        MenuItem("f2", "Margherita Pizza", "토마토, 모차렐라, 바질의 클래식 피자", 13900, 4.7, "food", "🍕"),
        MenuItem("f3", "Classic Burger", "두툼한 소고기 패티와 체다 치즈", 9800, 4.8, "food", "🍔", popular = true),
        MenuItem("f4", "Cream Pasta", "베이컨과 버섯을 넣은 크림 파스타", 12500, 4.6, "food", "🍝"),
        MenuItem("f5", "Caesar Salad", "로메인과 파르메산 치즈의 시저 샐러드", 9000, 4.5, "food", "🥗")
    )

    /** 메뉴 목록 화면에서 쓰는 '인기' 가상 카테고리 */
    const val POPULAR_ID = "popular"
    val popularCategory = Category(POPULAR_ID, "Popular", "인기", "⭐")

    fun popularItems(): List<MenuItem> = menuItems.filter { it.popular }

    /** 카테고리 id로 메뉴 목록 가져오기 ("popular"이면 인기 메뉴) */
    fun itemsFor(categoryId: String): List<MenuItem> =
        if (categoryId == POPULAR_ID) popularItems() else itemsByCategory(categoryId)

    /** id로 카테고리 찾기 ("popular" 포함) */
    fun findCategory(categoryId: String): Category =
        if (categoryId == POPULAR_ID) popularCategory
        else categories.firstOrNull { it.id == categoryId } ?: categories.first()

    fun itemsByCategory(categoryId: String): List<MenuItem> =
        menuItems.filter { it.categoryId == categoryId }

    fun search(query: String): List<MenuItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return menuItems.filter {
            it.name.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true)
        }
    }

    fun categoryName(categoryId: String): String =
        categories.firstOrNull { it.id == categoryId }?.nameKo ?: ""
}

/** 4500 -> "4,500원" */
fun Int.toWon(): String = NumberFormat.getNumberInstance(Locale.KOREA).format(this) + "원"