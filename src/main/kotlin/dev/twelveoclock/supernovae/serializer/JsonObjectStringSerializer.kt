package dev.twelveoclock.supernovae.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object JsonObjectStringSerializer : KSerializer<JsonObject> {

    val json = Json { encodeDefaults = true }

    override val descriptor = PrimitiveSerialDescriptor("JsonObject", PrimitiveKind.STRING)


    override fun serialize(encoder: Encoder, value: JsonObject) {
        encoder.encodeString(json.encodeToString(JsonObject.serializer(), value))
    }

    override fun deserialize(decoder: Decoder): JsonObject {
        return json.decodeFromString(JsonObject.serializer(), decoder.decodeString())
    }

}