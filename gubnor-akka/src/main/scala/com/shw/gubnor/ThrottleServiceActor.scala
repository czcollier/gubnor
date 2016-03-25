package com.shw.gubnor

import akka.actor.{ActorRef, Props}
import com.shw.gubnor.APIHitEventBus.APIHit
import com.shw.gubnor.PrefixTrie.Trie
import shapeless.{::, HNil}
import spray.routing._
import kamon.spray.KamonTraceDirectives.traceName
import spray.http.{HttpRequest, StatusCodes}

import scala.collection.mutable
import scala.xml.NodeSeq

class ThrottleServiceActor(
    throttleEventBus: ThrottleEventBus,
    apiHitEventBus: APIHitEventBus,
    connector: ActorRef) extends ProxyServiceActor(connector) {

  import ThrottleEvents._

  val logEmitter = context.actorOf(Props[LogEmitterActor])

  val throttled = mutable.Set[APIHit]()
  var throttled2 = Trie()

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

  val throttle: Route = ctx => ctx.complete(StatusCodes.BandwidthLimitExceeded, "request limit exceeded")

  val throttling: Route = path(RestPath) { p =>
    traceName("gubnor-throttles") {
        realm { r =>
          val hit = APIHit(p.toString, r)
          apiHitEventBus.publish(hit)
          println("checking: " + p.toString)
          val matches = throttled2.findPrefixesOf(p.toString)
          val isThrottled = matches.nonEmpty || throttled.contains(hit)
          if (isThrottled) throttle else proxy
        }
    }
  }

  def receive = runRoute(throttling) orElse manageThrottled

  def printThrottled = throttled2.foreach(println)

  def addThrottled(h: APIHit) = {
    if (h.path.endsWith("*")) throttled2 append h.path.dropRight(1) else throttled.add(h)
    println("added: " + h.path)
  }
  def removeThrottled(h: APIHit) = {
    if (h.path.endsWith("*")) throttled2 remove h.path.dropRight(1) else throttled.remove(h)
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



