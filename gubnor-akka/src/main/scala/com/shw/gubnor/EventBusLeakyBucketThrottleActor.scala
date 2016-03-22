package com.shw.gubnor

import akka.actor.Props
import com.shw.gubnor.APIHitEventBus.APIHit
import com.shw.gubnor.ThrottleEvents.RateBoundaryEvent

import scala.concurrent.duration._

class EventBusLeakyBucketThrottleActor(
    matchSpec: APIHit,
    bucketSize: Long,
    drainFrequency: FiniteDuration,
    drainSize: Long,
    eventBus: ThrottleEventBus,
    hits: APIHitEventBus)

  extends LeakyBucketThrottleActor(matchSpec, bucketSize, drainFrequency, drainSize) {

  override def preStart = {
    hits.subscribe(self, matchSpec)
  }

  override def sendRateBoundaryEvent[T <: RateBoundaryEvent](e: T): Unit = eventBus.publish(e)
}

object EventBusLeakyBucketThrottleActor {
  def props(matchSpec: APIHit,
            eventBus: ThrottleEventBus,
            hits: APIHitEventBus,
            bucketSize: Long = 2000,
            drainFrequency: FiniteDuration = 1 second,
            drainSize: Long = 300) = Props(new EventBusLeakyBucketThrottleActor(
    matchSpec,
    bucketSize,
    drainFrequency,
    drainSize,
    eventBus,
    hits))

}
