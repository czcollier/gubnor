package com.shw.gubnor

import akka.actor.{Actor, ActorRef}
import com.shw.gubnor.CounterCheckActor.Tick

import scala.concurrent.duration._

/**
  * Periodically checks a counter for its value and prints it
  *
  * @param name string to print along with count values
  * @param counterActor counter whose value we should check
  */
class CounterCheckActor(name: String, counterActor: ActorRef) extends Actor {
  import CounterEvents._

  import context.dispatcher

  val tick =
    context.system.scheduler.schedule(0 second, 5 second, self, Tick)

  override def postStop() = tick.cancel()

  def receive: Receive = {
    case Tick => counterActor ! GetValue
    case CounterValue(v) =>
      println(s"counter $name: $v")
  }
}

object CounterCheckActor {
  case object Tick
}
