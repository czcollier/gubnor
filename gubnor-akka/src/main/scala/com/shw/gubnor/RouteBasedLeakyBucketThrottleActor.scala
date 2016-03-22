package com.shw.gubnor

import akka.actor.{ActorRef, Props}
import com.shw.gubnor.APIHitEventBus.APIHit
import com.shw.gubnor.ThrottleEvents.RateBoundaryEvent

import scala.concurrent.duration._

/**
  * Created by ccollier on 3/21/16.
  */
class RouteBasedLeakyBucketThrottleActor(
    matchSpec: APIHit,
    bucketSize: Long,
    drainFrequency: FiniteDuration,
    drainSize: Long,
    service: ActorRef) extends LeakyBucketThrottleActor(matchSpec, bucketSize, drainFrequency, drainSize) {

  override def sendRateBoundaryEvent[T <: RateBoundaryEvent](e: T) = service ! e
}

object RouteBasedLeakyBucketThrottleActor {
  def props(matchSpec: APIHit,
            bucketSize: Long = 2000,
            drainFrequency: FiniteDuration = 1 second,
            drainSize: Long = 300,
            service: ActorRef) = Props(new RouteBasedLeakyBucketThrottleActor(
    matchSpec,
    bucketSize,
    drainFrequency,
    drainSize,
    service))
}
