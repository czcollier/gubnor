package com.shw.gubnor

import akka.actor.{ActorRef, Props}
import com.shw.gubnor.APIHitEventBus.APIHit
import com.shw.gubnor.Throttle.{RateOutOfBounds, RateWithinBounds}
import spray.routing._
import scala.collection.mutable

class ThrottleServiceActor(
    throttleEventBus: ThrottleEventBus,
    apiHitEventBus: APIHitEventBus,
    connector: ActorRef) extends ProxyServiceActor(connector) {

  val logEmitter = context.actorOf(Props[LogEmitterActor])

  val tempRealm = "realm1"

  val throttled = mutable.Set[APIHit]()

  def settings = context.system.settings

  override def preStart = {
    throttleEventBus.subscribe(self, APIHit("*", "*"))
  }

  val throttledRoute: Route = ctx =>
    ctx.complete(503, "throttled")

  val openRoute: Route = { ctx: RequestContext =>
    val hit = APIHit(ctx.request.uri.path.toString, tempRealm)
    apiHitEventBus.publish(hit)
    println("throttled: " + throttled)
    val isThrottled = throttled.collectFirst {
      case t => hit.matches(t)
    }.getOrElse(false)

    println("is throttled: " + isThrottled)
    if (isThrottled) ctx.complete(503, "throttled") else proxy(ctx)
  }

  val receiveThrottled: Receive = runRoute(throttledRoute) orElse {
    case RateWithinBounds(n) => context.become(receiveOpen)
  }

  val receiveOpen: Receive = runRoute(openRoute) orElse manageThrottled

  def manageThrottled: Receive = {
    case RateOutOfBounds(n) => throttled += n
    case RateWithinBounds(n) => throttled -= n
  }

  def receive = {
    receiveOpen
  }

  def emit(capture: String): Unit = {
    logEmitter ! capture
  }
}

object ThrottleServiceActor {
  def props(throttleEventBus: ThrottleEventBus, apiHitEventBus: APIHitEventBus, connector: ActorRef): Props =
    Props(new ThrottleServiceActor(throttleEventBus, apiHitEventBus, connector))
}



