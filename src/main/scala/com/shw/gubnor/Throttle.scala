package com.shw.gubnor

import com.shw.gubnor.APIHitEventBus.APIHit

import scala.concurrent.duration.FiniteDuration

class Throttle {
}

object Throttle {
  case object Tick
  case class ChangeLimit(v: Long)
  case class ChangeFrequency(v: FiniteDuration)
  trait RateBoundaryEvent { val key: APIHit }
  case class RateOutOfBounds(key: APIHit) extends RateBoundaryEvent
  case class RateWithinBounds(key: APIHit) extends RateBoundaryEvent
}
