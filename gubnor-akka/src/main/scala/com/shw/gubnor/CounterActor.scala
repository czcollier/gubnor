package com.shw.gubnor

import akka.actor.{Actor, Props, Terminated}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Simple counting actor.  Not currently used in gubnor anywhere.
  * Retained because CounterPoolActor here serves as a concise example
  * of how to create a pool of actors and route to them.
  * @param name
  */
class CounterActor(name: String) extends Actor {

  import CounterEvents._

  var counter = 0L

  def receive = {
    case Increment => counter += 1
    case n: Add => counter += n.v
    case GetValue => sender ! CounterValue(counter)
  }
}

object CounterActor  {
  def props(name: String): Props = Props(new CounterActor(name))
}


import akka.routing.{ ActorRefRoutee, RoundRobinRoutingLogic, Router }

class CounterPoolActor(name: String, numRoutees: Int) extends Actor {

  import CounterEvents._

  implicit val ec = context.system.dispatcher
  implicit val timeout: Timeout = Timeout(5 seconds)

  def getCurrent = {
    val cntFut = context.children.map { c =>
      (c ? GetValue).mapTo[Long]
    }

    Future.sequence(cntFut).map(_.sum)
  }

  var router = {
    val routees = 1.to(numRoutees) map { i =>
      val r = context.actorOf(CounterActor.props("counter " + i))
      context watch r
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  def receive = {
    case Terminated(a) =>
      router = router.removeRoutee(a)
      val r = context.actorOf(CounterActor.props("new counter"))
      context watch r
      router = router.addRoutee(r)
    case a =>
      router.route(a, sender())
  }
}
