package com.shw.gubnor

/**
  * Events accepted and sent by counters (counter API)
  */
object CounterEvents {
  //accepts
  case object Increment
  case object GetValue
  case class Add(v: Long)

  //sends
  case class CounterValue(v: Long)
}
