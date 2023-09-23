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

package com.github.whitescent.mastify.network.model.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Relationship(
  val id: String,
  val following: Boolean,
  @SerialName("followed_by") val followedBy: Boolean,
  val blocking: Boolean,
  val muting: Boolean,
  @SerialName("muting_notifications") val mutingNotifications: Boolean,
  val requested: Boolean,
  @SerialName("showing_reblogs") val showingReblogs: Boolean,
  @SerialName("domain_blocking") val blockingDomain: Boolean,
  val note: String?,
  val notifying: Boolean?
)
