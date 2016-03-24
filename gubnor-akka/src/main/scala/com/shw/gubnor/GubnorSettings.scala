package com.shw.gubnor

import akka.actor.ActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem

import scala.concurrent.duration.FiniteDuration
import com.typesafe.config.{Config, ConfigObject}
import scala.collection.JavaConversions._

class GubnorSettingsImpl(config: Config) extends Extension {
  def cfgName(n: String) = "gubnor." + n

  val throttles = config.getObjectList(cfgName("throttles")) collect {
    case LeakyBucketThrottleConfig(c) => c
  }
  val endpoints = config.getObjectList(cfgName("endpoints")) collect {
    case EndpointConfig(e) => e
  }
}

object GubnorSettings extends ExtensionId[GubnorSettingsImpl] with ExtensionIdProvider {

  override def lookup = GubnorSettings

  override def createExtension(system: ExtendedActorSystem) =
    new GubnorSettingsImpl(system.settings.config)

  override def get(system: ActorSystem): GubnorSettingsImpl = super.get(system)
}

abstract class ThrottleConfig {
  val name: String
  val path: String
  val realm: String
}

case class LeakyBucketThrottleConfig(
  name: String,
  path: String,
  realm: String,
  bucketSize: Long,
  drainFrequency: FiniteDuration,
  drainSize: Long) extends ThrottleConfig


object LeakyBucketThrottleConfig {
  implicit def asFiniteDuration(d: java.time.Duration) =
    scala.concurrent.duration.Duration.fromNanos(d.toNanos)

  def unapply(co: ConfigObject) = {
    val cfg = co.toConfig
    Some(LeakyBucketThrottleConfig(
      cfg.getString("name"),
      cfg.getString("path"),
      cfg.getString("realm"),
      cfg.getLong("bucketSize"),
      cfg.getDuration("drainFrequency"),
      cfg.getLong("drainSize")))
  }
}

case class EndpointConfig(host: String, port: Int)

object EndpointConfig {
  def unapply(co: ConfigObject) = {
    val cfg = co.toConfig
    Some(EndpointConfig(
      cfg.getString("host"),
      cfg.getInt("port")
    ))
  }
}
