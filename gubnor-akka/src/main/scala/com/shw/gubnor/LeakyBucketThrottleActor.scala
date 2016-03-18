package com.shw.gubnor
import scala.concurrent.duration._
import CounterEvents._
import akka.actor.{Actor, Props}
import com.shw.gubnor.APIHitEventBus.APIHit

class LeakyBucketThrottleActor(
    matchSpec: APIHit,
    eventBus: ThrottleEventBus,
    hits: APIHitEventBus,
    var bucketSize: Long = 2000,
    var drainFrequency: FiniteDuration = 1 second,
    var drainSize: Long = 300) extends Actor {
  import Throttle._

  var counter = 0L

  import context.dispatcher

  val tick =
    context.system.scheduler.schedule(drainFrequency, drainFrequency, self, Tick)

  override def preStart = {
    hits.subscribe(self, matchSpec)
  }

  def receive = withinLimit

  def withinLimit: Receive = commonEvents orElse {
    case APIHit(path, realm) => {
      counter += 1
      if (counter >= bucketSize) {
        eventBus.publish(RateOutOfBounds(matchSpec))
        context.become(overLimit)
      }
    }
    case n: Add => counter += n.v
    case Tick => {
      counter = if (counter < drainSize) 0 else counter - drainSize
    }
  }

  def overLimit: Receive = commonEvents orElse {
    case APIHit(path, realm) => { }
    case n: Add => { }
    case Tick => {
      counter = if (counter < drainSize) 0 else counter - drainSize
      if (counter < bucketSize) {
        eventBus.publish(RateWithinBounds(matchSpec))
        context.become(withinLimit)
      }
    }
  }

  def commonEvents: Receive = {
    case GetValue => sender ! CounterValue(counter)
    case ChangeLimit(v) => bucketSize = v
    case ChangeFrequency(v) => drainFrequency = v
  }
}

object LeakyBucketThrottleActor {
  def props(matchSpec: APIHit,
            eventBus: ThrottleEventBus,
            hits: APIHitEventBus,
            bucketSize: Long = 2000,
            drainFrequency: FiniteDuration = 1 second,
            drainSize: Long = 300) = Props(new LeakyBucketThrottleActor(matchSpec, eventBus, hits, bucketSize, drainFrequency, drainSize))
}

