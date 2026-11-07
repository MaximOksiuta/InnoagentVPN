package com.inoagent.vpn.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ProfileEntity::class], version = 2, exportSchema = true)
abstract class InoagentDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
}

