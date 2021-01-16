package dev.twelveoclock.supernovae.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*
import java.util.*

@Serializer(UUID::class)
object UUIDSerializer : KSerializer<UUID> {

    override val descriptor =  buildClassSerialDescriptor("UUID") {
        element<Long>("leastSignificantBits")
        element<Long>("mostSignificantBits")
    }

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeStructure(descriptor) {
            encodeLongElement(descriptor, 0, value.leastSignificantBits)
            encodeLongElement(descriptor, 1, value.mostSignificantBits)
        }
    }

    override fun deserialize(decoder: Decoder): UUID {
        return decoder.decodeStructure(descriptor) {

            var leastSignificantBits = -1L
            var mostSignificantBits  = -1L

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> leastSignificantBits = decodeLongElement(descriptor, 0)
                    1 -> mostSignificantBits  = decodeLongElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }

            UUID(mostSignificantBits, leastSignificantBits)
        }
    }

}