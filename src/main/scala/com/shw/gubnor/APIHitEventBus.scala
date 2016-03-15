package com.shw.gubnor

/**
  * An event bus where subscribers are actors.  Taken directly from:
  *
  * Wyatt, Derek (2013-05-24). Akka Concurrency (Kindle Locations 9702-9743). Artima Press. Kindle Edition.
  * And from code repository at:
  * https://github.com/danluu/akka-concurrency-wyatt/blob/master/src/main/scala/EventBusForActors.scala
  */

import akka.actor.ActorRef
import akka.event.{ActorEventBus, SubchannelClassification}
import akka.util.Subclassification
import com.shw.gubnor.APIHitEventBus.APIHit
import com.shw.gubnor.Throttle.RateBoundaryEvent

class PathStartsWithSubclassification extends Subclassification[APIHit] {
  override def isEqual(h1: APIHit, h2: APIHit): Boolean = h1 == h2
  override def isSubclass(h1: APIHit, h2: APIHit): Boolean = h1.matches(h2)
}

object APIHitEventBus {
  case class APIHit(path: String, realm: String) {
    def matches(that: APIHit) = {
      //print("comparing: " + this + " to " + that)
      val r = (that.realm == "*" || that.realm == realm) &&
        ((that.path == "*" || (that.path.endsWith("*") && path.startsWith(that.path.dropRight(1)))) || (that.path == path))
      //println(" -- got: " + r)
      r
    }
  }
}

class APIHitEventBus extends ActorEventBus with SubchannelClassification {

  type Event = APIHit
  type Classifier = APIHit

  protected def publish(event: Event, subscriber: Subscriber): Unit = {
    //println("publishing: " + event + " to " + subscriber)
    subscriber ! event
  }

  protected def classify(event: Event): Classifier = event

  override protected implicit def subclassification: Subclassification[Classifier] =
    new PathStartsWithSubclassification
}

class ThrottleEventBus extends ActorEventBus with SubchannelClassification {

  override type Classifier = APIHit
  override type Event = RateBoundaryEvent

  override protected def publish(event: Event, subscriber: ActorRef): Unit = { subscriber ! event }

  override protected def classify(event: Event): Classifier = event.key

  override protected implicit def subclassification: Subclassification[APIHit] =
    new PathStartsWithSubclassification
}


