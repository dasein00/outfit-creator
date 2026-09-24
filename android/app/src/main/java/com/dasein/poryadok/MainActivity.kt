package com.dasein.poryadok

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.serialization.Serializable

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) { Text("Порядок") }
                }
            }
        }
    }
}

@Serializable
@Entity
data class Probe(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Dao
interface ProbeDao {
    @Query("SELECT * FROM Probe")
    suspend fun all(): List<Probe>
}

@Database(entities = [Probe::class], version = 1, exportSchema = false)
abstract class ProbeDb : RoomDatabase() {
    abstract fun dao(): ProbeDao
}
