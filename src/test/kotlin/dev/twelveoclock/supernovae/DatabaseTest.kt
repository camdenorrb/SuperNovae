package dev.twelveoclock.supernovae

import dev.twelveoclock.supernovae.net.DBClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

class DatabaseTest {

    val testingFolder = File("ServerTesting")

    @Test
    fun `multiclient test`() {

        val server = SuperNovae.server("127.0.0.1", 12345, testingFolder)

        runBlocking {

            val initClient = DBClient("127.0.0.1", 12345).apply { connect() }

            initClient.createDB("MultiClientTest")
            initClient.selectDB("MultiClientTest")

            initClient.createTable("Count", Count::id.name)
            val initTable  = initClient.selectTable("Count", Count::id)

            initTable.insertRow(Count(1, 0))
            println(initTable.selectRow(1))

            delay(1000)

            runBlocking {
                repeat(1000000) {
                    val client = DBClient("127.0.0.1", 12345).apply { connect() }
                    client.selectDB("MultiClientTest")
                    val table = client.selectTable("Count", Count::id)

                    val count = table.selectRow(1)!!
                    table.updateRow(count.id, Count::count, count.count + 1)
                }
            }


            delay(1000)

            println(initTable.selectRow(1))
        }
    }

}