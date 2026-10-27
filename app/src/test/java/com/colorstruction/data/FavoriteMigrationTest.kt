package com.colorstruction.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migration tests for [ColorStructionDatabase].
 *
 * The exported schemas are merged into the debug assets (`sourceSets` in `app/build.gradle.kts`),
 * which lets [MigrationTestHelper] find them when running under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class FavoriteMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ColorStructionDatabase::class.java,
    )

    @Test
    fun migrate1To2KeepsPalettesAndPaintsAndAddsFavoriteTables() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO palette (id, name, type, createdAt) " +
                    "VALUES (1, 'Acrylic', 'OPAQUE', 11)",
            )
            db.execSQL(
                "INSERT INTO paint (id, paletteId, name, colorArgb, tintingStrength, note, " +
                    "sortOrder) VALUES (1, 1, 'Ultramarine', $ULTRAMARINE, 1.5, NULL, 0)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true).use { db ->
            db.query("SELECT name, type FROM palette WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Acrylic", cursor.getString(0))
                assertEquals("OPAQUE", cursor.getString(1))
            }
            db.query("SELECT name, colorArgb FROM paint WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Ultramarine", cursor.getString(0))
                assertEquals(ULTRAMARINE, cursor.getInt(1))
            }

            // The new tables exist and are usable after the auto migration.
            db.execSQL(
                "INSERT INTO favorite_recipe (id, targetArgb, paletteName, deltaE, createdAt, note) " +
                    "VALUES (1, $ULTRAMARINE, 'Acrylic', 1.5, 99, NULL)",
            )
            db.execSQL(
                "INSERT INTO favorite_component (id, favoriteId, position, paintName, colorArgb, " +
                    "isWater, weight) VALUES (1, 1, 0, 'Ultramarine', $ULTRAMARINE, 0, 1.0)",
            )
            db.query("SELECT COUNT(*) FROM favorite_component WHERE favoriteId = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        val ULTRAMARINE = 0xFF002185.toInt()
    }
}
