package com.shw.gubnor

import akka.actor.{ActorRef, Props}
import akka.event.Logging
import com.rklaehn.radixtree.RadixTree
import com.shw.gubnor.APIHitEventBus.APIHit
import shapeless.{::, HNil}
import spray.routing._
import kamon.spray.KamonTraceDirectives.traceName
import spray.http.StatusCodes

import scala.collection.mutable
import scala.xml.NodeSeq

class ThrottleServiceActor(
    throttleEventBus: ThrottleEventBus,
    apiHitEventBus: APIHitEventBus,
    connector: ActorRef) extends ProxyServiceActor(connector) {

  import ThrottleEvents._

  val log = Logging.getLogger(context.system, self)

  val throttled = mutable.Set[APIHit]()
  var throttled2 = RadixTree[String, Boolean]()

  def settings = context.system.settings

  val realm: Directive1[String] = {
    entity(as[NodeSeq]).hmap {
      case body :: HNil =>
        (body \  "authentication" \ "simple" \ "realm").text
    }
  }

  override def preStart = {
    throttleEventBus.subscribe(self, APIHit("*", "*"))
  }

  val throttle: Route = ctx => ctx.complete(StatusCodes.TooManyRequests, "request limit exceeded")

  val throttling: Route = path(RestPath) { p =>
    traceName("gubnor-throttles") {
        realm { r =>
          val hit = APIHit(p.toString, r)
          apiHitEventBus.publish(hit)
          log.debug(s"checking: $r@${p.toString}")
          val matches = throttled2.filterPrefixesOf(p.toString)
          val isThrottled = ! matches.isEmpty || throttled.contains(hit)
          if (isThrottled) throttle else proxy
        }
    }
  }

  def receive = runRoute(throttling) orElse manageThrottled

  def addThrottled(h: APIHit) = {
    if (h.path.endsWith("*")) {
      val newEntries = (h.path.dropRight(1) -> true) :: throttled2.entries.toList
      throttled2 = RadixTree(newEntries: _*)
    } else throttled.add(h)
    println("added: " + h.path)
  }
  def removeThrottled(h: APIHit) = {
    val newEntries = throttled2.entries.filterNot(_._1 == h.path.dropRight(1)).toSeq
    if (h.path.endsWith("*")) throttled2 = RadixTree(newEntries: _*) else throttled.remove(h)
  }

  def manageThrottled: Receive = {
    case RateOutOfBounds(n) => addThrottled(n)
    case RateWithinBounds(n) => removeThrottled(n)
  }
}

object ThrottleServiceActor {
  def props(throttleEventBus: ThrottleEventBus, apiHitEventBus: APIHitEventBus, connector: ActorRef): Props =
    Props(new ThrottleServiceActor(throttleEventBus, apiHitEventBus, connector))
}

