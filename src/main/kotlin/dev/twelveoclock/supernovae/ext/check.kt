package dev.twelveoclock.supernovae.ext

import dev.twelveoclock.supernovae.proto.DBProto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive


operator fun DBProto.Check.invoke(value1: JsonElement, value2: JsonElement): Boolean {

    if (this == DBProto.Check.EQUAL) {
        return value1 == value2
    }

    val value1Number = (value1 as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return false
    val value2Number = (value2 as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return false

    return when (this) {

        DBProto.Check.GREATER_THAN -> value1Number > value2Number
        DBProto.Check.LESSER_THAN  -> value1Number < value2Number
        DBProto.Check.LESSER_THAN_OR_EQUAL  -> value1Number <= value2Number
        DBProto.Check.GREATER_THAN_OR_EQUAL -> value1Number >= value2Number

        else -> false
    }
}