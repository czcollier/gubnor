package com.shw.gubnor

import akka.actor.{Actor, ActorRef}
import com.shw.gubnor.CounterCheckActor.Tick

import scala.concurrent.duration._

abstract class CounterCheckActor(name: String) extends Actor {
  import context.dispatcher
  val tick =
    context.system.scheduler.schedule(1 second, 1 second, self, Tick)

  def getCurrent: Long

  override def postStop() = tick.cancel()

  def receiveTick: Receive = {
    case Tick => println(s"counter $name: ${getCurrent}")
  }
}

object CounterCheckActor {
  case object Tick
}
