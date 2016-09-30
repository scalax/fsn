package net.scalax.fsn.slick_common

import io.circe.Json
import net.scalax.fsn.model.{SlickParam, StaticManyInfo, StaticManyUbw, UpdateStaticManyInfo}
import org.xarcher.cpoi.CellData
import slick.dbio.DBIO

import scala.concurrent.{ExecutionContext, Future}

case class PropertyInfo(
  property: String,
  typeName: String,
  inRetrieve: Boolean,
  canOrder: Boolean,
  isDefaultDesc: Boolean//,
  //selectRender: String,
  //retrieveRender: String,
  //inputRender: String
)

case class JsonView(properties: List[PropertyInfo], data: List[Map[String, Json]], sum: Int)

case class JsonOut(properties: List[PropertyInfo], data: SlickParam => DBIO[(List[Map[String, Json]], Int)]) {
  def toView(param: SlickParam)(implicit ec: ExecutionContext): DBIO[JsonView] = {
    data(param).map(t => JsonView(properties, t._1, t._2))
  }
}
case class PoiOut(properties: List[PropertyInfo], data: SlickParam => DBIO[(List[Map[String, CellData[_]]], Int)])