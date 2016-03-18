package com.shw.gubnor

import akka.actor.ActorRef

/**
  * Created by ccollier on 3/15/16.
  */
object EventPublisher {
  case class RegisterListener(listener: ActorRef)
  case class UnregisterListener(listener: ActorRef)
}
