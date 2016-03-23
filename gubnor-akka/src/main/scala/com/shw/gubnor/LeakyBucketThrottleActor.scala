package com.shw.gubnor

import scala.concurrent.duration._
import akka.actor.{Actor, Props}
import com.shw.gubnor.APIHitEventBus.APIHit

/**
  * Throttling actor that implements the leaky bucket algorithm:
  * https://en.wikipedia.org/wiki/Leaky_bucket
  *
  * @param matchSpec API calls that match this realm and path pattern will be throttled by this actor
  * @param bucketSize size of the leaky bucket
  * @param drainFrequency rate at which the actor will send itself drain events
  * @param drainSize how much to drain the bucket upon each drain event
  */
abstract class LeakyBucketThrottleActor(
    matchSpec: APIHit,
    var bucketSize: Long = 2000,
    var drainFrequency: FiniteDuration = 1 second,
    var drainSize: Long = 300) extends Actor {

  //is both a throttle and a counter
  import ThrottleEvents._
  import CounterEvents._

  import LeakyBucketThrottleActor._

  import context.dispatcher

  def sendRateBoundaryEvent[T <: RateBoundaryEvent](e: T): Unit

  var counter = 0L

  val tick =
    context.system.scheduler.schedule(drainFrequency, drainFrequency, self, LeakTick)

  def receive = withinLimit

  def withinLimit: Receive = commonEvents orElse {
    case APIHit(path, realm) => {
      counter += 1
      if (counter >= bucketSize) {
        sendRateBoundaryEvent(RateOutOfBounds(matchSpec))
        context.become(overLimit)
      }
    }
    case n: Add => counter += n.v
    case LeakTick => {
      counter = if (counter < drainSize) 0 else counter - drainSize
    }
  }

  def overLimit: Receive = commonEvents orElse {
    case APIHit(path, realm) => { }
    case n: Add => { }
    case LeakTick => {
      counter = if (counter < drainSize) 0 else counter - drainSize
      if (counter < bucketSize) {
        sendRateBoundaryEvent(RateWithinBounds(matchSpec))
        context.become(withinLimit)
      }
    }
  }

  def commonEvents: Receive = {
    case GetValue => sender ! CounterValue(counter)
    case c@ChangeLimit(v) => { bucketSize = v; sender ! CommandAck(c) }
    case c@ChangeFrequency(v) => { drainFrequency = v; sender ! CommandAck(c) }
  }
}

object LeakyBucketThrottleActor {

  case object LeakTick
}

