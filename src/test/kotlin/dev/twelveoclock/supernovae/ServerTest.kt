package dev.twelveoclock.supernovae

import dev.twelveoclock.supernovae.api.Database
import dev.twelveoclock.supernovae.ext.*
import dev.twelveoclock.supernovae.net.DBClient
import dev.twelveoclock.supernovae.protocol.ProtocolMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.camdenorrb.netlius.Netlius
import java.io.File
import kotlin.test.Test

@Serializable
data class Thing(val name: String, val personality: String)

@Serializable
data class Count(val id: Int, val count: Int)

class ServerTest {

    val testingFolder = File("ServerTesting")

    @Test
    fun `low level server testing`() {

        SuperNovae.server("127.0.0.1", 12345, testingFolder)

        runBlocking {

            val client = Netlius.client("127.0.0.1", 12345)//Client("127.0.0.1", 12345)
            //val client = Netlius.client("127.0.0.1", 12345)

            client.sendCreateDB("MeowDB")
            client.sendSelectDB("MeowDB")
            client.sendCreateTable("MeowTable", Thing::name.name, true)
            //client.sendSelectTable("MeowTable")
            val thing = Thing("Mr.Midnight", "Cool")
            println(Json.encodeToString(Thing.serializer(), thing))
            client.sendInsertRow("MeowTable", Json.encodeToJsonElement(Thing.serializer(), thing) as JsonObject)
            client.sendSelectRows(
                1,
                "MeowTable",
                listOf(Database.Filter("name", ProtocolMessage.Check.EQUAL, JsonPrimitive("Mr.Midnight")))
            )

            (client.suspendReadNovaeMessage() as ProtocolMessage.Blob).messages.forEach {
                it as ProtocolMessage.Table.SelectRowResponse
                println(it.row.toString())
            }

            delay(10000)
        }

        // Remove server folder after testing
        testingFolder.delete()
    }

    @Test
    fun `high level server testing`() {

        //ProtoBuf.encodeToByteArray(ProtocolMessage.Table.SelectRowResponse.serializer(), ProtocolMessage.Table.SelectRowResponse(JsonObject(emptyMap())))

        SuperNovae.server("127.0.0.1", 12345, testingFolder)

        runBlocking {

            val client = DBClient("127.0.0.1", 12345).apply { connect() } //Client("127.0.0.1", 12345)
            //val client = Netlius.client("127.0.0.1", 12345)

            //client.createDB("MeowDB")
            client.selectDB("MeowDB")
            //client.createTable("MeowTable", Thing::name.name, true)

            val table = client.selectTable("MeowTable", Thing::name, Thing.serializer(), String.serializer())
            //table.insertRow(Thing("Mr.Midnight", "Cat"))

            table.listenToUpdates { type, row ->
                println("Updated: $row")
            }

            val rows1 = table.selectRows(listOf(Database.Filter.eq(Thing::name, "Mr.Midnight")))

            rows1.forEach {
                println(it)
            }

            //delay(30000)

            table.updateRow("Mr.Midnight", Thing::personality, "Dog", String.serializer())

            val rows2 = table.selectRows(listOf(Database.Filter.eq(Thing::name, "Mr.Midnight")))

            rows2.forEach {
                println(it)
            }

            println("Done")

            delay(10000)
        }

        // Remove server folder after testing
        testingFolder.delete()
    }

    /*

    @Test
    fun `zero packing test`() {

        val buffer = ByteBuffer.wrap(ByteArray(8 * Random.nextInt(0, 100000)) { 0 })

        val server = Netlius.server("127.0.0.1", 12345)
        val client = Netlius.client("127.0.0.1", 12345)

        server.onConnect {

            val output = ClientCapnProto.unpack(it)

            check(buffer.array().contentEquals(output.getBytes(output.limit()))) {
                "Buffer isn't equal"
            }

            println("Checked equality")
            exitProcess(0)
        }

        runBlocking {
            ClientCapnProto.pack(buffer, client)
            println("Here")
            //println("Checked size")
            delay(100000)
            error("Failed.")
            //println("Here")
        }
    }

    @Test
    fun `one packing test`() {

        val buffer = ByteBuffer.wrap(ByteArray(8 * Random.nextInt(0, 1000)) { 1 })

        val server = Netlius.server("127.0.0.1", 12345)
        val client = Netlius.client("127.0.0.1", 12345)

        server.onConnect {

            val output = ClientCapnProto.unpack(it)

            //println(buffer.array().contentToString())
            println()
            //println(output.getBytes(output.limit()).contentToString())

            //output.flip()
            check(buffer.array().contentEquals(output.getBytes(output.limit()))) {
                "Buffer isn't equal"
            }

            println("Checked equality")
            exitProcess(0)
        }

        runBlocking {
            println(ClientCapnProto.pack(buffer, client))
            delay(100000)
            error("Failed.")
            //println("Here")
        }
    }


    @Test
    fun `random packing test`() {

        val buffer = ByteBuffer.wrap(ByteArray(8 * 10000) { if (Random.nextBoolean()) 1 else 0 })
        val server = Netlius.server("127.0.0.1", 12345)

        server.onConnect {

            val output = ClientCapnProto.unpack(it)

            check(buffer.array().contentEquals(output.getBytes(output.limit()))) {
                "The contents are not equal"
            }

            println("Checked")
            exitProcess(0)
        }

        val client = Netlius.client("127.0.0.1", 12345)

        runBlocking {
            ClientCapnProto.pack(buffer, client)
            //println("Here")
            delay(100000)
        }

        error("Didn't check")
    }

    @Test
    fun `capnproto packing test`() {

        //Serialize.write()
        val buffer = ByteBuffer.wrap(ByteArray(8 * 10000) { if (Random.nextBoolean()) 1 else 0 })
        val server = Netlius.server("127.0.0.1", 12345)

        server.onConnect {
            val output = ClientCapnProto.unpack(it)
            output.flip()
            check(buffer.array().contentEquals(output.getBytes(output.limit())))
            println("Checked")
            exitProcess(0)
        }

        val client = Netlius.client("127.0.0.1", 12345)

        runBlocking {
            ClientCapnProto.pack(buffer, client)
            //println("Here")
            delay(100000)
        }

        error("Didn't check")
    }



    @Test
    fun `utf8 packing test`() {

        val buffer = ByteBuffer.wrap("MeowmewwMeowmewwMeowmeww".toByteArray())
        val server = Netlius.server("127.0.0.1", 12345)
        //println(buffer.capacity())

        server.onConnect {
            val output = ClientCapnProto.unpack(it)
            println(buffer.array().contentToString())

            println(output.getBytes(output.remaining()).contentToString())
            output.flip()
            check(buffer.array().contentEquals(output.getBytes(output.limit())))
            println("Passed check")
            //println("Checked")
            exitProcess(0)
        }

        val client = Netlius.client("127.0.0.1", 12345)

        runBlocking {
            ClientCapnProto.pack(buffer, client)
            //println("Here")
            delay(100000)
        }

        error("Didn't check")
    }
    */

}