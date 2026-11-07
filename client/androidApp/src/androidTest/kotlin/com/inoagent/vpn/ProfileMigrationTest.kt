package com.inoagent.vpn

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inoagent.vpn.data.InoagentDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class ProfileMigrationTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),InoagentDatabase::class.java)
    @Test fun oldEncryptedProfilesSurviveUpgrade() {
        helper.createDatabase("migration-test",1).apply {
            execSQL("INSERT INTO profiles(id,name,endpoint,encryptedConfig,createdAtEpochMillis) VALUES(1,'Imported','host:1234','encrypted-fixture',42)")
            close()
        }
        helper.runMigrationsAndValidate("migration-test",2,true,InoagentDatabase.MIGRATION_1_2).use { db->
            db.query("SELECT * FROM profiles WHERE id=1").use { row->
                assertTrue(row.moveToFirst());assertEquals("encrypted-fixture",row.getString(row.getColumnIndexOrThrow("encryptedConfig")))
                assertEquals("Imported",row.getString(row.getColumnIndexOrThrow("name")))
                assertTrue(row.isNull(row.getColumnIndexOrThrow("accountId")));assertTrue(row.isNull(row.getColumnIndexOrThrow("serverId")))
            }
        }
    }
}
