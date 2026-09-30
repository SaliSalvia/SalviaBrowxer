package com.salvia.salviabrowxer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadState
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(downloads: List<DownloadEntity>)

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatus(status: DownloadState): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN (:states) ORDER BY createdAt DESC")
    fun getByStates(states: List<DownloadState>): Flow<List<DownloadEntity>>

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN (:states)")
    suspend fun countByStates(states: List<DownloadState>): Int

    /** Single-statement progress write: avoids a SELECT + full-row UPDATE per progress tick. */
    @Query(
        "UPDATE downloads SET downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, " +
            "bytesPerSecond = :bytesPerSecond, etaSeconds = :etaSeconds, " +
            "updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateProgressColumns(
        id: String,
        downloadedBytes: Long,
        totalBytes: Long?,
        bytesPerSecond: Long,
        etaSeconds: Long?,
        updatedAt: Long
    )

    /**
     * Single-statement status write for state transitions (pause/resume/queue/retry).
     *
     * The rate and the estimate are cleared here as well: the transfer that measured them is no
     * longer running, and a paused row showing `8 MB/s` would be a plain lie.
     */
    @Query(
        "UPDATE downloads SET status = :status, bytesPerSecond = 0, etaSeconds = NULL, " +
            "updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateStatus(id: String, status: DownloadState, updatedAt: Long)

    /**
     * Single-statement outcome write; COALESCE keeps existing values for null inputs. The rate and
     * the estimate are always cleared: the transfer is over, whatever the outcome.
     */
    @Query(
        "UPDATE downloads SET status = :status, " +
            "downloadedBytes = COALESCE(:downloadedBytes, downloadedBytes), " +
            "totalBytes = COALESCE(:totalBytes, totalBytes), " +
            "finalPath = COALESCE(:finalPath, finalPath), " +
            "mimeType = COALESCE(:mimeType, mimeType), " +
            "bytesPerSecond = 0, etaSeconds = NULL, " +
            "error = :error, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateResult(
        id: String,
        status: DownloadState,
        downloadedBytes: Long?,
        totalBytes: Long?,
        finalPath: String?,
        mimeType: String?,
        error: String?,
        updatedAt: Long
    )

    /**
     * Writes the artefacts produced after a transfer finishes: the final file, its container, the
     * duration and thumbnail read from it, and the gallery URI it was published to. COALESCE keeps
     * whatever was already there for the values a caller does not have.
     */
    @Query(
        "UPDATE downloads SET " +
            "finalPath = COALESCE(:finalPath, finalPath), " +
            "filename = COALESCE(:filename, filename), " +
            "mimeType = COALESCE(:mimeType, mimeType), " +
            "durationMs = COALESCE(:durationMs, durationMs), " +
            "thumbnail = COALESCE(:thumbnail, thumbnail), " +
            "exportedUri = COALESCE(:exportedUri, exportedUri), " +
            "updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateArtifacts(
        id: String,
        finalPath: String?,
        filename: String?,
        mimeType: String?,
        durationMs: Long?,
        thumbnail: String?,
        exportedUri: String?,
        updatedAt: Long
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM downloads WHERE status = :status")
    suspend fun deleteByStatus(status: DownloadState)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()
}
