package com.example.brewandbite.data

/** 메뉴 카테고리 (Firebase: menu/categories/{카테고리ID}) — Coffee, Drinks, Tea, Dessert, Bakery, Food */
data class Category(
    var id: String = "",
    var name: String = "",
    var nameKo: String = "",
    var emoji: String = ""
)