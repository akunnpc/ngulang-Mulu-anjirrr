package com.example.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val captureIntervalMs: Long = 500L,
    val isEnabled: Boolean = true,
    val packageName: String = "",
    val appName: String = "",
    val loopSequence: Boolean = true
)

@Entity(
    tableName = "target_images",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class TargetImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    val filePath: String, // Path to the saved image in internal storage
    val threshold: Float = 0.8f,
    val delayAfterTapMs: Long = 1000L,
    val maxTaps: Int = 0, // 0 means unlimited
    val tapCount: Int = 0,
    val priority: Int = 0,
    val restrictRegion: Boolean = false,
    val regionX: Int = 0,
    val regionY: Int = 0,
    val regionWidth: Int = 0,
    val regionHeight: Int = 0,
    val timeoutSeconds: Int = 10,
    val actionType: String = "TAP", // TAP, LONG_PRESS, WAIT_ONLY, WAIT_DISAPPEAR
    val holdDurationMs: Long = 1000L,
    val targetType: String = "IMAGE", // "IMAGE" or "POINT"
    val pointX: Float = 0f,
    val pointY: Float = 0f,
    val allowMultiMatch: Boolean = false,
    val offsetX: Int = 0,
    val offsetY: Int = 0
)

data class ProfileWithTargets(
    @Embedded val profile: Profile,
    @Relation(
        parentColumn = "id",
        entityColumn = "profileId"
    )
    val targets: List<TargetImage>
)

@Dao
interface AutoTapDao {
    // Profiles
    @Query("SELECT * FROM profiles ORDER BY id DESC")
    fun getAllProfilesFlow(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles ORDER BY id DESC")
    suspend fun getAllProfiles(): List<Profile>

    @Transaction
    @Query("SELECT * FROM profiles WHERE id = :profileId")
    fun getProfileWithTargetsFlow(profileId: Long): Flow<ProfileWithTargets?>

    @Transaction
    @Query("SELECT * FROM profiles WHERE id = :profileId")
    suspend fun getProfileWithTargets(profileId: Long): ProfileWithTargets?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile): Long

    @Update
    suspend fun updateProfile(profile: Profile)

    @Delete
    suspend fun deleteProfile(profile: Profile)

    // Target Images
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTargetImage(target: TargetImage): Long

    @Update
    suspend fun updateTargetImage(target: TargetImage)

    @Delete
    suspend fun deleteTargetImage(target: TargetImage)

    @Query("DELETE FROM target_images WHERE profileId = :profileId")
    suspend fun deleteTargetsForProfile(profileId: Long)
}

@Database(entities = [Profile::class, TargetImage::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun autoTapDao(): AutoTapDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN packageName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profiles ADD COLUMN appName TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN loopSequence INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE target_images ADD COLUMN timeoutSeconds INTEGER NOT NULL DEFAULT 10")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE target_images ADD COLUMN actionType TEXT NOT NULL DEFAULT 'TAP'")
                db.execSQL("ALTER TABLE target_images ADD COLUMN holdDurationMs INTEGER NOT NULL DEFAULT 1000")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE target_images ADD COLUMN targetType TEXT NOT NULL DEFAULT 'IMAGE'")
                db.execSQL("ALTER TABLE target_images ADD COLUMN pointX REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE target_images ADD COLUMN pointY REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE target_images ADD COLUMN allowMultiMatch INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE target_images ADD COLUMN offsetX INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE target_images ADD COLUMN offsetY INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "autotap_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
