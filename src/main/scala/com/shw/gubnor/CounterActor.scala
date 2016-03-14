package com.shw.gubnor

import akka.actor.{Actor, Props, Terminated}
import com.shw.gubnor.CounterActor.{Add, CounterValue, GetValue, Increment}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

class CounterActor(name: String) extends Actor {

  var counter = 0L

  def receive = {
    case Increment => counter += 1
    case n: Add => counter += n.v
    case GetValue => sender ! CounterValue(counter)
  }
}

object CounterActor {
  case object Increment
  case object GetValue
  case class CounterValue(v: Long)
  case class Add(v: Long)

  def props(name: String): Props = Props(new CounterActor(name))
}


import akka.routing.{ ActorRefRoutee, RoundRobinRoutingLogic, Router }

class CounterPoolActor(name: String, numRoutees: Int) extends Actor {

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
      val r = context.actorOf(Props(new CounterActor("counter " + i)))
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
