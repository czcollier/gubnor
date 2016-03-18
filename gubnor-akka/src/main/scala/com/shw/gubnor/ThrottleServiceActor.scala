package com.shw.gubnor

import akka.actor.{ActorRef, Props}
import com.shw.gubnor.APIHitEventBus.APIHit
import com.shw.gubnor.ThrottleEvents.{RateOutOfBounds, RateWithinBounds}
import shapeless.{::, HNil}
import spray.routing._
import kamon.spray.KamonTraceDirectives.traceName

import scala.collection.mutable
import scala.xml.NodeSeq

class ThrottleServiceActor(
    throttleEventBus: ThrottleEventBus,
    apiHitEventBus: APIHitEventBus,
    connector: ActorRef) extends ProxyServiceActor(connector) {

  import ThrottleEvents._

  val logEmitter = context.actorOf(Props[LogEmitterActor])

  val throttled = mutable.Set[APIHit]()

  def settings = context.system.settings

  val realm: Directive1[String] = {
    entity(as[NodeSeq]).hmap {
      case body :: HNil =>
        (body \ "authentication" \ "simple" \ "realm").text
    }
  }

  override def preStart = {
    throttleEventBus.subscribe(self, APIHit("*", "*"))
  }

  val throttle: Route = ctx => ctx.complete(503, "throttled")

  val throttling: Route = path(RestPath) { p =>
    traceName("gubnor-throttles") {
        realm { r =>
          val hit = APIHit(p.toString, r)
          apiHitEventBus.publish(hit)

          val isThrottled = throttled.collectFirst {
            case t => hit.matches(t)
          }.getOrElse(false)
          if (isThrottled) throttle else proxy
        }
    }
  }

  def receive = runRoute(throttling) orElse manageThrottled

  def manageThrottled: Receive = {
    case RateOutOfBounds(n) =>  throttled += n
    case RateWithinBounds(n) => throttled -= n
  }
}

object ThrottleServiceActor {
  def props(throttleEventBus: ThrottleEventBus, apiHitEventBus: APIHitEventBus, connector: ActorRef): Props =
    Props(new ThrottleServiceActor(throttleEventBus, apiHitEventBus, connector))
}



