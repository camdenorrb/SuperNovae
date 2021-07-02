package dev.twelveoclock.supernovae.ext

import dev.twelveoclock.supernovae.api.FileDatabase
import kotlinx.serialization.json.JsonObject

fun List<JsonObject>.filter(filter: FileDatabase.Filter): List<JsonObject> {
    return filter {
        filter.check(it.getValue(filter.columnName), filter.value)
    }
}