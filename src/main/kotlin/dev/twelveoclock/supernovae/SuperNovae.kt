package dev.twelveoclock.supernovae

import dev.twelveoclock.supernovae.net.Client
import dev.twelveoclock.supernovae.net.Server
import java.io.File


object SuperNovae {

    fun server(host: String, port: Int, folder: File, shouldAutoStart: Boolean = true): Server {
        return Server(host, port, folder).apply {
            if (shouldAutoStart) {
                start()
            }
        }
    }

    fun client(host: String, port: Int): Client {
        return Client(host, port).apply { connect() }
    }

}