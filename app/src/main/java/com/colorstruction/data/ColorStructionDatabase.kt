package com.colorstruction.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app database: palettes with their paints.
 *
 * Schema JSON is exported to `app/schemas` (see the `room.schemaLocation` KSP argument) and is
 * committed to the repository, which lets later versions be tested for migrations.
 */
@Database(
    entities = [PaletteEntity::class, PaintEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ColorStructionDatabase : RoomDatabase() {

    /** Access to palette rows. */
    abstract fun paletteDao(): PaletteDao

    /** Access to paint rows. */
    abstract fun paintDao(): PaintDao

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
