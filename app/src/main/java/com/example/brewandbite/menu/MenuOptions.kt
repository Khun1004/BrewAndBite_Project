package com.example.brewandbite.menu

import com.example.brewandbite.data.MenuItem

/**
 * 메뉴 상세 화면에서 고를 수 있는 옵션 (카테고리마다 다름).
 *
 *  커피   : 온도(HOT/ICE) · 사이즈 · 샷 추가
 *  차     : 온도(HOT/ICE) · 사이즈
 *  음료   : 사이즈 (항상 ICE)
 *  디저트 · 베이커리 : 따뜻하게 데우기
 *  식사   : (온도/사이즈 없음)
 *
 *  + 모든 메뉴 : 재료 빼기(무료) · 재료 추가(유료) · 사장님께 요청사항 (메뉴마다 다름 → 아래 ingredients)
 */
object MenuOptions {

    enum class Size(val label: String, val desc: String, val extraPrice: Int) {
        REGULAR("Regular", "355ml", 0),
        LARGE("Large", "473ml", 500),
        GRANDE("Grande", "591ml", 1000)
    }

    /** 추가할 수 있는 재료 (이름 + 추가 금액) */
    data class Extra(val name: String, val price: Int)

    /** 메뉴 한 개의 재료 설정: 뺄 수 있는 재료 / 추가할 수 있는 재료 */
    data class Ingredients(val removable: List<String>, val extras: List<Extra>)

    /** 메뉴별 재료 (메뉴 ID로 찾고, 없으면 카테고리 기본값) */
    private val byMenu = mapOf(
        "f1" to Ingredients(   // Club Sandwich
            listOf("양파", "토마토", "피클", "머스터드"),
            listOf(Extra("치즈", 700), Extra("베이컨", 1500), Extra("계란", 1000))
        ),
        "f2" to Ingredients(   // Margherita Pizza
            listOf("바질", "토마토 소스 적게"),
            listOf(Extra("치즈", 2000), Extra("페퍼로니", 2500), Extra("올리브", 1000))
        ),
        "f3" to Ingredients(   // Classic Burger
            listOf("양파", "피클", "토마토", "양상추"),
            listOf(Extra("치즈", 700), Extra("패티", 3000), Extra("베이컨", 1500))
        ),
        "f4" to Ingredients(   // Cream Pasta
            listOf("베이컨", "버섯", "양파", "후추"),
            listOf(Extra("베이컨", 1500), Extra("새우", 2500), Extra("치즈", 1000), Extra("면 곱빼기", 2000))
        ),
        "f5" to Ingredients(   // Caesar Salad
            listOf("크루통", "파르메산 치즈", "드레싱"),
            listOf(Extra("닭가슴살", 2500), Extra("계란", 1000), Extra("드레싱 따로", 0))
        )
    )

    private val byCategory = mapOf(
        "coffee" to Ingredients(
            emptyList(),
            listOf(Extra("바닐라 시럽", 500), Extra("카라멜 시럽", 500), Extra("휘핑크림", 500), Extra("오트밀크로 변경", 700))
        ),
        "tea" to Ingredients(emptyList(), listOf(Extra("꿀", 500), Extra("레몬 슬라이스", 300))),
        "drinks" to Ingredients(listOf("얼음", "시럽"), listOf(Extra("과일 듬뿍", 1000))),
        "dessert" to Ingredients(emptyList(), listOf(Extra("생크림", 800), Extra("아이스크림", 1500))),
        "bakery" to Ingredients(emptyList(), listOf(Extra("버터", 500), Extra("크림치즈", 800))),
        "food" to Ingredients(emptyList(), emptyList())
    )

    fun ingredients(item: MenuItem): Ingredients =
        byMenu[item.id] ?: byCategory[item.categoryId] ?: Ingredients(emptyList(), emptyList())

    const val MAX_REQUEST_LENGTH = 60

    const val SHOT_PRICE = 500
    const val MAX_SHOTS = 3

    fun hasTemperature(item: MenuItem) = item.categoryId == "coffee" || item.categoryId == "tea"
    fun hasSize(item: MenuItem) = item.categoryId in setOf("coffee", "tea", "drinks")
    fun hasShot(item: MenuItem) = item.categoryId == "coffee"
    fun canWarm(item: MenuItem) = item.categoryId == "dessert" || item.categoryId == "bakery"

    /** 고른 옵션 */
    data class Selection(
        val iced: Boolean = true,
        val size: Size = Size.REGULAR,
        val shots: Int = 0,
        val warm: Boolean = false,
        val removed: Set<String> = emptySet(),   // 뺄 재료 이름
        val extras: Set<String> = emptySet(),    // 추가할 재료 이름
        val request: String = ""                 // 사장님께 요청사항
    )

    /** 옵션을 더한 1개 가격 */
    fun unitPrice(item: MenuItem, s: Selection): Int {
        var price = item.price
        if (hasSize(item)) price += s.size.extraPrice
        if (hasShot(item)) price += s.shots * SHOT_PRICE
        price += ingredients(item).extras.filter { it.name in s.extras }.sumOf { it.price }
        return price
    }

    /** 장바구니에 보여 줄 옵션 설명. 예) "ICE · Large · 샷 +1 · 양파 빼기 · +베이컨 · 요청: 소스 따로" */
    fun describe(item: MenuItem, s: Selection): String {
        val parts = mutableListOf<String>()
        if (hasTemperature(item)) parts += if (s.iced) "ICE" else "HOT"
        if (hasSize(item)) parts += s.size.label
        if (hasShot(item) && s.shots > 0) parts += "샷 +${s.shots}"
        if (canWarm(item) && s.warm) parts += "데워서"
        val ing = ingredients(item)
        ing.removable.filter { it in s.removed }.forEach { parts += "$it 빼기" }
        ing.extras.filter { it.name in s.extras }.forEach { parts += "+${it.name}" }
        if (s.request.isNotBlank()) parts += "요청: ${s.request.trim()}"
        return parts.joinToString(" · ")
    }
}