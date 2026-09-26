package com.dasein.poryadok.logic

/**
 * Разбор уведомлений и SMS Сбербанка (номер 900). Официального API для частных лиц у Сбера нет,
 * поэтому операции берутся из текста: «MIR-1234 14:05 Покупка 350р PYATEROCHKA Баланс: 12 000р».
 */
object BankSms {
    enum class Kind { EXPENSE, INCOME }

    data class Op(val kind: Kind, val amount: Double, val merchant: String, val card: String, val category: String?, val text: String)

    private val AMOUNT = Regex("""(\d{1,3}(?:[  ]\d{3})*(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)\s?(?:руб|р(?![а-яА-ЯёЁa-zA-Z])|₽|RUB|RUR)""", RegexOption.IGNORE_CASE)
    private val BALANCE = Regex("""(баланс|доступно|остаток)[:\s]""", RegexOption.IGNORE_CASE)
    private val CARD = Regex("""\b((?:MIR|VISA|ECMC|MC|СЧЁТ|СЧЕТ|КАРТА)[-\s*]?\d{4})\b""", RegexOption.IGNORE_CASE)
    private val TIME = Regex("""\b\d{1,2}:\d{2}\b""")

    private val SKIP = listOf("код", "пароль", "никому не сообщайте", "отказ", "недостаточно", "отклонен", "не выполнен", "не прошла", "подтвердите", "одобрен кредит", "предлагаем")
    private val INCOME = listOf("зачислен", "зачисление", "поступлен", "перевод от", "пополнение", "возврат", "кэшбэк", "кешбэк", "cashback", "зарплат", "аванс", "вам перевели", "получен перевод")
    private val EXPENSE = listOf("покупка", "оплата", "списан", "списание", "перевод ", "перевод:", "выдача", "платёж", "платеж", "оплатили", "снятие", "мобильный банк", "комиссия")

    private val MERCHANT_CATEGORIES = listOf(
        "Продукты" to listOf("пятерочка", "пятёрочка", "pyaterochka", "magnit", "магнит", "перекресток", "перекрёсток", "perekrestok", "вкусвилл", "vkusvill", "лента", "lenta", "ашан", "auchan", "дикси", "dixy", "окей", "okey", "spar", "спар", "metro", "fix price", "светофор", "чижик", "самокат", "samokat", "лавка", "продукт"),
        "Кафе и рестораны" to listOf("кафе", "cafe", "coffee", "кофе", "ресторан", "restoran", "kfc", "вкусно и точка", "бургер", "burger", "додо", "dodo", "шоколадница", "теремок", "суши", "sushi", "пицц", "pizza", "delivery club", "яндекс еда"),
        "Транспорт" to listOf("такси", "taxi", "yandex go", "яндекс go", "uber", "ситимобил", "метро", "metro mos", "транспорт", "тройка", "лукойл", "lukoil", "роснефть", "rosneft", "газпромнефть", "gazpromneft", "азс", "shell", "татнефть", "парковк", "ржд", "rzd", "аэрофлот"),
        "Здоровье" to listOf("аптека", "apteka", "ригла", "36,6", "здравсити", "клиник", "стоматолог", "медси", "инвитро", "гемотест"),
        "Связь и интернет" to listOf("мтс", "mts", "билайн", "beeline", "мегафон", "megafon", "tele2", "теле2", "ростелеком", "rostelecom", "yota", "йота", "интернет"),
        "Жильё и ЖКХ" to listOf("жку", "жкх", "квартплат", "мосэнерго", "энергосбыт", "водоканал", "газ ", "управляющ"),
        "Одежда" to listOf("wildberries", "вайлдберриз", "ozon", "озон", "lamoda", "ламода", "zara", "h&m", "gloria", "спортмастер", "sportmaster", "obuv", "обувь"),
        "Развлечения" to listOf("кино", "cinema", "театр", "концерт", "steam", "playstation", "игр"),
        "Подписки" to listOf("яндекс плюс", "yandex plus", "okko", "ivi", "кинопоиск", "spotify", "apple.com", "google", "сберпрайм", "sberprime", "подписк"),
        "Красота" to listOf("золотое яблоко", "летуаль", "letoile", "салон", "барбер", "парикмахер"),
        "Спорт" to listOf("фитнес", "fitness", "спортзал", "world class", "x-fit"),
    )

    fun isSberSource(pkg: String, title: String): Boolean {
        val p = pkg.lowercase()
        val t = title.lowercase().trim()
        return "sber" in p || t == "900" || t == "сбербанк" || t == "sberbank" || t == "сбер" || t.startsWith("сбербанк")
    }

    fun parse(text: String): Op? {
        val t = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        val low = t.lowercase()
        if (t.isEmpty() || SKIP.any { it in low }) return null
        val kind = when {
            INCOME.any { it in low } -> Kind.INCOME
            EXPENSE.any { it in low } -> Kind.EXPENSE
            else -> return null
        }
        val balanceAt = BALANCE.find(t)?.range?.first ?: Int.MAX_VALUE
        val match = AMOUNT.findAll(t).firstOrNull { it.range.first < balanceAt } ?: return null
        val amount = match.groupValues[1].replace(" ", "").replace(" ", "").replace(',', '.').toDoubleOrNull() ?: return null
        if (amount <= 0) return null
        val card = CARD.find(t)?.value?.uppercase().orEmpty()
        val merchant = t.substring(match.range.last + 1, minOf(balanceAt, t.length))
            .replace(TIME, "").trim().trim('.', ',', ';', ':', '-').trim()
            .let { if (it.length > 60) it.take(60) else it }
        val category = if (kind == Kind.INCOME) {
            if ("зарплат" in low || "аванс" in low) "Зарплата" else if ("кэшбэк" in low || "кешбэк" in low || "cashback" in low) "Кэшбэк" else null
        } else {
            val m = (merchant + " " + low).lowercase()
            MERCHANT_CATEGORIES.firstOrNull { (_, words) -> words.any { it in m } }?.first
        }
        return Op(kind, amount, merchant, card, category, t)
    }
}
