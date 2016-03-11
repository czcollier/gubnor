package com.shw.gubnor

import akka.actor.{Actor, ActorRef, Props, Terminated}
import com.shw.gubnor.CounterActor.Increment
import spray.routing._

class ThrottleServiceActor(
    val doCounting: Boolean,
    counterActor: ActorRef,
    connector: ActorRef) extends ProxyServiceActor(connector) {

  val logEmitter = context.actorOf(Props[LogEmitterActor])

  def settings = context.system.settings

  val rcRoute = { ctx: RequestContext =>
    if (doCounting)
      counterActor ! Increment
    proxyRoute(ctx)
  }

  def receive = runRoute(rcRoute)

  def emit(capture: String): Unit = {
    logEmitter ! capture
  }
}

object ThrottleServiceActor {
  def props(doCounting: Boolean, counterActor: ActorRef, connector: ActorRef): Props =
    Props(new ThrottleServiceActor(doCounting, counterActor, connector))
}



