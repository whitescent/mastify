/*
 * Copyright 2024 WhiteScent
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

package com.github.whitescent.mastify.utils

import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaInstant
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

object FormatFactory {
  fun getInstanceName(url: String): String {
    val regex = Regex("^(?:https?://)?(?:www\\.)?([\\w.-]+)")
    val matchResult = regex.find(url)
    return matchResult?.groups?.get(1)?.value ?: ""
  }
  fun getLocalizedDateTime(timestamp: String): String {
    return DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
      .format(timestamp.toInstant().toEpochMilliseconds())
  }
  fun getTime(timestamp: String): String {
    val formatter = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    return formatter.format(Date.from(timestamp.toInstant().toJavaInstant()))
  }
  fun getPercentageString(value: Float): String {
    val percentInstance = NumberFormat.getPercentInstance()
    percentInstance.maximumFractionDigits = if (value > 0.01f) 0 else 2
    return percentInstance.format(value)
  }
}
