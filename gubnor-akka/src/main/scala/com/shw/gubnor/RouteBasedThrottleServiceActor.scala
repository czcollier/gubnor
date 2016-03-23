package com.shw.gubnor

import akka.actor.ActorRef
import com.shw.gubnor.APIHitEventBus.APIHit
import spray.routing._
import CustomDirectives._
import com.shw.gubnor.ThrottleEvents.{RateOutOfBounds, RateWithinBounds}
import shapeless.{HList, HNil}
import spray.http.Uri.Path

class RouteBasedThrottleServiceActor(connector: ActorRef)  extends ProxyServiceActor(connector) {
  import RouteBasedThrottleServiceActor._

  var throttleRoutes = Map.empty[APIHit, ThrottleRoute]
  var activeThrottles = Set.empty[APIHit]

  val throttle: Route = ctx => ctx.complete(503, "throttled")

  def prepPath(pathStr: String) = {
    if (pathStr.endsWith("*")) pathPrefix(separateOnSlashes(pathStr.dropRight(1)))
    else path(separateOnSlashes(pathStr))
  }

  def createThrottleRoute(spec: APIHit, throttleActor: ActorRef): ThrottleRoute = {
    val r = prepPath(spec.path) {
        realm.require(r => spec.realm == "*" || r == spec.realm) { ctx =>
            throttleActor ! genericHit
            if (activeThrottles.contains(spec)) throttle(ctx) else proxy(ctx)
        }
      }
    ThrottleRoute(r, throttleActor, false)
  }

  def registrationReceive: Receive = {
    case Register(s, t) =>
      println("registered: " + s + " -- " + t)
      throttleRoutes = throttleRoutes.updated(s, createThrottleRoute(s, t))
      context.become(fullRoute)
  }

  def manageThrottled: Receive = {
    case RateOutOfBounds(n) =>
      println("throttling: " + n)
      activeThrottles = activeThrottles + n
    case RateWithinBounds(n) =>
      println("unthrottling: " + n)
      activeThrottles = activeThrottles - n
  }

  def fullRoute = runRoute(buildThrottleRoute) orElse registrationReceive orElse manageThrottled

  override def receive: Receive = fullRoute

  def buildThrottleRoute: Route = {
    println("regenerating route")
    throttleRoutes.foldRight[Route](proxy) { (acc, next) =>
      acc._2.route ~ next
    }
  }
}

object RouteBasedThrottleServiceActor {
  val genericHit = APIHit("","")
  case class ThrottleRoute(route: Route, throttle: ActorRef, throttled: Boolean)
  case class Register(spec: APIHit, throttle: ActorRef)
}
