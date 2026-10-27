package com.colorstruction.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app database: palettes with their paints, plus saved favorite recipes.
 *
 * Schema JSON is exported to `app/schemas` (see the `room.schemaLocation` KSP argument) and is
 * committed to the repository, which lets later versions be tested for migrations.
 *
 * Version history: 1 = palettes and paints; 2 adds the favorite tables (auto-migrated, the new
 * tables are simply created).
 */
@Database(
    entities = [
        PaletteEntity::class,
        PaintEntity::class,
        FavoriteRecipeEntity::class,
        FavoriteComponentEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class ColorStructionDatabase : RoomDatabase() {

    /** Access to palette rows. */
    abstract fun paletteDao(): PaletteDao

    /** Access to paint rows. */
    abstract fun paintDao(): PaintDao

    /** Access to saved favorite recipes. */
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        /** File name of the on-device database. */
        const val NAME: String = "colorstruction.db"

        /** Opens the on-device database stored in the app's private storage. */
        fun create(context: Context): ColorStructionDatabase = Room.databaseBuilder(
            context.applicationContext,
            ColorStructionDatabase::class.java,
            NAME,
        ).build()
    }
}
