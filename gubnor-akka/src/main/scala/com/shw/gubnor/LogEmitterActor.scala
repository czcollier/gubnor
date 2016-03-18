package com.shw.gubnor

import akka.actor.Actor
import akka.event.{Logging, LoggingAdapter}

class LogEmitterActor extends Actor {
  import LogEmitterActor._

  val soakerLog: LoggingAdapter = Logging(context.system.eventStream, "soaker")

  def writeLogLine(line: String): Unit = {
    soakerLog.info(line)
  }

  def receive = {
    case p: Product => writeLogLine(productToLogLine(p))
  }
}

object LogEmitterActor {
  class FormattableOption[T](opt: Option[T]) {
    def stringOrBlank = {
      opt map (v => s"$v") getOrElse ""
    }
  }

  implicit def OptionToFormattableOption[T](opt: Option[T]) =  new FormattableOption(opt)

  def productToLogLine[P <: Product](p: P): String = {
    val fmt = Array.fill(p.productArity)("%s").mkString("\t")
    def optFmt(a: Any) = {
      a match {
        case p: Product => productToLogLine(p)
        case x => x
      }
    }
    val itr = p.productIterator.toArray.map(optFmt(_))
    fmt.format(itr: _*)
  }
}
