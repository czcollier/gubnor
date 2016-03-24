package com.shw.gubnor

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorRef, Props}
import akka.util.Timeout
import spray.http.{HttpRequest, HttpResponse}
import spray.routing.{HttpServiceActor, Route}
import akka.pattern.ask
import com.shw.gubnor.ProxyServiceActor.ResponseMaster

import scala.concurrent.duration._

/**
  * Given a Spray HTTP connector, this actor uses the Spray host-level client API
  * to pass requests directly on to the configured host and respond with that hosts's response
  * (reverse proxy)
  *
  * @param connector
  */
abstract class ProxyServiceActor(connector: ActorRef) extends Actor {
  implicit val timeout: Timeout = Timeout(5 seconds)
  implicit val ec = context.system.dispatcher


  def pxy: Receive = {
    case r: HttpRequest =>
      val origin = sender
      val ta = context.actorOf(Props(new ResponseMaster(origin)))
      connector.tell(r, ta)
  }
}

object ProxyServiceActor {
  val filteredHeaderNames = Set("Date", "Server", "Timeout-Access", "Content-Length", "Content-Type")

  def filterHeaders(req: HttpResponse) = {
    req.withHeaders {
      req.headers.filterNot { h => filteredHeaderNames.contains(h.name) }
    }
  }
  class ResponseMaster(val respondTo: ActorRef) extends Actor {
    def receive = {
      case res: HttpResponse =>
        respondTo ! filterHeaders(res)
        context.stop(self)
    }
  }
}
