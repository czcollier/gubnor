package com.shw.gubnor

import akka.actor.{Actor, ActorRef}
import com.shw.gubnor.CounterActor.CounterValue

class PrinterActor extends Actor {
  val subscribers = List[ActorRef]()

  def receive = {
    case CounterValue(v) => println(v.toString)
  }
}
object PrinterActor {
  case class Subscribe(a: ActorRef)
}
