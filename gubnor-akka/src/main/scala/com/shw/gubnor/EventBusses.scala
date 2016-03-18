package com.shw.gubnor

import akka.actor.ActorRef
import akka.event.{ActorEventBus, SubchannelClassification}
import akka.util.Subclassification
import com.shw.gubnor.APIHitEventBus.APIHit
import com.shw.gubnor.ThrottleEvents.RateBoundaryEvent

class PathPrefixSubclassification extends Subclassification[APIHit] {
  override def isEqual(h1: APIHit, h2: APIHit): Boolean = h1 == h2
  override def isSubclass(h1: APIHit, h2: APIHit): Boolean = h1.matches(h2)
}

object APIHitEventBus {
  case class APIHit(path: String, realm: String) {
    def matches(that: APIHit) = {
      (that.realm == "*" || that.realm == realm) &&
        ((that.path == "*" || (that.path.endsWith("*") && path.startsWith(that.path.dropRight(1)))) || (that.path == path))
    }
  }
}

class APIHitEventBus extends ActorEventBus with SubchannelClassification {

  type Event = APIHit
  type Classifier = APIHit

  protected def publish(event: Event, subscriber: Subscriber): Unit = subscriber ! event

  protected def classify(event: Event): Classifier = event

  override protected implicit def subclassification: Subclassification[Classifier] =
    new PathPrefixSubclassification
}

class ThrottleEventBus extends ActorEventBus with SubchannelClassification {
  override type Classifier = APIHit
  override type Event = RateBoundaryEvent

  override protected def publish(event: Event, subscriber: ActorRef): Unit = subscriber ! event

  override protected def classify(event: Event): Classifier = event.key

  override protected implicit def subclassification: Subclassification[APIHit] =
    new PathPrefixSubclassification
}


