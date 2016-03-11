package com.shw.gubnor

import akka.agent.Agent
import scala.collection.mutable

object Counters {

  import scala.concurrent.ExecutionContext.Implicits.global

  val counters = new mutable.HashMap[String, Agent[Long]]()


  val counter = Agent(0L)
}
