package com.shw.gubnor

import akka.actor.{Actor, ActorIdentity, ActorRef, ActorSystem, Identify, Props}
import akka.event.Logging
import akka.io.{IO, Tcp}
import com.typesafe.config.{Config, ConfigObject}
import scopt.OptionParser
import spray.can.Http
import spray.can.Http.{Bound, ClientConnectionType}
import akka.pattern.ask
import akka.util.Timeout
import com.shw.gubnor.APIHitEventBus.APIHit
import com.shw.gubnor.HttpLoadBalancer.{AddConnector, MonitoredMode}
import com.shw.gubnor.ThrottleEvents.CommandAck
import kamon.Kamon

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

/**
  * Main entry point for HTTP proxy/throttler.  Starts up HTTP server and clients
  * and begins listening.  Prints counter values for any counter-based throttles
  * at 5-second intervals.
  *
  */
object Main extends App {

  implicit val system = ActorSystem("gubnor")
  implicit val executionContext = system.dispatcher

  val log = Logging.getLogger(system, this.getClass)

  val systemConfig = system.settings.config
  val gubnorSettings = GubnorSettings(system)

  val throttleEventBus = new ThrottleEventBus()
  val hitCountEventBus = new APIHitEventBus()

  def mkThrottle(tc: LeakyBucketThrottleConfig) = {
    val hit = APIHit(tc.path, tc.realm)
    val throttle = system.actorOf(EventBusLeakyBucketThrottleActor.props(
      hit,
      throttleEventBus,
      hitCountEventBus,
      tc.bucketSize, tc.drainFrequency, tc.drainSize), "throttle_" + tc.name)
    log.info("added throttle " + throttle.path)
    throttle
  }


  implicit val timeout: Timeout = Timeout(5 seconds)

  val http = IO(Http)(system)

  val router = system.actorOf(Props[HttpLoadBalancer], "load-balancer")

  gubnorSettings.endpoints.map { ep =>
    router ? AddConnector(ep.host, ep.port) andThen {
      case Success(CommandAck(c: AddConnector)) => log.info(s"added connector to: ${c.host}:${c.port}")
    }
  }

  val service = system.actorOf(
    ThrottleServiceActor.props(throttleEventBus, hitCountEventBus, router), "throttle-service")

  gubnorSettings.throttles.map(mkThrottle)

  http ? Http.Bind(service, interface = gubnorSettings.bindHost, port = gubnorSettings.bindPort) andThen {
    case Success(bind: Bound) => log.info(s"bound to interface: ${bind.localAddress}")
  }
}

