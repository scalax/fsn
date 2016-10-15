package net.scalax.fsn.mix.helpers

import io.circe.{Decoder, Encoder, Json}
import net.scalax.fsn.common.atomic.FProperty
import net.scalax.fsn.core.{FAtomic, FColumn, FsnColumn}
import net.scalax.fsn.json.operation.JsonOperation
import net.scalax.fsn.mix.helpers.{Select => SSelect}
import net.scalax.fsn.mix.operation.PropertiesOperation
import net.scalax.fsn.mix.slickbase.{CrudQueryExtensionMethods, ListQueryExtensionMethods, ListQueryWrap, QueryWrap}
import net.scalax.fsn.slick.atomic.{AutoInc, SlickRetrieve}
import net.scalax.fsn.slick.helpers.{FRep, SlickUtils}
import net.scalax.fsn.slick.model._
import net.scalax.fsn.slick.operation._
import slick.basic.BasicProfile
import slick.dbio._
import slick.jdbc.JdbcActionComponent
import slick.lifted.{FlatShapeLevel, Query, Rep, Shape}
import slick.relational.RelationalProfile

import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe._
import scala.language.implicitConversions

trait Slick2JsonFsnImplicit {

  implicit class slick2jsonExtraClass(listQueryWrap: ListQueryWrap) {
    def result(defaultOrders: List[ColumnOrder])
                  (
                    implicit
                    jsonEv: Query[_, Seq[Any], Seq] => BasicProfile#StreamingQueryActionExtensionMethods[Seq[Seq[Any]], Seq[Any]],
                    repToDBIO: Rep[Int] => BasicProfile#QueryActionExtensionMethods[Int, NoStream],
                    ec: ExecutionContext
                  ): JsonOut = {
      lazy val withExtraCols = OutSelectConvert.extraSubCol(listQueryWrap.columns)
      lazy val queryWrap: JsonQuery = SelectOperation.encode(withExtraCols, listQueryWrap.listQueryBind)

      val gen = { slickParam: SlickParam =>
        queryWrap.jsonResult(defaultOrders).apply(slickParam).map { result =>
          result._1.map(JsonOperation.writeJ) -> result._2
        }
      }
      JsonOut(withExtraCols.map(PropertiesOperation.convertProperty), gen)
    }

    def result(orderColumn: String, isDesc: Boolean = true)
                  (
                    implicit
                    jsonEv: Query[_, Seq[Any], Seq] => BasicProfile#StreamingQueryActionExtensionMethods[Seq[Seq[Any]], Seq[Any]],
                    repToDBIO: Rep[Int] => BasicProfile#QueryActionExtensionMethods[Int, NoStream],
                    ec: ExecutionContext
                  ): JsonOut = {
      result( List(ColumnOrder(orderColumn, isDesc)))
    }

    def result
              (
                implicit
                jsonEv: Query[_, Seq[Any], Seq] => BasicProfile#StreamingQueryActionExtensionMethods[Seq[Seq[Any]], Seq[Any]],
                repToDBIO: Rep[Int] => BasicProfile#QueryActionExtensionMethods[Int, NoStream],
                ec: ExecutionContext
              ): JsonOut = {
      result( Nil)
    }
  }

}

trait Slick2CrudFsnImplicit extends Slick2JsonFsnImplicit {

  implicit class slick2crudExtraClass(crudQueryWrap: QueryWrap) {
    val columns = crudQueryWrap.listQueryWrap.columns
    lazy val properties = PropertiesOperation.convertColumn(columns)

    def result
    (defaultOrders: List[ColumnOrder])
    (
      implicit
      jsonEv: Query[_, Seq[Any], Seq] => BasicProfile#StreamingQueryActionExtensionMethods[Seq[Seq[Any]], Seq[Any]],
      repToDBIO: Rep[Int] => BasicProfile#QueryActionExtensionMethods[Int, NoStream],
      retrieve: Query[_, String, Seq] => BasicProfile#StreamingQueryActionExtensionMethods[Seq[String], String],
      insertConv: Query[_, Seq[Any], Seq] => JdbcActionComponent#InsertActionExtensionMethods[Seq[Any]],
      deleteConV: Query[RelationalProfile#Table[_], _, Seq] => JdbcActionComponent#DeleteActionExtensionMethods,
      updateConV: Query[_, Seq[Any], Seq] => JdbcActionComponent#UpdateActionExtensionMethods[Seq[Any]],
      ec: ExecutionContext
    ): QueryJsonInfo = {
      QueryJsonInfo(
        properties = properties,
        jsonGen = {
          crudQueryWrap.listQueryWrap.result(defaultOrders)
        },
        retrieveGen = { v: Map[String, Json] =>
          val jsonData = JsonOperation.readWithFilter(columns) { eachColumn =>
            FColumn.findOpt(eachColumn) { case s: SlickRetrieve[eachColumn.DataType] => s }.map(_.primaryGen.isDefined).getOrElse(false)
          } (v)
          for {
            execInfo <- RetrieveOperation.parseInsert(crudQueryWrap.binds, jsonData)
            staticMany = StaticManyOperation.convertList2Query(execInfo.columns)
            staticM <- DBIO.from(staticMany)
          } yield {
            val jsonResult = JsonOperation.writeJ(execInfo.columns)
            StaticManyInfo(properties, jsonResult, staticM)
          }
        },
        insertGen = { v: Map[String, Json] =>
          val jsonData = JsonOperation.readWithFilter(columns){ eachColumn =>
            ! FColumn.findOpt(eachColumn) { case s: AutoInc[eachColumn.DataType] => s }.map(_.isAutoInc).getOrElse(false)
          }(v)
          for {
            execInfo <- CreateOperation.parseInsert(crudQueryWrap.binds, jsonData)
            staticMany = StaticManyOperation.convertList2Query(execInfo.columns)
            staticM <- DBIO.from(staticMany)
          } yield {
            UpdateStaticManyInfo(execInfo.effectRows, staticM)
          }
        },
        deleteGen = (v: Map[String, Json]) => {
          val primaryColumns = columns.filter { col => FColumn.findOpt(col) { case retrieve: SlickRetrieve[col.DataType] => retrieve }.map(_.primaryGen.isDefined).getOrElse(false) }
          val jsonData = JsonOperation.readJ(primaryColumns)(v)
          val staticMany = StaticManyOperation.convertList2Query(jsonData)
          for {
            updateInfo <- DeleteOperation.parseInsert(crudQueryWrap.binds, jsonData)
            staticM <- DBIO.from(staticMany)
          } yield {
            updateInfo.copy(many = staticM).effectRows
          }
        },
        updateGen = (v: Map[String, Json]) => {
          val jsonData = JsonOperation.readJ(columns)(v)
          val staticMany = StaticManyOperation.convertList2Query(jsonData)
          for {
            updateInfo <- UpdateOperation.parseInsert(crudQueryWrap.binds, jsonData)
            staticM <- DBIO.from(staticMany)
          } yield {
            updateInfo.copy(many = staticM)
          }
        },
        staticMany = StaticManyOperation.convertList2Ubw(columns)
      )
    }

    def result
    (orderColumn: String, isDesc: Boolean = true)
    (
      implicit
      jsonEv: Query[_, Seq[Any], Seq] => BasicProfile#StreamingQueryActionExtensionMethods[Seq[Seq[Any]], Seq[Any]],
      repToDBIO: Rep[Int] => BasicProfile#QueryActionExtensionMethods[Int, NoStream],
      retrieve: Query[_, String, Seq] => BasicProfile#StreamingQueryActionExtensionMethods[Seq[String], String],
      insertConv: Query[_, Seq[Any], Seq] => JdbcActionComponent#InsertActionExtensionMethods[Seq[Any]],
      deleteConV: Query[RelationalProfile#Table[_], _, Seq] => JdbcActionComponent#DeleteActionExtensionMethods,
      updateConV: Query[_, Seq[Any], Seq] => JdbcActionComponent#UpdateActionExtensionMethods[Seq[Any]],
      ec: ExecutionContext
    ): QueryJsonInfo = {
      result(List(ColumnOrder(orderColumn, isDesc)))
    }

    def result
    (
      implicit
      jsonEv: Query[_, Seq[Any], Seq] => BasicProfile#StreamingQueryActionExtensionMethods[Seq[Seq[Any]], Seq[Any]],
      repToDBIO: Rep[Int] => BasicProfile#QueryActionExtensionMethods[Int, NoStream],
      retrieve: Query[_, String, Seq] => BasicProfile#StreamingQueryActionExtensionMethods[Seq[String], String],
      insertConv: Query[_, Seq[Any], Seq] => JdbcActionComponent#InsertActionExtensionMethods[Seq[Any]],
      deleteConV: Query[RelationalProfile#Table[_], _, Seq] => JdbcActionComponent#DeleteActionExtensionMethods,
      updateConV: Query[_, Seq[Any], Seq] => JdbcActionComponent#UpdateActionExtensionMethods[Seq[Any]],
      ec: ExecutionContext
    ): QueryJsonInfo = {
      result(Nil)
    }
  }

}