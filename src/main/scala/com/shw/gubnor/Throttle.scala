package com.shw.gubnor

import com.shw.gubnor.APIHitEventBus.APIHit

import scala.concurrent.duration.FiniteDuration

class Throttle {
}

object Throttle {
  case object Tick
  trait ChangeCommand
  case class ChangeLimit(v: Long) extends ChangeCommand
  case class ChangeFrequency(v: FiniteDuration) extends ChangeCommand
  case class CommandAck[T <: ChangeCommand](cmd: T)
  trait RateBoundaryEvent { val key: APIHit }
  case class RateOutOfBounds(key: APIHit) extends RateBoundaryEvent
  case class RateWithinBounds(key: APIHit) extends RateBoundaryEvent
}
