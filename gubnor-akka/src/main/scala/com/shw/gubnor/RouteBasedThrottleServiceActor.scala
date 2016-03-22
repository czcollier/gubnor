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

  val throttle: Route = ctx => ctx.complete(503, "throttled")

  def prepPath(pathStr: String) = {
    val isPattern = pathStr.endsWith("*")
    val noPat = if (isPattern) pathStr.dropRight(1) else pathStr
    val pth = noPat.split("/").foldRight(Neutral.asInstanceOf[PathMatcher[HNil]])((a, b) => a / b)
    if (isPattern) pathPrefix(pth) else path(pth)
  }


  def createThrottleRoute(spec: APIHit, throttleActor: ActorRef): ThrottleRoute = {
    val r = prepPath(spec.path) {
        realm.require(r => spec.realm == "*" || r == spec.realm) { ctx =>
          println("matched: " + spec)
          println("path is: " + ctx.request.uri.path.toString)
            throttleActor ! genericHit
            if (throttleRoutes(spec).throttled) throttle(ctx) else proxy(ctx)
        }
      }
    ThrottleRoute(r, throttleActor, false)
  }

  def registrationReceive: Receive = {
    case Register(s, t) =>
      println("registered: " + s + " -- " + t)
      throttleRoutes = throttleRoutes.updated(s, createThrottleRoute(s, t))
      context.become(runRoute(buildThrottleRoute) orElse registrationReceive orElse manageThrottled)
  }

  def manageThrottled: Receive = {
    case RateOutOfBounds(n) =>  {
      //println("out of bounds! " + n)
      throttleRoutes = throttleRoutes.updated(n, throttleRoutes(n).copy(throttled = true))
    }

    case RateWithinBounds(n) => throttleRoutes = throttleRoutes.updated(n, throttleRoutes(n).copy(throttled = false))
  }

  override def receive: Receive = runRoute(buildThrottleRoute) orElse registrationReceive orElse manageThrottled

  def buildThrottleRoute: Route = {
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
