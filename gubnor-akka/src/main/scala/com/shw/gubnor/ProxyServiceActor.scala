package com.shw.gubnor

import akka.actor.ActorRef
import akka.util.Timeout
import spray.http.HttpResponse
import spray.routing.{HttpServiceActor, Route}
import akka.pattern.ask

import scala.concurrent.duration._

abstract class ProxyServiceActor(connector: ActorRef) extends HttpServiceActor {
  implicit val timeout: Timeout = Timeout(5 seconds)
  implicit val ec = context.system.dispatcher

  val filteredHeaderNames = Set("Date", "Server", "Timeout-Access", "Content-Length", "Content-Type")
  def filterHeaders(req: HttpResponse) = {
    req.withHeaders {
      req.headers.filterNot { h => filteredHeaderNames.contains(h.name) }
    }
  }

 val proxy: Route = ctx =>
    ctx.complete((connector ? ctx.request).mapTo[HttpResponse].map(filterHeaders))

}
