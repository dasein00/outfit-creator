package com.dasein.poryadok.logic

/**
 * Подходит ли упражнение под инвентарь, который есть дома. Пустой инвентарь, «Без инвентаря» и «свой вес» подходят всегда.
 * «Гантели, стул» — нужны оба; «Блок или резина» — хватит одного.
 */
object HomeGear {
    private val KEYS: Map<String, List<String>> = mapOf(
        "Степпер" to listOf("степ"),
        "Беговая дорожка" to listOf("дорожк", "бегов"),
        "Велотренажёр" to listOf("вело"),
        "Колесо для пресса" to listOf("колес", "ролик"),
        "Гантели" to listOf("гантел"),
        "Утяжелители" to listOf("утяжел"),
        "Фитнес-коврик" to listOf("коврик", "мат"),
        "Стул" to listOf("стул", "скамь", "табурет", "опор"),
        "Скакалка" to listOf("скакал"),
        "Турник" to listOf("турник", "перекладин"),
        "Штанга" to listOf("штанг", "гриф"),
        "Гиря" to listOf("гир"),
        "Резинка" to listOf("резин", "эспандер", "петл"),
    )
    private val BODY = listOf("без инвентар", "свой вес", "собственн", "ничего")

    fun fits(equipment: String, gear: Set<String>): Boolean {
        val e = equipment.lowercase().replace('ё', 'е').trim()
        if (e.isEmpty() || BODY.any { it in e }) return true
        val keys = gear.flatMap { KEYS[it].orEmpty() }
        return e.split(',', ';', '+').map { it.trim() }.filter { it.isNotEmpty() }.all { token ->
            token.split(Regex("""\s+или\s+|/""")).any { alt -> keys.any { k -> k in alt } }
        }
    }
}
