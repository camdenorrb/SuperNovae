package dev.twelveoclock.supernovae

import dev.twelveoclock.supernovae.api.Database
import dev.twelveoclock.supernovae.net.Server
import java.io.File

object SuperNovae {

    fun server(host: String, port: Int, folder: File, shouldAutoStart: Boolean = true): Server {
        return Server(host, port, Database(folder)).apply {
            if (shouldAutoStart) {
                start()
            }
        }
    }

}