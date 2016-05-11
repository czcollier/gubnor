package com.shw.gubnor

import akka.actor.{Actor, ActorRef, Props}
import akka.io.IO
import akka.routing._
import akka.pattern.ask
import akka.util.Timeout
import com.shw.gubnor.HttpLoadBalancer._
import com.shw.gubnor.ThrottleEvents.{ChangeCommand, CommandAck}
import spray.can.Http
import spray.can.Http.ClientConnectionType

import scala.concurrent.duration._
import scala.util.Success

class HttpLoadBalancer extends Actor {
  var connectors = Map.empty[ConnectorDef, ActorRefRoutee]
  var router = Router(RoundRobinRoutingLogic())
  var monitoredMode = false

  def manageConnectors: Receive = {
    case a@AddConnector(host, port) =>
      implicit val sys = context.system
      implicit val ctx = context.system.dispatcher
      implicit val timeout: Timeout = Timeout(5 seconds)
      val setup = Http.HostConnectorSetup(host=host, port=port, connectionType=ClientConnectionType.Direct)
      val origin = sender
      IO(Http) ? setup andThen {
        case Success(Http.HostConnectorInfo(connector, _)) =>
          val routee = ActorRefRoutee(connector)
          connectors = connectors + (a -> routee)
          router = router.addRoutee(routee)
          context.watch(connector)
          origin ! CommandAck(a)
      }
    case r@RemoveConnector(host, port) =>
      connectors.get(r) map { c =>
        println("removing connector: " + r)
        router = router.removeRoutee(c)
        context.unwatch(c.ref)
      }
    case c@MonitoredMode(v) =>
      println("monitored mode: " + v)
      monitoredMode = v
      sender ! CommandAck(c)
  }

  def handleRequests: Receive = {
    case Monitored(msg) =>
      val origin  = sender
      monitored(origin, msg)
    case msg =>
      val origin = sender
      if (monitoredMode) monitored(origin, msg)
      else router.route(msg, origin)
  }

  def monitored(origin: ActorRef, msg: Any) = {
    val ta = context.actorOf(Props(new ResponseMonitor(origin)))
    router.route(msg, ta)
  }

  def receive = manageConnectors orElse handleRequests
}

object HttpLoadBalancer {
  abstract class ConnectorDef(host: String, port: Int)
  case class AddConnector(host: String, port: Int) extends ConnectorDef(host, port) with ChangeCommand
  case class RemoveConnector(host: String, port: Int) extends ConnectorDef(host, port) with ChangeCommand
  case class ConnectorAdded(host: String, port: Int, connector: ActorRef) extends ChangeCommand
  case class MonitoredMode(on: Boolean = true) extends ChangeCommand
  case class Monitored[T](payload: T)
}

/**
  * This implements a temporary Actor that is created for each request and
  * would contain logic for monitoring response times, or anything else we
  * would want to monitor, from endpoints.  We could use this information
  * to inform decisions about whether to kill endpoints because of bad/slow
  * behavior, judge relative business of endpoints, etc.
  *
  * @param respondTo
  */
class ResponseMonitor(val respondTo: ActorRef) extends Actor {
  def receive = {
    case res =>
      respondTo ! res
      context.stop(self)
  }
}
