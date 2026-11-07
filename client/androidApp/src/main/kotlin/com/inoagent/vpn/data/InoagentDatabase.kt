package com.inoagent.vpn.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ProfileEntity::class], version = 2, exportSchema = true)
abstract class InoagentDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    companion object {
        val MIGRATION_1_2=object:androidx.room.migration.Migration(1,2) {
            override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN accountId INTEGER")
                db.execSQL("ALTER TABLE profiles ADD COLUMN serverId INTEGER")
            }
        }
    }
}

