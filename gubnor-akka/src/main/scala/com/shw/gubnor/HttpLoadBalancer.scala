package com.shw.gubnor

import akka.actor.{Actor, ActorRef, Props}
import akka.routing._
import com.shw.gubnor.HttpLoadBalancer.{AddConnector, Monitored, MonitoredMode}
import com.shw.gubnor.ThrottleEvents.{ChangeCommand, CommandAck}

class HttpLoadBalancer extends Actor {
  var connectors = List.empty[ActorRefRoutee]
  var router = Router(RoundRobinRoutingLogic())
  var monitoredMode = false

  def manageConnectors: Receive = {
    case c@AddConnector(conn) =>
      println("adding connector: " + conn)
      val routee = ActorRefRoutee(conn)
      connectors = routee :: connectors
      router = router.addRoutee(routee)
      context.watch(conn)
      sender ! CommandAck(c)
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
  case class AddConnector(connector: ActorRef) extends ChangeCommand
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
