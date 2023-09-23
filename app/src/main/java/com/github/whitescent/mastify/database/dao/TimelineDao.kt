/*
 * Copyright 2023 WhiteScent
 *
 * This file is a part of Mastify.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Mastify is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Mastify; if not,
 * see <http://www.gnu.org/licenses>.
 */

package com.github.whitescent.mastify.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import com.github.whitescent.mastify.database.model.TimelineEntity
import com.github.whitescent.mastify.network.model.status.Status

@Dao
interface TimelineDao {

  @Insert(onConflict = REPLACE)
  suspend fun insert(vararg timelineEntity: TimelineEntity)

  @Insert(onConflict = REPLACE)
  suspend fun insertAll(timelineEntity: List<TimelineEntity>)

  @Query(
    """
      SELECT * FROM timelineentity WHERE timelineUserId = :accountId
      ORDER BY LENGTH(id) DESC, id DESC
    """
  )
  fun getStatuses(accountId: Long): List<Status>

  @Query("SELECT * FROM timelineentity WHERE timelineUserId = :accountId")
  fun getAll(accountId: Long): List<Status>

  @Query(
    """
      SELECT id FROM timelineentity WHERE timelineUserId = :accountId
      ORDER BY LENGTH(id) DESC, id DESC LIMIT 1
    """
  )
  suspend fun getTopId(accountId: Long): String?

  @Query(
    """
    DELETE FROM timelineentity WHERE
    timelineUserId = :accountId
    AND
    (LENGTH(id) < LENGTH(:maxId) OR LENGTH(id) == LENGTH(:maxId) AND id <= :maxId)
    AND
    (LENGTH(id) > LENGTH(:minId) OR LENGTH(id) == LENGTH(:minId) AND id >= :minId)
    """
  )
  suspend fun deleteRange(accountId: Long, minId: String, maxId: String)

  @Query("DELETE FROM timelineentity WHERE timelineUserId = :accountId")
  suspend fun clearAll(accountId: Long)
}
