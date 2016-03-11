package com.shw.gubnor

import akka.actor.{Actor, Props, Terminated}
import com.shw.gubnor.CounterActor.{Add, GetValue, Increment}

class CounterActor(name: String) extends CounterCheckActor(name) {

  var counter = 0L

  def receive = receiveTick orElse {
    case Increment => counter += 1
    case n: Add => counter += n.v
    case GetValue => sender ! counter
  }

  override def getCurrent: Long = counter
}

object CounterActor {
  case object Increment
  case object GetValue
  case class Add(v: Long)

  def props(name: String): Props = Props(new CounterActor(name))
}


import akka.routing.{ ActorRefRoutee, RoundRobinRoutingLogic, Router }

class CounterPoolActor(numRoutees: Int) extends Actor {
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
