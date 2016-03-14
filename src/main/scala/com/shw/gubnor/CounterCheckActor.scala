package com.shw.gubnor

import akka.actor.{Actor, ActorRef}
import com.shw.gubnor.CounterActor.{CounterValue, GetValue}
import com.shw.gubnor.CounterCheckActor.Tick

import scala.concurrent.duration._

class CounterCheckActor(name: String, counterActor: ActorRef) extends Actor {
  import context.dispatcher
  val tick =
    context.system.scheduler.schedule(1 second, 1 second, self, Tick)

  override def postStop() = tick.cancel()

  def receive: Receive = {
    case Tick => counterActor ! GetValue
    case CounterValue(v) => println(s"counter $name: $v")
  }
}

object CounterCheckActor {
  case object Tick
}
