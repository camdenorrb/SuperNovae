package dev.twelveoclock.supernovae

import java.io.File

// This should be used for starting the server as a program

// java -jar (jarFile) host port
fun main(args: Array<String>) {

    val host = args[0]

    val port = checkNotNull(args[1].toIntOrNull()) {
        "Invalid port input, expected a number"
    }

    SuperNovae.server(host, port, File("Data"))

    println("Server started $host:$port.")
}