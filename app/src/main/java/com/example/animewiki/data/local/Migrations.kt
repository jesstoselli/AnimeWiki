package com.example.animewiki.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `home_shelf_item` (" +
                "`shelf` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "`id` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                "`imageUrl` TEXT NOT NULL, `score` REAL, `season` TEXT, " +
                "`year` INTEGER, `rank` INTEGER, `nextEpisode` INTEGER, " +
                "`nextAiringAtSeconds` INTEGER, " +
                "PRIMARY KEY(`shelf`, `position`))"
        )
    }
}
