package com.dasein.poryadok.data

/**
 * Разделы резервной копии: какие таблицы и папки с файлами относятся к каждому.
 * Копию можно сделать целиком или только по выбранным разделам, а загрузить — тоже целиком или частично:
 * остальные разделы на телефоне при этом не трогаются.
 */
enum class BackupSection(
    val title: String,
    val glyph: String,
    val about: String,
    /** Папки внутри файлов приложения: фото, видео, GIF, постеры. */
    val dirs: List<String>,
) {
    TASKS("Задачи и цели", "ui:notebook", "задачи, проекты, подзадачи, цели и этапы", emptyList()),
    CALENDAR("Календарь и напоминалки", "cal/00", "события, напоминалки, свои дни и отметки праздников", emptyList()),
    HABITS("Привычки", "sport/24", "привычки и отметки по дням", emptyList()),
    FINANCE("Финансы", "ui:wallet", "счета, категории, операции, бюджеты, регулярные платежи, тетрадь", emptyList()),
    HEALTH("Здоровье и питание", "sport/11", "вес, замеры, состав тела, дневник питания, сон, настроение, вода, шаги, фото прогресса, колесо баланса", listOf("progress")),
    TRAINING("Тренировки", "sport/00", "упражнения с фото и видео, программы, тренировки и подходы", listOf("training")),
    RECIPES("Рецепты и меню", "food/02", "свои и встроенные рецепты, продукты, меню, шаблоны, покупки, история готовки, фото и видео рецептов", listOf("recipes")),
    MEDIA("Фильмы, книги и топы", "habit/01", "коллекция, оценки, отзывы, прогресс, списки, топы и постеры", listOf("posters", "media")),
    NOTES("Заметки", "habit/23", "страницы и блоки с картинками, видео и GIF", listOf("pages")),
    WARDROBE("Гардероб", "ui:hanger", "вещи, посадка и образы с фото", listOf("wardrobe", "outfits")),
    PRESSURE("Давление и пульс", "ui:heart", "люди, замеры давления и пульса, заметки", listOf("pressure")),
    CRAFTS("Рукоделие", "minimal/04", "схемы алмазной мозаики, вышивки и бисера: фото, настройки, отмеченные цвета", listOf("crafts")),
    SETTINGS("Настройки и виджет", "train/38", "тема, PIN, профиль Small Talks, холодильник, темп ходьбы, вид виджета, свои иконки", listOf("icons"));

    /** Переносит данные раздела из [src] в [dst] (остальное в [dst] не меняется). */
    fun copy(src: BackupData, dst: BackupData): BackupData {
        val sx = src.extra ?: ExtraBackup()
        val dx = dst.extra ?: ExtraBackup()
        return when (this) {
            TASKS -> dst.copy(
                projects = src.projects, taskItems = src.taskItems, subtasks = src.subtasks,
                goals = src.goals, milestones = src.milestones, focusSessions = src.focusSessions,
            )
            CALENDAR -> dst.copy(
                eventItems = src.eventItems, reminders = src.reminders,
                extra = dx.copy(customDays = sx.customDays, holidayMarks = sx.holidayMarks),
            )
            HABITS -> dst.copy(habits = src.habits, habitLogs = src.habitLogs)
            FINANCE -> dst.copy(
                accounts = src.accounts, categorys = src.categorys, txns = src.txns, budgets = src.budgets, recurrings = src.recurrings,
                extra = dx.copy(financeNotes = sx.financeNotes, importRecords = sx.importRecords),
            )
            HEALTH -> dst.copy(
                bodyProfiles = src.bodyProfiles, weightEntrys = src.weightEntrys, measurements = src.measurements,
                foodEntrys = src.foodEntrys, dayLogs = src.dayLogs, sleepEntrys = src.sleepEntrys, moodEntrys = src.moodEntrys,
                progressPhotos = src.progressPhotos, balanceWheels = src.balanceWheels,
                extra = dx.copy(bodyMetrics = sx.bodyMetrics, sleepAuto = sx.sleepAuto, dayEnergy = sx.dayEnergy),
            )
            TRAINING -> dst.copy(
                workouts = src.workouts,
                extra = dx.copy(
                    exercises = sx.exercises, workoutPlans = sx.workoutPlans, planExercises = sx.planExercises,
                    workoutSessions = sx.workoutSessions, setLogs = sx.setLogs,
                ),
            )
            RECIPES -> dst.copy(
                extra = dx.copy(
                    products = sx.products, recipes = sx.recipes, ingredients = sx.ingredients, steps = sx.steps, plan = sx.plan,
                    presets = sx.presets, presetItems = sx.presetItems, repeats = sx.repeats, shopping = sx.shopping, history = sx.history,
                ),
            )
            MEDIA -> dst.copy(
                topLists = src.topLists, topItems = src.topItems,
                extra = dx.copy(media = sx.media, mediaLists = sx.mediaLists, mediaListItems = sx.mediaListItems),
            )
            NOTES -> dst.copy(notes = src.notes, extra = dx.copy(pages = sx.pages, pageBlocks = sx.pageBlocks))
            WARDROBE -> dst.copy(wardrobeItems = src.wardrobeItems, itemFits = src.itemFits, outfits = src.outfits)
            PRESSURE, CRAFTS, SETTINGS -> dst
        }
    }

    /** Сколько записей раздела в копии. */
    fun count(b: BackupData): Int {
        val x = b.extra ?: ExtraBackup()
        return when (this) {
            TASKS -> b.projects.size + b.taskItems.size + b.subtasks.size + b.goals.size + b.milestones.size
            CALENDAR -> b.eventItems.size + b.reminders.size + x.customDays.size + x.holidayMarks.size
            HABITS -> b.habits.size + b.habitLogs.size
            FINANCE -> b.accounts.size + b.categorys.size + b.txns.size + b.budgets.size + b.recurrings.size + x.financeNotes.size
            HEALTH -> b.weightEntrys.size + b.measurements.size + b.foodEntrys.size + b.dayLogs.size + b.sleepEntrys.size +
                b.moodEntrys.size + b.progressPhotos.size + b.balanceWheels.size + x.bodyMetrics.size
            TRAINING -> b.workouts.size + x.exercises.size + x.workoutPlans.size + x.workoutSessions.size + x.setLogs.size
            RECIPES -> x.recipes.size + x.plan.size + x.presets.size + x.shopping.size + x.history.size
            MEDIA -> x.media.size + x.mediaLists.size + b.topLists.size + b.topItems.size
            NOTES -> b.notes.size + x.pages.size + x.pageBlocks.size
            WARDROBE -> b.wardrobeItems.size + b.outfits.size
            PRESSURE, CRAFTS, SETTINGS -> 0
        }
    }

    companion object {
        /** Пустая копия, в которую по очереди переносятся выбранные разделы. */
        fun empty(now: Long) = BackupData(createdAt = now, extra = ExtraBackup())

        /** Копия только из выбранных разделов. */
        fun only(full: BackupData, sections: Set<BackupSection>): BackupData =
            sections.fold(empty(full.createdAt)) { acc, s -> s.copy(full, acc) }

        /** Текущие данные телефона, в которых выбранные разделы заменены данными из копии. */
        fun merge(current: BackupData, file: BackupData, sections: Set<BackupSection>): BackupData =
            sections.fold(current) { acc, s -> s.copy(file, acc) }

        /** Раздел для папки с файлами; неизвестные папки — к настройкам. */
        fun forDir(dir: String): BackupSection = entries.firstOrNull { dir in it.dirs } ?: SETTINGS
    }
}
