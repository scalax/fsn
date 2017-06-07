package net.scalax.fsn.core

import shapeless._

import scala.language.implicitConversions

trait FPileAbstract {
  self =>

  type PathType
  type DataType

  val pathPile: PathType

  val fShape: FsnShape[PathType, DataType]

  val dataFromSub: List[Any] => DataType
  val subs: List[FPileAbstract]

  def genPiles: List[FPileAbstract]

}

trait FPile extends FPileAbstract {
  self =>

  //val genPiles: FPile => List[FPile]

  val dataListFromSubList: List[FAtomicValue] => List[FAtomicValue] = { list =>
    val result = genPiles.zip(ListUtils.splitList(list, genPiles.map(_.dataLengthSum): _*)).flatMap {
      case (eachPile, eachData) =>
        if (eachPile.subs.isEmpty) {
          list
        } else if (eachPile.subs.forall(_.genPiles.forall(_.subs.isEmpty))) {
          val totalSubs = eachPile.subs.flatMap(_.genPiles)
          eachPile.fShape.encodeData(eachPile.dataFromSub {
            val dataList = totalSubs.zip(ListUtils.splitList(eachData, totalSubs.map(_.dataLengthSum): _*)).flatMap {
              case (eachSub, subData) =>
                //eachSub.fShape.decodeData()
                eachSub.dataListFromSubList(subData)
            }
            eachPile.subs.zip(ListUtils.splitList(dataList, eachPile.subs.map(_.dataLengthSum): _*)).map {
              case (eachSub, subData) =>
                //eachSub.fShape.decodeData()
                eachSub.fShape.decodeData(subData)
            }
          })
        } else {
          val totalSubs = eachPile.subs.flatMap(_.genPiles)
          totalSubs.zip(ListUtils.splitList(eachData, totalSubs.map(_.dataLengthSum): _*)).flatMap {
            case (pile, data) =>
              pile.dataListFromSubList(data)
          }
        }
    }
    println(result)
    result
    /*if (self.subs.isEmpty) {
      list
    } else if (self.subs.forall(_.subs.isEmpty)) {
      self.fShape.encodeData(self.dataFromSub(self.subs.zip(ListUtils.splitList(list, self.subs.map(_.dataLengthSum): _*)).map {
        case (eachSub, subData) =>
          eachSub.fShape.decodeData(eachSub.dataListFromSubList(subData))
      }))
    } else {
      println(self)
      println(self.subs.map(_.dataLengthSum))
      println(list)
      self.subs.zip(ListUtils.splitList(list, self.subs.map(_.dataLengthSum): _*)).flatMap {
        case (pile, data) =>
          pile.dataListFromSubList(data)
      }
    }*/
  }

  lazy val paths: List[FAtomicPath] = genPiles.flatMap(s => s.fShape.encodeColumn(s.pathPile))

  lazy val dataLengthSum: Int = {
    /*val autalSubs = genPiles.flatMap(_.subs)
    if (autalSubs.isEmpty) {
      //fShape.dataLength
      genPiles.map { s => s.fShape.dataLength }.sum
    } else {
      autalSubs.map(_.dataLengthSum).sum
    }*/
    genPiles.map { s =>
      if (s.subs.isEmpty) {
        s.fShape.dataLength
      } else {
        s.subs.map(_.dataLengthSum).sum
      }
    }.sum
  }

  def deepZero: List[FAtomicValue] = {
    val autalSubs = genPiles.flatMap(_.subs)
    if (autalSubs.isEmpty) {
      //fShape.encodeData(fShape.zero)
      genPiles.flatMap { s => s.fShape.encodeData(s.fShape.zero) }
    } else {
      subs.flatMap(s => s.genPiles.flatMap(_.deepZero))
      //subs.flatMap(s => s.deepZero)
    }
    genPiles.flatMap { s =>
      if (s.subs.isEmpty) {
        s.fShape.encodeData(s.fShape.zero)
      } else {
        s.subs.flatMap(_.deepZero)
      }
    }
  }

  override val subs: List[FPile]

  override def toString: String = {
    s"""Node(length:${dataLengthSum}): {
       |${genPiles.flatMap(s => s.fShape.encodeColumn(s.pathPile)).mkString("\n").split("\\n").map(s => "  " + s).mkString("\n")}
       |""".stripMargin +
      s"""
       |  Children: {
       |${subs.map(_.toString).mkString("\n").split("\\n").map(s => "    " + s).mkString("\n")}
       |  }
       |}""".stripMargin
  }

  override def genPiles: List[FPile]

}

abstract class FPileImpl[E, U](
    override val pathPile: E,
    override val fShape: FsnShape[E, U],
    override val dataFromSub: List[Any] => U,
    override val subs: List[FPile]
) extends FPile {
  self =>

  override def genPiles: List[FPile] //= List(self)

  override type PathType = E
  override type DataType = U

  trait Abc[G, H, I] {
    def transform(cv: G => I): FPileImpl[H, I]
  }

  def poly[A, B](other: FPileImpl[A, B]): Abc[U, A, B] = {
    new Abc[U, A, B] {
      override def transform(cv: U => B): FPileImpl[A, B] = {

        /*val dataFromSub: List[Any] => B = { list =>
          val piles = List(self) //self.genPiles(self)
          val subData = self.fShape.decodeData(piles.zip(ListUtils.splitList(list, piles.map(_ => 1): _*)).map {
            case (eachPile, data) =>
              eachPile.fShape.encodeData(data.head.asInstanceOf[eachPile.DataType])
          }.flatten)

          cv(subData)
        }*/
        val dataFromSub: List[Any] => B = { list =>
          //val piles = List(self) //self.genPiles(self)
          /*val subData = self.fShape.decodeData(self.subs.zip(list).map {
            case (eachPile, data) =>
              eachPile.fShape.encodeData(data.asInstanceOf[eachPile.DataType])
          }.flatten)*/
          cv(list.head.asInstanceOf[self.DataType])
        }

        new FPileImpl(other.pathPile, other.fShape, dataFromSub, List(self) /*.genPiles(self)*/ ) {
          implSelf =>
          override def genPiles = List(implSelf) //throw new Exception("不应该使用")
        } /*({ source => other.genPiles(source) })*/
      }
    }
  }

}

object FPile {

  class bbbb[E <: HList, U <: HList](bb: FPileImpl[E, U]) {

    def ::[A, B](cc: FPileImpl[A, B]): FPileImpl[A :: E, B :: U] = {
      val pathPile = cc.pathPile :: bb.pathPile
      val shape: FsnShape[A :: E, B :: U] = new FsnShape[A :: E, B :: U] {
        override def encodeColumn(pile: A :: E): List[FAtomicPath] = {
          //val sub :: tail = pile
          //cc.fShape.encodeColumn(sub) ::: bb.fShape.encodeColumn(tail)
          throw new Exception("合并后的 FPile 不应该使用 encodeColumn")
        }
        override def encodeData(pileData: B :: U): List[FAtomicValue] = {
          //val sub :: tail = pileData
          //cc.fShape.encodeData(sub) ::: bb.fShape.encodeData(tail)
          throw new Exception("合并后的 FPile 不应该使用 encodeData")
        }
        override def decodeData(data: List[FAtomicValue]): B :: U = {
          cc.fShape.decodeData(data.take(cc.fShape.dataLength)) :: bb.fShape.decodeData(data.drop(cc.fShape.dataLength))
          //throw new Exception("合并后的 FPile 不应该使用 decodeData")
        }

        override def zero = {
          //cc.fShape.zero :: bb.fShape.zero
          throw new Exception("合并后的 FPile 不应该使用 zero")
        }

        override def dataLength = {
          //cc.fShape.dataLength + bb.fShape.dataLength
          throw new Exception("合并后的 FPile 不能求出长度")
        }
      }
      val dataFromSub: List[Any] => (B :: U) = { list =>
        /*val piles: List[FPile] = cc.subs ::: bb.subs
        shape.decodeData(piles.zip(ListUtils.splitList(list, piles.map(_ => 1): _*)).map {
          case (eachPile, data) =>
            eachPile.fShape.encodeData(data.head.asInstanceOf[eachPile.DataType])
        }.flatten)*/
        cc.dataFromSub(list.take(cc.subs.size)) :: bb.dataFromSub(list.drop(cc.subs.size))
      }

      new FPileImpl(pathPile, shape, dataFromSub, cc.subs ::: bb.subs) {
        override val genPiles: List[FPile] = cc.genPiles ::: bb.genPiles //List(cc, bb)
      } /*({ cusType =>
        val newCus = cusType.asInstanceOf[FPileImpl[A :: E, B :: U]]
        val ccPathImpl = new FPileImpl(newCus.pathPile.head, cc.fShape, cc.dataFromSub,)
        cc.genPiles(cc) ::: bb.genPiles(bb)
      })*/
    }
  }

  implicit def convert[E <: HList, U <: HList](bb: FPileImpl[E, U]): bbbb[E, U] = new bbbb(bb)

  val empty: FPileImpl[HNil, HNil] = {
    val shape = FsnShape.hnilFsnShape
    new FPileImpl(HNil, shape, { _: List[Any] => shape.zero }, List.empty[FPile]) {
      self =>
      override def genPiles = List(self) //throw new Exception("不应该使用")
    } /*({ cusType =>
      cusType :: Nil
    })*/
  }

  def apply[D](paths: FAtomicPathImpl[D]): FPileImpl[FAtomicPathImpl[D], FAtomicValueImpl[D]] = {
    val shape = FsnShape.fpathFsnShape[D]
    new FPileImpl(paths, shape, { _: List[Any] =>
      shape.zero
    }, List.empty[FPile]) {
      self =>
      override def genPiles = List(self) //throw new Exception("不应该使用")
    } /*({ cusType =>
      cusType :: Nil
    })*/
  }

  def genTreeTailCall[U](pathGen: FAtomicPath => FQueryTranform[U], oldPile: FPile, newPile: FPile): Either[FAtomicException, (FPile, List[FPile])] = {
    if (newPile.subs.isEmpty) {
      println(newPile)
      val transforms = newPile.paths.map(pathGen)
      if (transforms.forall(_.gen.isRight)) {
        Right(newPile, List(oldPile))
      } else {
        Left(FAtomicException(transforms.map(_.gen).collect { case Left(FAtomicException(s)) => s }.flatten))
      }
    } else {
      val newSubs = oldPile.subs.flatMap(_.genPiles).zip(newPile.subs.flatMap(_.genPiles)).map { case (eachOldPile, eachNewPile) => genTreeTailCall(pathGen, eachOldPile, eachNewPile) }
      if (newSubs.forall(_.isRight)) {
        val (newSubTree, successNodes) = newSubs.map(_.right.get).unzip
        val newNode = new FPileImpl(newPile.pathPile, newPile.fShape, newPile.dataFromSub, newSubTree) {
          override val genPiles: List[FPile] = List(this) //newPile.genPiles
        } /*(newPile.genPiles)*/
        Right(newNode, successNodes.flatten)
      } else {
        genTreeTailCall(pathGen, oldPile, new FPileImpl(newPile.pathPile, newPile.fShape, (_: List[Any]) => newPile.fShape.zero, Nil) {
          override val genPiles: List[FPile] = List(this) //newPile.genPiles
        } /*()*/ )
      }
    }
  }

  /*
  def genTreeTailCall[U](pathGen: FAtomicPath => FQueryTranform[U], oldPile: FPile, newPile: FPile): Either[FAtomicException, (FPile, FPile, List[FPile])] = {
    if (newPile.subs.isEmpty) {
      println(newPile)
      val transforms = newPile.paths.map(pathGen)
      if (transforms.forall(_.gen.isRight)) {
        Right(oldPile, newPile, List(oldPile))
      } else {
        Left(FAtomicException(transforms.map(_.gen).collect { case Left(FAtomicException(s)) => s }.flatten))
      }
    } else {
      val newSubs = oldPile.subs.flatMap(_.genPiles).zip(newPile.subs.flatMap(_.genPiles)).map { case (eachOldPile, eachNewPile) => genTreeTailCall(pathGen, eachOldPile, eachNewPile) }
      if (newSubs.forall(_.isRight)) {
        val (_, newSubTree, successNodes) = newSubs.map(_.right.get).unzip3
        val newNode = new FPileImpl(newPile.pathPile, newPile.fShape, newPile.dataFromSub, newSubTree) {
          override val genPiles: List[FPile] = List(this) //newPile.genPiles
        } /*(newPile.genPiles)*/
        Right(oldPile, newNode, successNodes.flatten)
      } else {
        genTreeTailCall(pathGen, oldPile, new FPileImpl(newPile.pathPile, newPile.fShape, (_: List[Any]) => newPile.fShape.zero, Nil) {
          override val genPiles: List[FPile] = List(this) //newPile.genPiles
        } /*()*/ )
      }
    }
  }
    */

  def genTree[U](pathGen: FAtomicPath => FQueryTranform[U], pile: FPile): Either[FAtomicException, (FPile, List[FPile])] = {
    genTreeTailCall(pathGen, pile, pile).right.map { case (newPile, piles) => newPile -> piles }
  }

  def transformTree[U, T](pathGen: FAtomicPath => FQueryTranform[U])(columnGen: List[U] => T): FPile => Either[FAtomicException, (FPile, List[FAtomicValue] => T)] = {
    (pile: FPile) =>
      {
        genTree(pathGen, pile).right.map {
          case (rightPile, piles) =>
            rightPile -> { anyList: List[FAtomicValue] =>
              val newList = ListUtils.splitList(anyList, piles.map(_.dataLengthSum): _*).zip(piles).flatMap { case (data, eachPile) => eachPile.dataListFromSubList(data) }
              transformOf(pathGen)(columnGen)(piles.map(_.paths).flatten).right.get.apply(newList)
            }
        }
      }
  }

  def transformTreeList[U, T](pathGen: FAtomicPath => FQueryTranform[U])(columnGen: List[U] => T): FPileSyntax.PileGen[T] = {
    prePiles: List[FPile] =>
      //防止定义 FPile 时最后一步使用了混合后不能识别最后一层 path
      val piles = prePiles.flatMap(eachPile => eachPile.genPiles)

      val calculatePiles = piles.map { s =>
        genTree(pathGen, s)
      }.foldLeft(Right(Nil): Either[FAtomicException, List[(FPile, List[FPile])]]) {
        (append, eitherResult) =>
          (append -> eitherResult) match {
            case (Left(s), Left(t)) =>
              Left(FAtomicException(s.typeTags ::: t.typeTags))
            case (Left(s), Right(_)) =>
              Left(FAtomicException(s.typeTags))
            case (Right(_), Left(s)) =>
              Left(FAtomicException(s.typeTags))
            case (Right(s), Right(t)) =>
              Right(t :: s)
          }
      }.right.map(_.reverse)
      calculatePiles.right.map { pileList =>
        val (newPile, summaryPiles) = pileList.unzip
        newPile -> { anyList: List[FAtomicValue] =>
          //println(anyList)
          //println(summaryPiles.map(_.map(_.dataLengthSum).sum))
          println(newPile)
          columnGen(ListUtils.splitList(anyList, summaryPiles.map(_.map(_.dataLengthSum).sum): _*)
            .zip(summaryPiles)
            .flatMap {
              case (subList, subPiles) =>
                ListUtils.splitList(subList, subPiles.map(_.dataLengthSum): _*).zip(subPiles).flatMap {
                  case (eachList, eachPiles) =>
                    eachPiles.paths.map(s => pathGen(s)).zip(eachPiles.dataListFromSubList(eachList)).map {
                      case (tranform, data) =>
                        tranform.apply(tranform.gen.right.get, data.asInstanceOf[FAtomicValueImpl[tranform.path.DataType]])
                    }
                }
            })
        }
      }

  }

  def genTreeTailCallWithoutData[U](pathGen: FAtomicPath => FQueryTranformWithOutData[U], oldPile: FPile, newPile: FPile): Either[FAtomicException, (FPile, FPile, List[FPile])] = {
    /*if (newPile.subs.isEmpty) {
      val transforms = newPile.paths.map(pathGen)
      if (transforms.forall(_.gen.isRight)) {
        Right(oldPile, newPile, List(oldPile))
      } else {
        Left(FAtomicException(transforms.map(_.gen).collect { case Left(FAtomicException(s)) => s }.flatten))
      }
    } else {*/
    if (newPile.subs.isEmpty) {
      val transforms = newPile.paths.map(pathGen)
      if (transforms.forall(_.gen.isRight)) {
        Right(oldPile, newPile, List(oldPile))
      } else {
        Left(FAtomicException(transforms.map(_.gen).collect { case Left(FAtomicException(s)) => s }.flatten))
      }
    } else {
      val newSubs = oldPile.subs.flatMap(_.genPiles).zip(newPile.subs.flatMap(_.genPiles)).map { case (eachOldPile, eachNewPile) => genTreeTailCallWithoutData(pathGen, eachOldPile, eachNewPile) }
      //val newSubs = oldPile.subs.zip(newPile.subs).map { case (eachOldPile, eachNewPile) => genTreeTailCallWithoutData(pathGen, eachOldPile, eachNewPile) }
      if (newSubs.forall(_.isRight)) {
        val (_, newSubTree, successNodes) = newSubs.map(_.right.get).unzip3
        val newNode = new FPileImpl(newPile.pathPile, newPile.fShape, newPile.dataFromSub, newSubTree) {
          self =>
          //TODO
          override def genPiles = List(self) //throw new Exception("不应该使用")
        } /*()*/
        Right(oldPile, newNode, successNodes.flatten)
      } else {
        genTreeTailCallWithoutData(pathGen, oldPile, new FPileImpl(newPile.pathPile, newPile.fShape, (_: List[Any]) => newPile.fShape.zero, Nil) {
          self =>
          //TODO
          override def genPiles = List(self) //throw new Exception("不应该使用")
        })
      }
    }
  }

  def genTreeWithoutData[U](pathGen: FAtomicPath => FQueryTranformWithOutData[U], pile: FPile): Either[FAtomicException, (FPile, List[FPile])] = {
    genTreeTailCallWithoutData(pathGen, pile, pile).right.map { case (oldPile, newPile, piles) => newPile -> piles }
  }

  def transformTreeListWithoutData[U, T](pathGen: FAtomicPath => FQueryTranformWithOutData[U])(columnGen: List[U] => T): FPileSyntaxWithoutData.PileGen[T] = {
    prePiles: List[FPile] =>
      //防止定义 FPile 时最后一步使用了混合后不能识别最后一层 path
      val piles = prePiles.flatMap(eachPile => eachPile.genPiles)
      val calculatePiles = piles.map { s =>
        genTreeWithoutData(pathGen, s)
      }.foldLeft(Right(Nil): Either[FAtomicException, List[(FPile, List[FPile])]]) {
        (append, eitherResult) =>
          (append -> eitherResult) match {
            case (Left(s), Left(t)) =>
              Left(FAtomicException(s.typeTags ::: t.typeTags))
            case (Left(s), Right(_)) =>
              Left(FAtomicException(s.typeTags))
            case (Right(_), Left(s)) =>
              Left(FAtomicException(s.typeTags))
            case (Right(s), Right(t)) =>
              Right(t :: s)
          }
      }.right.map(_.reverse)
      calculatePiles.right.map { pileList =>
        val (newPile, summaryPiles) = pileList.unzip
        newPile -> {
          columnGen(summaryPiles.map { subPiles =>
            subPiles.map { eachPiles =>
              eachPiles.paths.map(s => pathGen(s)).map { tranform =>
                tranform.apply(tranform.gen.right.get)
              }
            }
          }.flatten.flatten)
        }
      }
  }

  def transformOf[U, T](pathGen: FAtomicPath => FQueryTranform[U])(columnGen: List[U] => T): List[FAtomicPath] => Either[FAtomicException, List[FAtomicValue] => T] = {
    (initPaths: List[FAtomicPath]) =>
      {
        initPaths.map(pathGen).zipWithIndex.foldLeft(Right { _: List[FAtomicValue] => Nil }: Either[FAtomicException, List[FAtomicValue] => List[U]]) {
          case (convert, (queryTranform, index)) =>
            (convert -> queryTranform.gen) match {
              case (Left(s), Left(t)) =>
                Left(FAtomicException(s.typeTags ::: t.typeTags))
              case (Left(s), Right(_)) =>
                Left(FAtomicException(s.typeTags))
              case (Right(_), Left(s)) =>
                Left(FAtomicException(s.typeTags))
              case (Right(s), Right(t)) =>
                Right { list: List[FAtomicValue] =>
                  queryTranform.apply(t, list(index).asInstanceOf[FAtomicValueImpl[queryTranform.path.DataType]]) :: s(list)
                }
            }
        }.right.map { s => (t: List[FAtomicValue]) => {
          columnGen(s(t))
        }
        }
      }
  }
}