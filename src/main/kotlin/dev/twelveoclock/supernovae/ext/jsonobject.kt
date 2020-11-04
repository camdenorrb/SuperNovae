package dev.twelveoclock.supernovae.ext

import dev.twelveoclock.supernovae.api.Database
import kotlinx.serialization.json.JsonObject

fun List<JsonObject>.filter(filter: Database.Filter): List<JsonObject> {
    return filter {
        filter.check(it.getValue(filter.columnName), filter.value)
    }
}