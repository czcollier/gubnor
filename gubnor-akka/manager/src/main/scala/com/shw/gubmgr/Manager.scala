package com.shw.gubmgr

import akka.actor.{Actor, ActorIdentity, ActorSystem, Identify, PoisonPill, Props}
import akka.util.Timeout
import com.shw.gubnor.CounterCheckActor
import com.shw.gubnor.HttpLoadBalancer.MonitoredMode
import com.shw.gubnor.ThrottleEvents.ChangeLimit
import akka.pattern.ask
import com.shw.gubnor.LeakyBucketThrottleActor.GetInfo

import scala.concurrent.Future
import scala.concurrent.duration._

object Manager extends App {

  implicit val system = ActorSystem("gubmgr")

  implicit val timeout: Timeout = Timeout(5 seconds)

  val systemConfig = system.settings.config

  implicit val executionContext = system.dispatcher
  val cmdPat2 = "([\\w]+)(?:[\\s]+(.+))?".r

  case class Cmd(cmd: String, mod: Option[String])

  def parseCommand(c: String) = c match {
    case cmdPat2(x, y) => Cmd(x, Option(y))
    case "" => Cmd("", Some(""))
  }

  implicit def onoff(s: String) = s match {
    case "on" => true
    case "off" => false
  }

  def withMod(o: Option[String], default: Option[String] = None)(f: (String) => Any) = o match {
    case Some(v) => f(v)
    case _ => default match {
      case None => throw new IllegalArgumentException ("need a command modifier")
      case Some(d) => f(d)
    }
  }

  var remoteHost = "localhost:2552"

  def pathPrefix(n: String) = s"akka.tcp://gubnor@$remoteHost" + n

  val out = system.actorOf(Props[Output])

  def prompt = (out ? NoNewline("~~> "))

  io.Source.stdin.getLines.foreach { cmd =>
    try {
      parseCommand(cmd) match {
        case Cmd(c, m) =>
          c match {
            case "connect" => withMod(m) { mod => remoteHost = mod }
            case "proxyMonitorMode" => withMod(m) { mod =>
              system.actorSelection(pathPrefix("/user/load-balancer")).resolveOne map { r =>
                (r ? MonitoredMode(mod)).onComplete(c => prompt)
              }
            }
            case "watch" => withMod(m) { mod: String =>
              system.actorSelection(pathPrefix("/user/throttle_" + mod)).resolveOne.map { t =>
                system.actorOf(Props(new CounterCheckActor(mod, t)), "counterCheck_" + t.path.elements.last)
                out ? ("watching: " + mod)
              }
              .recover {
                case e => println("couldn't find " + mod + ": " + e)
              }.onComplete(x => prompt)
            }
            case "unwatch" => withMod(m) { mod =>
              system.actorSelection("/user/counterCheck_throttle_" + mod).resolveOne.map { w =>
                out ? ("unwatching: " + w.path) ; system.stop(w)
              }.onComplete(x => prompt)
            }
            case "mod" => withMod(m) { mod =>
              val m2 = mod.split(" ") match {
                case Array(name, setting, value) => (name, setting, value.toLong)
              }
              m2 match {
                case (n, "limit", i) =>
                  system.actorSelection(pathPrefix("/user/throttle_" + n)).resolveOne map { t =>
                    (t ? ChangeLimit(i)).map { r =>
                      out ? ("changed: " + r)
                    }.recover {
                      case e => out ? ("failed: " + e)
                    }.onComplete(x => prompt)
                  }
              }
            }
            case "list" => withMod(m, Some("/user/throttle_*")) { mod =>
              val a = system.actorOf(Props(new Actor {
                def receive = {
                  case ActorIdentity(id, Some(ref)) => (ref ? GetInfo) map { i =>
                    println(s"${ref.path}: ${i}")
                  }
                  case ActorIdentity(id, None) => context.stop(self) ; prompt
                }
              }))
              val lo = system.actorSelection(pathPrefix(mod))
              lo.tell(Identify(1), a)
            }
            case "quit" =>
              system.terminate().map { f =>
                out ? (f.getActor.path + " terminated. exiting...")
                System.exit(0)
              }
            case "shutdown" =>
              val sys = system.actorSelection(pathPrefix("/system")).resolveOne map { s =>
                s ! PoisonPill
                out ! ("system stopped")
              }
              system.terminate().map { f =>
                out ? (f.getActor.path + " terminated. exiting...").map { ack =>
                  System.exit(0)
                }
              }
            case "" => prompt
          }
      }
    }
    catch {
      case e: Exception => println("bad command: " + e.getMessage)
    }
  }

  case object Ack
  case class NoNewline[T](payload: T)
  class Output extends Actor {
    def receive = {
      case NoNewline(x) => print(String.valueOf(x)) ; sender ! Ack
      case x => println(String.valueOf(x)) ; sender ! Ack
    }
  }
}
