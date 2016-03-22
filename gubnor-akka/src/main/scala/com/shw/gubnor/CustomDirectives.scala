package com.shw.gubnor

import shapeless._
import spray.routing.Directive1
import spray.routing._
import scala.xml.NodeSeq

object CustomDirectives extends Directives {

  val realm: Directive1[String] = {
    entity(as[NodeSeq]).hmap {
      case body :: HNil =>
        (body \ "authentication" \ "simple" \ "realm").text
    }
  }
}
