package dev.twelveoclock.supernovae.config

import kotlinx.serialization.Serializable

@Serializable
data class TableConfig(
    val keyColumnName: String,
    val shouldCacheAll: Boolean
)