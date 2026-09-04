package com.doffi4.doffisecure.domain.model

import androidx.room.ColumnInfo

/**
 * One row = one (service, username) pair that appears more than once in the DB.
 * Used by the developer-mode duplicate detector. Plain POJO mapped by Room from
 * the aggregate query; not a table entity.
 */
data class DuplicateGroup(
    @ColumnInfo(name = "dup_key") val key: String,
    @ColumnInfo(name = "cnt") val count: Int
)