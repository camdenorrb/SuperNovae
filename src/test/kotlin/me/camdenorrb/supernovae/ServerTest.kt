package me.camdenorrb.supernovae

import dev.twelveoclock.supernovae.SuperNovae
import dev.twelveoclock.supernovae.async.ClientCapnProto
import dev.twelveoclock.supernovae.ext.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.camdenorrb.kcommons.ext.getBytes
import me.camdenorrb.netlius.Netlius
import java.io.File
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlin.system.exitProcess
import kotlin.test.Test

@Serializable
data class Thing(val name: String, val personality: String)

class ServerTest {

    val testingFolder = File("ServerTesting")

    @Test
    fun `basic server testing`() {

        SuperNovae.server("127.0.0.1", 12345, testingFolder)

        runBlocking {

            val client = Netlius.client("127.0.0.1", 12345)//Client("127.0.0.1", 12345)
            //val client = Netlius.client("127.0.0.1", 12345)

            client.sendCreateDB("MeowDB")
            client.sendSelectDB("MeowDB")
            client.sendCreateTable("MeowTable", Thing::name.name, true)
            client.sendSelectTable("MeowTable")
            val thing = Thing("Mr.Midnight", "Cool")
            println(Json.encodeToString(Thing.serializer(), thing))
            client.sendInsertRow("MeowTable", Json.encodeToString(Thing.serializer(), thing))
            //val rows = client.sendSelectRows(listOf(Database.Filter("name", CapnProto.Check.EQUAL, JsonPrimitive("Mr.Midnight"))), "MeowTable")

            //rows.forEach {
            //    println(it.row.toString())
            //}

            //client.createDB("MeowDB")
            //client.selectDB("MeowDB")
            //client.selectTable("MeowTable", Thing::name, Thing.serializer(), )
            /*
            client.createTable("MeowTable", )
            client.(Thing::name.name, "MeowTable", true)
            client.sendInsertRow("MeowTable", Json.encodeToString(Thing.serializer(), Thing("Mr.Midnight", "Cool")))

            client
            println(client.sendSelectByKey("MeowTable", "Mr.Midnight").row.toString())

            client.sendUpdateRows("MeowTable", Thing::personality.name, "\"Moody\"", Database.Filter("name", CapnProto.Check.EQUAL, JsonPrimitive("Mr.Midnight")), 1)

            println(client.sendSelectByKey("MeowTable", "Mr.Midnight").row.toString())
            //println(client.readNovaeMessage().which())
            */
            delay(10000)
        }

        // Remove server folder after testing
        testingFolder.delete()
    }

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


}