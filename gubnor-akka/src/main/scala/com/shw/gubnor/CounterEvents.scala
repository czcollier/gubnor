package com.shw.gubnor

object CounterEvents {
  case object Increment
  case object GetValue
  case class CounterValue(v: Long)
  case class Add(v: Long)
}
