package dev.twelveoclock.supernovae.net

import dev.twelveoclock.supernovae.api.Database
import dev.twelveoclock.supernovae.ext.readNovaeMessage
import me.camdenorrb.netlius.Netlius
import me.camdenorrb.netlius.net.Client

//import me.camdenorrb.netlius.net.Client as NetClient

class Server(
    val host: String,
    val port: Int,
    val database: Database
) {

    val netServer = Netlius.server(host, port, false)

    var isRunning = false
        private set


    init {
        netServer.onConnect {
            connectHandler(it)
        }
    }


    fun start() {

        if (isRunning) {
            return
        }

        netServer.start()

        isRunning = true
    }

    fun stop() {

        if (!isRunning) {
            return
        }

        netServer.stop()
        
        isRunning = false
    }


    private suspend fun connectHandler(client: Client) {
        // TODO: Add handling of messages

        println(client.readNovaeMessage().which())
    }

}