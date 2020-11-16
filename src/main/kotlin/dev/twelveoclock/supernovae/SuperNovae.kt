package dev.twelveoclock.supernovae

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

}