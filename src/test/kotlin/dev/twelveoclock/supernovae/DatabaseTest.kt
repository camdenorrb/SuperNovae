package dev.twelveoclock.supernovae

import dev.twelveoclock.supernovae.net.Client
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import org.junit.Test
import java.io.File

class DatabaseTest {

    val testingFolder = File("ServerTesting")

    @Test
    fun `multiclient test`() {

        val server = SuperNovae.server("127.0.0.1", 12345, testingFolder)

        runBlocking {

            val initClient = Client("127.0.0.1", 12345).apply { connect() }

            val database = initClient.database("MultiClientTest")
            val initTable = database.table("Count", Count::id)

            initTable.insertRow(Count(1, 0))
            println(initTable.selectRow(1))

            delay(1000)

            runBlocking {
                repeat(1000000) {
                    val client = Client("127.0.0.1", 12345).apply { connect() }
                    client.selectDB("MultiClientTest")
                    val table = client.table("Count", Count::id)

                    val count = table.selectRow(1)!!
                    table.updateRow(count.id, Count::count, count.count + 1)
                }
            }


            delay(1000)

            println(initTable.selectRow(1))
        }
    }


    @Test
    fun `disconnect pause test`() {

        val server = SuperNovae.server("127.0.0.1", 12345, testingFolder)
        val client = Client("127.0.0.1", 12345).apply { connect() }

        runBlocking {

            client.createDB("Meow")
            client.selectDB("Meow")

            client.createTable("Meow", "mew")
            client.createTable("MeowTable", Thing::name.name, true)

            val table = client.table("MeowTable", Thing::name, Thing.serializer(), String.serializer())

            server.stop()
            println("Stopped")

            delay(10000)

            println("Attempting to retrieve")
            table.selectRow("Meow")
            println("Done retrieving")
        }
    }

}