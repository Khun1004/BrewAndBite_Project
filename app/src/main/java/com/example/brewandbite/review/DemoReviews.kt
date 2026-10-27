package com.example.brewandbite.review

import com.example.brewandbite.data.MenuRepository
import kotlin.random.Random

/**
 * 🧪 시연용 '다른 손님들' 리뷰.
 *
 * 앱을 처음 보여 줄 때 리뷰가 내 것밖에 없으면 허전해서, 메뉴마다 다른 손님 리뷰 3~5개를 보여 준다.
 *  - Firebase 에 저장하지 않고 앱 안에서만 만든다 (메뉴 ID 로 항상 같은 리뷰가 나옴)
 *  - 평균 별점 · 별점 막대 · 리뷰 수에도 같이 들어간다
 *  - 내 리뷰가 아니므로 수정 · 삭제 버튼은 안 나온다
 *
 * 발표가 끝나고 진짜 리뷰만 보이게 하려면 → ENABLED = false
 */
object DemoReviews {

    /** false 로 바꾸면 시연용 리뷰가 모두 사라진다 */
    const val ENABLED = true

    private const val DAY = 24 * 60 * 60 * 1000L

    private val names = listOf(
        "민지", "준호", "서연", "도윤", "하은", "지훈", "수아", "현우",
        "예린", "태민", "유나", "성민", "다은", "건우", "채원", "시우"
    )

    /** 종류별 리뷰 글 (별점 5 · 4 · 3) */
    private val texts = mapOf(
        "coffee" to Triple(
            listOf(
                "원두가 고소하고 산미가 적당해서 매일 마시고 싶어요!",
                "샷이 진해서 아침에 잠이 확 깨요. 포장도 꼼꼼했어요 ☕",
                "얼음이 금방 안 녹게 따로 담아 주셔서 끝까지 맛있게 마셨어요.",
                "근처 카페 중에 여기가 제일 맛있어요. 단골 됐습니다 😊"
            ),
            listOf(
                "맛은 좋은데 생각보다 양이 조금 적었어요. 다음엔 라지로!",
                "부드럽고 맛있어요. 조금만 더 뜨거웠으면 완벽했을 것 같아요."
            ),
            listOf("무난했어요. 개인적으로는 조금 연한 느낌이었어요.")
        ),
        "drinks" to Triple(
            listOf(
                "과일 맛이 진짜 진해요! 시럽 맛만 나는 음료랑 달라요 🍹",
                "상큼하고 시원해서 더운 날 딱이에요. 또 시킬게요!",
                "아이가 너무 좋아해서 두 번째 주문이에요 😊"
            ),
            listOf("맛있는데 조금 달았어요. 다음엔 시럽 빼고 주문해 볼게요."),
            listOf("얼음이 많아서 금방 끝났어요. 맛은 괜찮아요.")
        ),
        "tea" to Triple(
            listOf(
                "향이 은은하게 오래가서 좋아요. 마음이 편안해지는 맛 🍵",
                "티백이 아니라 잎차로 우려 주셔서 확실히 달라요!",
                "자기 전에 마시기 딱 좋아요. 따뜻하게 잘 왔어요."
            ),
            listOf("맛은 좋은데 조금 더 진하게 우려 주시면 좋겠어요."),
            listOf("평범한 맛이에요. 꿀 추가하면 더 맛있을 것 같아요.")
        ),
        "dessert" to Triple(
            listOf(
                "꾸덕하고 진해서 커피랑 같이 먹으니 최고예요 🍰",
                "너무 달지 않아서 한 조각 순식간에 먹었어요!",
                "모양도 예쁘고 포장도 안 무너지게 와서 감동했어요."
            ),
            listOf("맛있어요! 다만 크기가 조금 작게 느껴졌어요."),
            listOf("괜찮았는데 제 입맛엔 조금 달았어요.")
        ),
        "bakery" to Triple(
            listOf(
                "겉은 바삭하고 속은 촉촉해요. 데워서 먹으니 갓 구운 맛 🥐",
                "버터 향이 진해서 집 안 가득 고소한 냄새가 났어요!",
                "아침마다 사 먹는 중이에요. 늘 한결같이 맛있어요."
            ),
            listOf("맛있는데 배달 오는 동안 조금 눅눅해졌어요. 데우면 괜찮아요."),
            listOf("보통이에요. 크림치즈를 따로 추가하면 더 좋을 것 같아요.")
        ),
        "food" to Triple(
            listOf(
                "양도 많고 재료가 신선해요. 한 끼 든든하게 먹었어요!",
                "재료 빼기 요청도 정확하게 반영해 주셔서 좋았어요 👍",
                "따뜻하게 잘 왔고 맛도 가게에서 먹는 것처럼 좋아요."
            ),
            listOf("맛있었어요. 소스가 조금만 더 있었으면 좋겠어요."),
            listOf("무난한 맛이에요. 가격 생각하면 괜찮아요.")
        )
    )

    /** 종류별로 손님들이 고른 것 같은 옵션 */
    private val options = mapOf(
        "coffee" to listOf("ICE · Regular", "HOT · Regular", "ICE · Large · 샷 +1", "ICE · Grande", "HOT · Large · +바닐라 시럽"),
        "drinks" to listOf("Regular", "Large", "Large · 시럽 빼기", ""),
        "tea" to listOf("HOT · Regular", "ICE · Large", "HOT · Regular · +꿀", ""),
        "dessert" to listOf("", "데워서", "+생크림", ""),
        "bakery" to listOf("데워서", "", "데워서 · +버터", ""),
        "food" to listOf("", "양파 빼기", "+치즈", "피클 빼기 · +베이컨")
    )

    private val cache = mutableMapOf<String, List<Review>>()

    /** 이 메뉴의 시연용 리뷰 (메뉴 ID 가 같으면 항상 같은 리뷰) */
    fun forMenu(menuId: String): List<Review> {
        if (!ENABLED) return emptyList()
        return cache.getOrPut(menuId) { build(menuId) }
    }

    private fun build(menuId: String): List<Review> {
        val item = MenuRepository.findItem(menuId) ?: return emptyList()
        val pool = texts[item.categoryId] ?: texts.getValue("coffee")
        val opts = options[item.categoryId] ?: listOf("")
        val random = Random(menuId.hashCode())
        val count = 3 + random.nextInt(3)   // 3 ~ 5개
        val now = System.currentTimeMillis()
        val usedNames = names.shuffled(random).take(count)
        val five = pool.first.shuffled(random).toMutableList()
        val four = pool.second.shuffled(random).toMutableList()
        val three = pool.third.toMutableList()

        return (0 until count).map { i ->
            // 별점: 대부분 5점 · 가끔 4점 · 드물게 3점 (메뉴 별점이 높을수록 5점이 많게)
            val roll = random.nextInt(100)
            val five5 = if (item.rating >= 4.7) 70 else 55
            var rating = when {
                roll < five5 -> 5
                roll < 93 -> 4
                else -> 3
            }
            val text = when (rating) {
                5 -> five.removeFirstOrNull()
                4 -> four.removeFirstOrNull()
                else -> three.removeFirstOrNull()
            } ?: run {
                rating = 5
                pool.first[i % pool.first.size]
            }
            val daysAgo = 1 + i * 4 + random.nextInt(4)          // 최근 며칠 ~ 몇 주 전
            val createdAt = now - daysAgo * DAY - random.nextInt(12) * 60 * 60 * 1000L
            Review(
                id = "demo_${menuId}_$i",
                uid = "demo_$i",
                name = usedNames[i],
                rating = rating,
                text = text,
                orderNo = "",
                options = opts[random.nextInt(opts.size)],
                photoCount = 0,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        }
    }
}