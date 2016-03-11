package com.shw.gubnor

import akka.actor.Actor.Receive
import akka.actor.{ActorSystem, Props}
import akka.io.{IO, Tcp}
import com.typesafe.config.Config
import scopt.OptionParser
import spray.can.Http
import spray.can.Http.ClientConnectionType
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._

object Main extends App {


  case class GubnorConfig(
     bindInterface: String = "0.0.0.0",
     bindPort: Int = 9000,
     endpointHost: String = "localhost",
     endpointPort: Int = 8765,
     doCounting: Boolean = true,
     actorPoolSize: Int = 5)

  val defaults = GubnorConfig()

  private var config: Option[GubnorConfig] = None

  def configuration = config.getOrElse(defaults)
  var systemConfig: Option[Config] = None

  val cliOptsParser = new OptionParser[GubnorConfig]("java -jar soaker.jar") {
    help("help").text("Show help (this message) and exit")
    opt[String]("interface")    abbr("bi")  action { (x, c) => c.copy(bindInterface = x) }  text (s"interface to bind to. Default: ${defaults.bindPort}")
    opt[Int]('p', "port")       abbr("bp")  action { (x, c) => c.copy(bindPort = x) }       text (s"port to bind to. Default: ${defaults.bindPort}")
    opt[String]("endpointHost") abbr("eh")  action { (x, c) => c.copy(endpointHost = x) }   text (s"host of endpoint proxied to. Default: ${defaults.endpointHost}")
    opt[Int]("endpointPort")    abbr("ep")  action { (x, c) => c.copy(endpointPort = x) }   text (s"port of endpoint proxied to. Default: ${defaults.bindPort}")
    opt[Unit]("no-counting")    abbr ("nc") action { (x, c) => c.copy(doCounting = false) } text ("whether to actually count")
    opt[Int]("actor-pool-size") abbr("aps") action { (x, c) => c.copy(actorPoolSize = x) }  text (s"for experimenting with pool sizes of various kinds of actors")
  }

  cliOptsParser.parse(args, GubnorConfig()) map { cfg =>
    config = Some(cfg)
    implicit val system = ActorSystem("spray-can")
    val http = IO(Http)(system)
    IO(Tcp)(system)

    systemConfig = Some(system.settings.config)

    implicit val executionContext = system.dispatcher

    val counterActor = system.actorOf(Props(new CounterPoolActor(cfg.actorPoolSize)))

    val setup = Http.HostConnectorSetup(
      host = cfg.endpointHost,
      port = cfg.endpointPort,
      connectionType = ClientConnectionType.Direct//.Proxied(cfg.endpointHost, cfg.endpointPort)
    )

    implicit val timeout: Timeout = Timeout(5 seconds)

    http ? (setup) map {
      case Http.HostConnectorInfo(connector, _) =>
        val service = system.actorOf(Props(new ThrottleServiceActor(cfg.doCounting, counterActor, connector)))
        IO(Http) ! Http.Bind(service, interface = cfg.bindInterface, port = cfg.bindPort)
    }
  }
}