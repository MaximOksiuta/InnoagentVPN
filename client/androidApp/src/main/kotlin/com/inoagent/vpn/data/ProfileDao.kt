package com.inoagent.vpn.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity): Long

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE accountId = :accountId AND serverId = :serverId LIMIT 1")
    suspend fun findRemote(accountId:Long,serverId:Long):ProfileEntity?

    @Delete
    suspend fun delete(profile: ProfileEntity)
}
