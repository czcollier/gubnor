package com.shw.gubnor

import akka.actor.{ActorRef, Props}
import com.shw.gubnor.APIHitEventBus.APIHit
import com.shw.gubnor.PrefixTrie.Trie
import shapeless.{::, HNil}
import spray.routing._
import kamon.spray.KamonTraceDirectives.traceName
import spray.can.Http
import spray.http.{HttpRequest, HttpResponse, StatusCodes}

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

//  val realm: Directive1[String] = {
//    entity(as[NodeSeq]).hmap {
//      case body :: HNil =>
//        (body \ "authentication" \ "simple" \ "realm").text
//    }
//  }

  override def preStart = {
    throttleEventBus.subscribe(self, APIHit("*", "*"))
  }

  val throttle: Route = ctx => ctx.complete(503, "throttled")

  val noRoute: Receive = {
    case r: HttpRequest => connector ! r
  }

  def throttle(req: HttpRequest) = {
  }

  def handleHTTP: Receive = {
    case r: HttpRequest =>
      val hit = APIHit(r.uri.path.toString.drop(1), "")
      apiHitEventBus.publish(hit)
      val matches = throttled2.findPrefixesOf(hit.path)
      val isThrottled = matches.nonEmpty || throttled.contains(hit)
      if (isThrottled) sender ! HttpResponse(status = StatusCodes.BandwidthLimitExceeded)
      else pxy(r)
    case _: Http.Connected => sender ! Http.Register(self)
  }

  def receive = handleHTTP orElse manageThrottled
//  val throttling: Route = path(RestPath) { p =>
//    traceName("gubnor-throttles") {
//        realm { r =>
//        }
//    }
//  }

  //def receive = runRoute(throttling) orElse manageThrottled

  def printThrottled = throttled2.foreach(println)

  def addThrottled(h: APIHit) = {
    if (h.path.endsWith("*")) throttled2 append h.path.dropRight(1) else throttled.add(h)
  }
  def removeThrottled(h: APIHit) = {
    if (h.path.endsWith("*")) throttled2 remove h.path.dropRight(1) else throttled.remove(h)
  }

  def manageThrottled: Receive = {
    case RateOutOfBounds(n) =>  addThrottled(n)
    case RateWithinBounds(n) => removeThrottled(n)
  }
}

object ThrottleServiceActor {
  def props(throttleEventBus: ThrottleEventBus, apiHitEventBus: APIHitEventBus, connector: ActorRef): Props =
    Props(new ThrottleServiceActor(throttleEventBus, apiHitEventBus, connector))
}



