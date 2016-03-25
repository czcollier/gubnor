package com.shw.gubnor

import akka.actor.{Actor, ActorIdentity, ActorRef, ActorSystem, Identify, Props}
import akka.io.{IO, Tcp}
import com.typesafe.config.{Config, ConfigObject}
import scopt.OptionParser
import spray.can.Http
import spray.can.Http.ClientConnectionType
import akka.pattern.ask
import akka.util.Timeout
import com.shw.gubnor.APIHitEventBus.APIHit
import com.shw.gubnor.HttpLoadBalancer.{AddConnector, MonitoredMode}
import kamon.Kamon

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Main entry point for HTTP proxy/throttler.  Starts up HTTP server and clients
  * and begins listening.  Prints counter values for any counter-based throttles
  * at 5-second intervals.
  *
  */
object Main extends App {

  case class GubnorConfig(
                           bindInterface: String = "0.0.0.0",
                           bindPort: Int = 9000,
                           endpointHost: String = "localhost",
                           endpointPort: Int = 8765,
                           actorPoolSize: Int = 5)

  val defaults = GubnorConfig()

  private var config: Option[GubnorConfig] = None

  def configuration = config.getOrElse(defaults)

  val cliOptsParser = new OptionParser[GubnorConfig]("java -jar gubnor.jar") {
    help("help").text("Show help (this message) and exit")
    opt[String]("interface")         abbr("bi") action { (x, c) => c.copy(bindInterface = x) } text (s"interface to bind to. Default: ${defaults.bindPort}")
    opt[Int]('p', "port")            abbr("bp") action { (x, c) => c.copy(bindPort = x) }      text (s"port to bind to. Default: ${defaults.bindPort}")
    opt[String]("endpointHost")      abbr("eh") action { (x, c) => c.copy(endpointHost = x) }  text (s"host of endpoint proxied to. Default: ${defaults.endpointHost}")
    opt[Int]("endpointPort")         abbr("ep") action { (x, c) => c.copy(endpointPort = x) }  text (s"port of endpoint proxied to. Default: ${defaults.bindPort}")
    opt[Int]("actor-pool-size")      abbr("ps") action { (x, c) => c.copy(actorPoolSize = x) } text (s"for experimenting with pool sizes of various kinds of actors")
  }

  cliOptsParser.parse(args, GubnorConfig()) foreach { cfg =>

    config = Some(cfg)
    implicit val system = ActorSystem("gubnor")

    val systemConfig = system.settings.config
    val gubnorSettings = GubnorSettings(system)

    implicit val executionContext = system.dispatcher

    val throttleEventBus = new ThrottleEventBus()
    val hitCountEventBus = new APIHitEventBus()

    def mkThrottle(tc: LeakyBucketThrottleConfig, service: ActorRef) = {
      val hit = APIHit(tc.path, tc.realm)
      val throttle = system.actorOf(EventBusLeakyBucketThrottleActor.props(
        hit,
        throttleEventBus,
        hitCountEventBus,
        tc.bucketSize, tc.drainFrequency, tc.drainSize), "throttle_" + tc.name)
      println("added throttle " + throttle.path)
      throttle
      //service ! Register(hit, ta)
    }

    def mkEndpoint(co: ConfigObject) = {
      val cfg = co.toConfig
      val host = cfg.getString("host")
      val port = cfg.getInt("port")
      Http.HostConnectorSetup(host=host, port=port, connectionType=ClientConnectionType.Direct)
    }

    val setups = for {
      cx <- systemConfig.getObjectList("gubnor.endpoints")
    } yield mkEndpoint(cx)


    implicit val timeout: Timeout = Timeout(5 seconds)

    val http = IO(Http)(system)

    val router = system.actorOf(Props[HttpLoadBalancer], "load-balancer")

    val connectorFutures = setups map (http ? _)

    Future.sequence(connectorFutures).map { f =>
      f.collect {
        case Http.HostConnectorInfo(connector, _) => router ! { println("adding it.....") ; AddConnector(connector) }
      }

      val service = system.actorOf(ThrottleServiceActor.props(throttleEventBus, hitCountEventBus, router), "throttle-service")

      gubnorSettings.throttles.map(mkThrottle(_, service))

      http ? Http.Bind(service, interface = cfg.bindInterface, port = cfg.bindPort) map { bound =>
        println("bound:" + bound) }
    }
  }
}

