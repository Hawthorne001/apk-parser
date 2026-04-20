package com.lb.apkparserdemo.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppIconDao {
    @Query("SELECT * FROM app_icons ORDER BY appName ASC, packageName ASC")
    suspend fun getAll(): List<AppIconInfo>

    @Query("SELECT * FROM app_icons WHERE packageName = :packageName")
    suspend fun getByPackageName(packageName: String): AppIconInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appIconInfo: AppIconInfo)

    @Delete
    suspend fun delete(appIconInfo: AppIconInfo)

    @Query("DELETE FROM app_icons")
    suspend fun deleteAll()
}
