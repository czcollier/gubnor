package com.shw.gubnor

import akka.actor.{ActorRef, Props}
import com.shw.gubnor.CounterActor.Increment
import com.shw.gubnor.Main.CounterType
import spray.routing._

class ThrottleServiceActor(
    val counterType: CounterType,
    counterActor: ActorRef,
    connector: ActorRef) extends ProxyServiceActor(connector) {

  val logEmitter = context.actorOf(Props[LogEmitterActor])

  def settings = context.system.settings

  val rcRoute = { ctx: RequestContext =>
    counterType match {
      case CounterType.Actor => counterActor ! Increment
      case CounterType.Agent => AgentCounters.testCounter1 send (_ + 1)
      case CounterType.NoCounter => ()
    }

    proxyRoute(ctx)
  }

  def receive = runRoute(rcRoute)

  def emit(capture: String): Unit = {
    logEmitter ! capture
  }
}

object ThrottleServiceActor {
  def props(counterType: CounterType, counterActor: ActorRef, connector: ActorRef): Props =
    Props(new ThrottleServiceActor(counterType, counterActor, connector))
}



