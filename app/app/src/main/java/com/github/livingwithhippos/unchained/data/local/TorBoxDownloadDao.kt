package com.github.livingwithhippos.unchained.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.livingwithhippos.unchained.data.model.TorBoxDownload

@Dao
interface TorBoxDownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(download: TorBoxDownload)

    @Query("SELECT * FROM torbox_download ORDER BY createdAt DESC")
    suspend fun getAll(): List<TorBoxDownload>

    @Query("UPDATE torbox_download SET downloadUrl = :url WHERE id = :id")
    suspend fun updateUrl(id: String, url: String)

    @Query("DELETE FROM torbox_download WHERE id = :id") suspend fun delete(id: String)

    @Query("DELETE FROM torbox_download") suspend fun deleteAll()
}
