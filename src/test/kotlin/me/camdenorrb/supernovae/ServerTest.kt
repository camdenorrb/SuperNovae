package me.camdenorrb.supernovae

import dev.twelveoclock.supernovae.SuperNovae
import dev.twelveoclock.supernovae.ext.sendNovaeMessage
import dev.twelveoclock.supernovae.proto.CapnProto
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.camdenorrb.netlius.Netlius
import org.capnproto.MessageBuilder
import java.io.File
import kotlin.test.Test

class ServerTest {

    val testingFolder = File("ServerTesting")

    @Test
    fun `basic server testing`() {

        SuperNovae.server("127.0.0.1", 12345, testingFolder)

        val messageBuilder = MessageBuilder()

        val insertMessage = messageBuilder
            .initRoot(CapnProto.Message.factory)
            .initInsert()

        insertMessage.setTableName("Meow")
        insertMessage.setRow("Mew")

        runBlocking {
            Netlius.client("127.0.0.1", 12345).sendNovaeMessage(messageBuilder)
            delay(1000)
        }

        // Remove server folder after testing
        testingFolder.delete()
    }

}