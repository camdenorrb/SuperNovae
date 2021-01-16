package dev.twelveoclock.supernovae

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
sealed class Message {

    @Serializable
    data class Blob<M : Message>(
        val messages: List<M> = emptyList()
    ) : Message()

    @Serializable
    data class Test(
        val text: String
    ) : Message()

}

class RandomTest {

    @Test
    fun thing() {

        val testMessage = Message.Blob(listOf(Message.Test("Meow")))

        val encoded = ProtoBuf.encodeToByteArray(Message.serializer(), testMessage)
        val decoded = ProtoBuf.decodeFromByteArray(Message.serializer(), encoded)

        assertEquals(testMessage, decoded)
    }

}