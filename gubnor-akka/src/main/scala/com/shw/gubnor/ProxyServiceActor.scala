package com.shw.gubnor

import akka.actor.ActorRef
import akka.util.Timeout
import spray.http.{HttpMessage, HttpRequest, HttpResponse}
import spray.routing.{HttpServiceActor, Route}
import akka.pattern.ask

import scala.concurrent.duration._

/**
  * Given a Spray HTTP connector, this actor uses the Spray host-level client API
  * @param connector
  */
abstract class ProxyServiceActor(connector: ActorRef) extends HttpServiceActor {
  implicit val timeout: Timeout = Timeout(5 seconds)
  implicit val ec = context.system.dispatcher

  val filteredHeaderNames = Set("Date", "Server", "Timeout-Access", "Content-Length", "Content-Type")
  def filterHeaders[T <: HttpMessage](msg: T) = {
    msg.withHeaders {
      msg.headers.filterNot { h => filteredHeaderNames.contains(h.name) }
    }
  }

 val proxy: Route = ctx =>
    ctx.complete((connector ? filterHeaders(ctx.request)).mapTo[HttpResponse].map(r => filterHeaders(r)))

}
