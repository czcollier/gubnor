package com.shw.gubnor

import akka.actor.{ActorSystem, Props}
import akka.io.{IO, Tcp}
import com.typesafe.config.{Config, ConfigObject}
import scopt.OptionParser
import spray.can.Http
import spray.can.Http.ClientConnectionType
import akka.pattern.ask
import akka.util.Timeout
import com.shw.gubnor.APIHitEventBus.APIHit
import kamon.Kamon

import scala.collection.JavaConversions._
import scala.concurrent.duration._

object Main extends App {

  trait CounterType
  object CounterType {

    case object Actor extends CounterType
    case object Agent
    extends CounterType
    case object NoCounter extends CounterType

    implicit val counterTypeReader = new scopt.Read[CounterType] {
      override def arity: Int = 3

      override def reads: (String) => CounterType = {
        case "actor" => Actor
        case "agent" => Agent
        case "none" => NoCounter
      }
    }
  }

  case class GubnorConfig(
                           bindInterface: String = "0.0.0.0",
                           bindPort: Int = 9000,
                           endpointHost: String = "localhost",
                           endpointPort: Int = 8765,
                           counterType: CounterType = CounterType.Actor,
                           actorPoolSize: Int = 5)

  val defaults = GubnorConfig()

  private var config: Option[GubnorConfig] = None

  def configuration = config.getOrElse(defaults)
  var systemConfig: Option[Config] = None


  val cliOptsParser = new OptionParser[GubnorConfig]("java -jar gubnor.jar") {
    help("help").text("Show help (this message) and exit")
    opt[String]("interface")         abbr("bi") action { (x, c) => c.copy(bindInterface = x) } text (s"interface to bind to. Default: ${defaults.bindPort}")
    opt[Int]('p', "port")            abbr("bp") action { (x, c) => c.copy(bindPort = x) }      text (s"port to bind to. Default: ${defaults.bindPort}")
    opt[String]("endpointHost")      abbr("eh") action { (x, c) => c.copy(endpointHost = x) }  text (s"host of endpoint proxied to. Default: ${defaults.endpointHost}")
    opt[Int]("endpointPort")         abbr("ep") action { (x, c) => c.copy(endpointPort = x) }  text (s"port of endpoint proxied to. Default: ${defaults.bindPort}")
    opt[CounterType]("counter-type") abbr("ct") action { (x, c) => c.copy(counterType = x) }   text ("counter type (actor, agent, none)")
    opt[Int]("actor-pool-size")      abbr("ps") action { (x, c) => c.copy(actorPoolSize = x) } text (s"for experimenting with pool sizes of various kinds of actors")
  }

  cliOptsParser.parse(args, GubnorConfig()) map { cfg =>

    Kamon.start()

    config = Some(cfg)
    implicit val system = ActorSystem("gubnor")
    val http = IO(Http)(system)
    IO(Tcp)(system)

    systemConfig = Some(system.settings.config)

    implicit val executionContext = system.dispatcher

    val throttleEventBus = new ThrottleEventBus()
    val hitCountEventBus = new APIHitEventBus()

    def mkThrottle(co: ConfigObject, index: Int) = {
      implicit def asFiniteDuration(d: java.time.Duration) =
        scala.concurrent.duration.Duration.fromNanos(d.toNanos)
      val cfg = co.toConfig
      val path = cfg.getString("path")
      val realm = cfg.getString("realm")
      val bucketSize = cfg.getInt("bucketSize")
      val drainFrequency: FiniteDuration = cfg.getDuration("drainFrequency")
      val drainSize = cfg.getInt("drainSize")
      val ta = system.actorOf(LeakyBucketThrottleActor.props(
        APIHit(path, realm),
        throttleEventBus,
        hitCountEventBus,
        bucketSize, drainFrequency, drainSize), "throttle_" + index)
      val chk = system.actorOf(Props(new CounterCheckActor(s"$realm@$path ($drainSize/$drainFrequency max $bucketSize)", ta)))
      ta
    }

    val throttles = systemConfig map { c =>
      c.getObjectList("gubnor.throttles").zipWithIndex.map(z => mkThrottle(z._1, z._2))
    }

    val setup = Http.HostConnectorSetup(
      host = cfg.endpointHost,
      port = cfg.endpointPort,
      connectionType = ClientConnectionType.Direct//.Proxied(cfg.endpointHost, cfg.endpointPort)
    )

    implicit val timeout: Timeout = Timeout(5 seconds)

    http ? (setup) map {
      case Http.HostConnectorInfo(connector, _) =>
        val service = system.actorOf(Props(new ThrottleServiceActor(throttleEventBus, hitCountEventBus, connector)), "throttle-service")
        http ! Http.Bind(service, interface = cfg.bindInterface, port = cfg.bindPort)
    }
  }
}