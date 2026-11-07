package com.inoagent.vpn

import android.app.Application
import androidx.room.Room
import com.inoagent.vpn.data.ConfigCipher
import com.inoagent.vpn.data.InoagentDatabase
import com.inoagent.vpn.data.ProfileRepository
import com.inoagent.vpn.tunnel.AmneziaTunnelEngine

class InoagentApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, InoagentDatabase::class.java, "inoagent.db")
            .addMigrations(object:androidx.room.migration.Migration(1,2) {
                override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE profiles ADD COLUMN accountId INTEGER")
                    db.execSQL("ALTER TABLE profiles ADD COLUMN serverId INTEGER")
                }
            }).build()
        container = AppContainer(
            profiles = ProfileRepository(database.profiles(), ConfigCipher()),
            tunnel = AmneziaTunnelEngine(this),
        )
    }
}

data class AppContainer(
    val profiles: ProfileRepository,
    val tunnel: AmneziaTunnelEngine,
)

