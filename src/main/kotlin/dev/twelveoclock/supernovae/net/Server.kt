package dev.twelveoclock.supernovae.net

import dev.twelveoclock.supernovae.api.Database
import dev.twelveoclock.supernovae.proto.CapnProto
import me.camdenorrb.netlius.Netlius
import org.capnproto.MessageBuilder

//import me.camdenorrb.netlius.net.Client as NetClient

class Server internal constructor(
    val host: String,
    val port: Int,
    val database: Database
) {

    val netServer = Netlius.server(host, port, false)

    var isRunning = false
        private set


    init {
        netServer.onConnect {
            connectHandler(Client(it))
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


    fun connectHandler(client: Client) {



        message.

        CapnProto.Filter.Reader()
        MessageBuilder(CapnProto.Filter.listFactory.).
        SerializePacked.
        client.netClient.
    }

}