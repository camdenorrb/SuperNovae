package dev.twelveoclock.supernovae

import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import java.io.File

// This should be used for starting the server as a program

// java -jar (jarFile) host port
fun main(args: Array<String>) {
    
    val cli = DefaultParser().parse(Options().apply {
        addOption(Option.builder("h")
                      .desc("The Server host")
                      .argName("Host")
                      .longOpt("host")
                      .hasArg(true)
                      .build())
        addOption(Option.builder("p")
                      .desc("The Server port")
                      .argName("Port")
                      .longOpt("port")
                      .hasArg(true)
                      .build())
    }, args)
    
    
    val host = requireNotNull(cli.getOptionValue("h", "localhost")) {
        "valid host value not found"
    }
    
    val port = requireNotNull(cli.getOptionValue("p", "38561").toIntOrNull()) {
        "valid port value not found"
    }
    

    SuperNovae.server(host, port, File("Data"))

    println("Server started $host:$port.")

    // TODO: Add a method to safely turn off the server
}