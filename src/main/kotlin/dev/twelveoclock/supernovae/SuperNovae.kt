package dev.twelveoclock.supernovae

import dev.twelveoclock.supernovae.net.DBClient
import dev.twelveoclock.supernovae.net.DBServer
import java.io.File


object SuperNovae {

    fun server(host: String, port: Int, folder: File, shouldAutoStart: Boolean = true): DBServer {
        return DBServer(host, port, folder).apply {
            if (shouldAutoStart) {
                start()
            }
        }
    }

    fun client(host: String, port: Int): DBClient {
        return DBClient(host, port).apply { connect() }
    }

}