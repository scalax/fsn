package net.scalax.fsn.database.test

import io.circe.{ Json, Printer }
import io.circe.syntax._
import io.circe.generic.auto._
import net.scalax.fsn.core.FPathImpl
import net.scalax.fsn.json.operation.{ FDefaultAtomicHelper, FPropertyAtomicHelper }
import net.scalax.fsn.mix.helpers.{ Slick2JsonFsnImplicit, SlickCRUDImplicits }
import net.scalax.fsn.slick.helpers.{ FJsonAtomicHelper, FSelectExtAtomicHelper, FStrSelectExtAtomicHelper, StrFSSelectAtomicHelper }
import net.scalax.fsn.slick.model._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.implicitConversions
import slick.jdbc.H2Profile.api._

import scala.concurrent._

object Sample06 extends SlickCRUDImplicits with StrFSSelectAtomicHelper with Slick2JsonFsnImplicit {

  val printer = Printer(
    preserveOrder = true,
    dropNullKeys = false,
    indent = " ",
    lbraceRight = " ",
    rbraceLeft = " ",
    lbracketRight = "\n",
    rbracketLeft = "\n",
    lrbracketsEmpty = "\n",
    arrayCommaRight = "\n",
    objectCommaRight = " ",
    colonLeft = " ",
    colonRight = " "
  )

  def prettyPrint(view: JsonView): Unit = {
    println("json data:\n" + view.data.asJson.pretty(printer) + "\n")
    println("properties:\n" + view.properties.asJson.pretty(printer) + "\n")
  }

  implicit def fPilesOptionImplicit[D](path: FPathImpl[D]): FJsonAtomicHelper[D] with FStrSelectExtAtomicHelper[D] with FPropertyAtomicHelper[D] with FDefaultAtomicHelper[D] = {
    val path1 = path
    new FJsonAtomicHelper[D] with FStrSelectExtAtomicHelper[D] with FPropertyAtomicHelper[D] with FDefaultAtomicHelper[D] {
      override val path = path1
    }
  }

  val fQuery = for {
    friend <- FriendTable.out
  } yield {
    List(
      "id" ofPile friend.id.out.order.describe("自增主键").inView(false).writeJ,
      "name" ofPile friend.name.out.orderTarget("nick").describe("昵称").writeJ,
      "nick" ofPile friend.nick.out.order.describe("昵称").inView(false).writeJ,
      "ageOpt" ofPile friend.age.out.writeJ
    )
  }

  val result1: JsonOut = fQuery.strResult

  val view1: DBIO[JsonView] = result1.toView(SlickParam(orders = List(ColumnOrder("name", true), ColumnOrder("id", false), ColumnOrder("ageOpt", false))))

  Await.result(Sample01.db.run {
    Sample01.initData
      .flatMap { _ =>
        view1.map { s =>
          prettyPrint(s)
        }
      }
  }, duration.Duration.Inf)

}