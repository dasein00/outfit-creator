package com.dasein.poryadok.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

object DayKindTag {
    const val BIRTHDAY = "birthday"
    const val HOLIDAY = "holiday"
    const val SPECIAL = "special"
    val names = mapOf(BIRTHDAY to "День рождения", HOLIDAY to "Праздник", SPECIAL to "Особый день")
}

/** Свой праздник, день рождения или особый день. year — год события (для «исполняется N лет»), yearly — повторять каждый год. */
@Serializable
@Entity(tableName = "custom_days")
data class CustomDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val month: Int,
    val day: Int,
    val year: Int? = null,
    val color: Int = 0xFFE06A9A.toInt(),
    val note: String = "",
    val kind: String = DayKindTag.HOLIDAY,
    val yearly: Boolean = true,
    val createdAt: Long = 0,
)

/** Цвет, которым пользователь выделил встроенный праздник. */
@Serializable
@Entity(tableName = "holiday_marks")
data class HolidayMark(
    @PrimaryKey val holidayId: String,
    val color: Int,
)

@Dao
interface DaysDao {
    @Query("SELECT * FROM custom_days ORDER BY month, day") fun customDays(): Flow<List<CustomDay>>
    @Query("SELECT * FROM custom_days") suspend fun customDaysNow(): List<CustomDay>
    @Query("SELECT * FROM custom_days WHERE id = :id") fun customDay(id: Long): Flow<CustomDay?>
    @Upsert suspend fun upsertCustomDay(d: CustomDay): Long
    @Delete suspend fun deleteCustomDay(d: CustomDay)

    @Query("SELECT * FROM holiday_marks") fun marks(): Flow<List<HolidayMark>>
    @Query("SELECT * FROM holiday_marks") suspend fun marksNow(): List<HolidayMark>
    @Upsert suspend fun upsertMark(m: HolidayMark)
    @Query("DELETE FROM holiday_marks WHERE holidayId = :id") suspend fun deleteMark(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCustomDays(items: List<CustomDay>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMarks(items: List<HolidayMark>)
    @Query("DELETE FROM custom_days") suspend fun wipeCustomDays()
    @Query("DELETE FROM holiday_marks") suspend fun wipeMarks()
}
