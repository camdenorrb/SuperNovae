package dev.twelveoclock.supernovae.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

object JsonElementStringSerializer : KSerializer<JsonElement> {

    val json = Json { encodeDefaults = true }

    override val descriptor = PrimitiveSerialDescriptor("JsonElement", PrimitiveKind.STRING)


    override fun serialize(encoder: Encoder, value: JsonElement) {
        encoder.encodeString(json.encodeToString(JsonElement.serializer(), value))
    }

    override fun deserialize(decoder: Decoder): JsonElement {
        return json.decodeFromString(JsonElement.serializer(), decoder.decodeString())
    }

}