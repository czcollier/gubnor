package com.shw.gubnor

import akka.actor.Actor
import akka.actor.Actor.Receive
import akka.agent.Agent
import com.shw.gubnor.CounterActor.{CounterValue, GetValue}
import spray.io.TickGenerator.Tick

import scala.concurrent.duration._

object AgentCounters {
  import scala.concurrent.ExecutionContext.Implicits.global

  val testCounter1 = Agent(0L)
}

class AgentCountCheckActor(agentCounter: Agent[Long], name: String) extends Actor {
  import context.dispatcher
  val tick =
    context.system.scheduler.schedule(1 second, 1 second, self, Tick)

  override def postStop() = tick.cancel()

  def receive = {
    case Tick => println(s" agent counter $name: ${agentCounter()}")
  }
}