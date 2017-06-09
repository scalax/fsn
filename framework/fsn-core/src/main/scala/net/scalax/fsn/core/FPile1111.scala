package net.scalax.fsn.core

import net.scalax.fsn.core.ListUtils.WeightData
import shapeless._

sealed abstract trait FPileAbs1111 {
  self =>
  type DataType

  def dataLengthSum: Int = {
    self match {
      case pList: FPileList =>
        pList.encodePiles(pList.pileEntity).map(_.dataLengthSum).sum
      case pile: FLeafPile =>
        pile.fShape.dataLength
      case pile: FPile =>
        pile.subs.dataLengthSum
    }
  }

  def deepZero: List[FAtomicValue] = {
    self match {
      case pList: FPileList =>
        pList.encodePiles(pList.pileEntity).flatMap(_.deepZero)
      case pile: FLeafPile =>
        pile.fShape.encodeData(pile.fShape.zero)
      case pile: FPile =>
        pile.subs.deepZero
    }
  }

  def subsCommonPile: List[FLeafPile] = self match {
    case pile: FLeafPile =>
      List(pile)
    case pile: FPile =>
      pile.subs.subsCommonPile
    case pile: FPileList =>
      pile.encodePiles(pile.pileEntity).flatMap(_.subsCommonPile)
  }

  def selfPaths: List[FAtomicPath] = self match {
    case pile: FCommonPile =>
      pile.fShape.encodeColumn(pile.pathPile)
    case pile: FPileList =>
      pile.encodePiles(pile.pileEntity).flatMap(_.selfPaths)
  }

  def dataListFromSubList(atomicDatas: List[FAtomicValue]): List[FAtomicValue] = {
    val leave = subsCommonPile
    val atomicValueList = ListUtils.splitList(atomicDatas, leave.map(_.dataLengthSum): _*)
    val weightData = leave.zip(atomicValueList).map { case (eachPile, values) => WeightData(values, eachPile.dataLengthSum) }
    weightDataListFromSubList(weightData).flatMap(_.data)
  }

  def weightDataListFromSubList(atomicDatas: List[WeightData[FAtomicValue]]): List[WeightData[FAtomicValue]] = {
    self match {
      case s: FPileList =>
        //如果是 pileList，直接分组再递归调用
        val piles = s.encodePiles(s.pileEntity)
        val datas = ListUtils.splitWithWeight1111(atomicDatas, piles.map(_.dataLengthSum): _*)
        val pileWithData = if (piles.size == datas.size) {
          piles.zip(datas)
        } else {
          throw new Exception("pile 与数据长度不匹配")
        }
        pileWithData.flatMap {
          case (eachPile, eachData) =>
            eachPile.weightDataListFromSubList(eachData)
        }
      case _: FLeafPile =>
        atomicDatas
      case s: FPile =>
        val subPiles = s.subs
        val subData = subPiles.weightDataListFromSubList(atomicDatas)
        subPiles match {
          case sp: FCommonPile =>
            if (subData.size != 1) {
              throw new Exception("FCommonPile 的权重数据长度必须为 1")
            }
            val subPileData = sp.fShape.decodeData(subData.head.data)
            val currentPileData = s.dataFromSub(subPileData)
            val resultDataList = s.fShape.encodeData(currentPileData)
            List(WeightData(resultDataList, s.dataLengthSum))
          case sp: FPileList =>
            val piles = sp.encodePiles(sp.pileEntity)
            if (subData.size != piles.size) {
              throw new Exception("FPileList 的权重数据长度和 pile 数量不一致")
            }
            val subDataList = ListUtils.splitWithWeight1111(subData, piles.map(_.dataLengthSum): _*)
            val pileWithData = piles.zip(subDataList)
            val currentPileData = sp.decodePileData {
              pileWithData.map {
                case (eachPile, subData) =>
                  if (subData.size != 1) {
                    throw new Exception("FCommonPile 的权重数据长度必须为 1")
                  }
                  eachPile.fShape.decodeData(subData.head.data)
              }
            }
            val resultDataList = s.fShape.encodeData(s.dataFromSub(currentPileData))
            List(WeightData(resultDataList, s.dataLengthSum))
        }
    }
  }
}

object FPileAbs1111 {
  def apply[D](paths: FAtomicPathImpl[D]): FLeafPileImpl[FAtomicPathImpl[D], FAtomicValueImpl[D]] = {
    val shape = FsnShape.fpathFsnShape[D]
    new FLeafPileImpl(paths, shape)
  }
}

trait FPileList extends FPileAbs1111 {
  type PileType
  override type DataType

  val pileEntity: PileType

  def encodePiles(piles: PileType): List[FCommonPile]
  def decodePiles(piles: List[FCommonPile]): PileType
  def decodePileData(datas: List[Any]): DataType

}

class FPileListImpl[PT, DT](
    override val pileEntity: PT,
    encoder: PT => List[FCommonPile],
    decoder: List[FCommonPile] => PT,
    dataDecoder: List[Any] => DT
) extends FPileList {
  override type PileType = PT
  override type DataType = DT

  override def encodePiles(piles: PT): List[FCommonPile] = encoder(piles)
  override def decodePiles(piles: List[FCommonPile]): PileType = decoder(piles)
  override def decodePileData(datas: List[Any]): DT = dataDecoder(datas)
}

abstract trait FCommonPile extends FPileAbs1111 {
  type PathType
  override type DataType

  val pathPile: PathType
  val fShape: FsnShape[PathType, DataType]
}

trait FPile extends FCommonPile {
  val subs: FPileAbs1111
  def dataFromSub(subDatas: Any): DataType
}

class FPileImpl[PT, DT](
    override val pathPile: PT,
    override val fShape: FsnShape[PT, DT],
    override val subs: FPileAbs1111,
    dataFromSubFunc: Any => DT
) extends FPile {
  override type PathType = PT
  override type DataType = DT

  override def dataFromSub(subDatas: Any): DataType = dataFromSubFunc(subDatas)

}

trait FLeafPile extends FCommonPile

class FLeafPileImpl[PT, DT](
    override val pathPile: PT,
    override val fShape: FsnShape[PT, DT]
) extends FLeafPile {
  override type PathType = PT
  override type DataType = DT
}

object FPile {

  def genTreeTailCall[U](pathGen: FAtomicPath => FQueryTranform[U], oldPile: FPileAbs1111, newPile: FPileAbs1111): Either[FAtomicException, (FPileAbs1111, List[FPileAbs1111])] = {
    oldPile -> newPile match {
      case (commonPile: FCommonPile, leafPile: FLeafPile) =>
        val transforms = leafPile.fShape.encodeColumn(leafPile.pathPile).map(pathGen)
        if (transforms.forall(_.gen.isRight)) {
          Right(newPile, List(commonPile))
        } else {
          Left(FAtomicException(transforms.map(_.gen).collect { case Left(FAtomicException(s)) => s }.flatten))
        }

      case (oldPile: FPile, newPile: FPile) =>
        genTreeTailCall(pathGen, oldPile.subs, newPile.subs) match {
          case Left(_) =>
            genTreeTailCall(pathGen, oldPile, new FLeafPileImpl(
              newPile.pathPile, newPile.fShape
            ))
          case Right((newSubResultPile, pileList)) =>
            Right((new FPileImpl(
              newPile.pathPile,
              newPile.fShape,
              newSubResultPile,
              newPile.dataFromSub _
            ), pileList))
        }

      case (oldPile: FPileList, newPile: FPileList) =>
        val newPiles = newPile.encodePiles(newPile.pileEntity)
        val oldPiles = oldPile.encodePiles(oldPile.pileEntity)
        val listResult = oldPiles.zip(newPiles).map {
          case (oldP, newP) =>
            genTreeTailCall(pathGen, oldP, newP)
        }
        val isSuccess = listResult.forall(_.isRight)
        if (isSuccess) {
          val (newPiles, newPileList) = listResult.map(s => s.right.get).unzip
          Right(new FPileListImpl(
            newPile.decodePiles(newPiles.map(_.asInstanceOf[FCommonPile])),
            newPile.encodePiles _,
            newPile.decodePiles _,
            newPile.decodePileData _
          ), newPileList.flatten)
        } else {
          Left(listResult.collect { case Left(ex) => ex }.reduce((a1, a2) =>
            FAtomicException(a1.typeTags ::: a2.typeTags)))
        }
    }
  }

  def genTree[U](pathGen: FAtomicPath => FQueryTranform[U], pile: FPileAbs1111): Either[FAtomicException, (FPileAbs1111, List[FPileAbs1111])] = {
    genTreeTailCall(pathGen, pile, pile).right.map { case (newPile, piles) => newPile -> piles }
  }

  def transformTreeList[U, T](pathGen: FAtomicPath => FQueryTranform[U])(columnGen: List[U] => T): FPileSyntax.PileGen[T] = {
    prePiles: List[FPileAbs1111] =>
      //防止定义 FPile 时最后一步使用了混合后不能识别最后一层 path
      val piles = prePiles //.flatMap(eachPile => eachPile.genPiles)

      val calculatePiles = piles.map { s =>
        genTree(pathGen, s)
      }.foldLeft(Right(Nil): Either[FAtomicException, List[(FPileAbs1111, List[FPileAbs1111])]]) {
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
          columnGen(ListUtils.splitList(anyList, summaryPiles.map(_.map(_.dataLengthSum).sum): _*)
            .zip(summaryPiles)
            .flatMap {
              case (subList, subPiles) =>
                ListUtils.splitList(subList, subPiles.map(_.dataLengthSum): _*).zip(subPiles).flatMap {
                  case (eachList, eachPiles) =>
                    eachPiles.selfPaths.map(s => pathGen(s)).zip(eachPiles.dataListFromSubList(eachList)).map {
                      case (tranform, data) =>
                        tranform.apply(tranform.gen.right.get, data.asInstanceOf[FAtomicValueImpl[tranform.path.DataType]])
                    }
                }
              //???
            })
          //???
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

  /*def genTreeTailCallWithoutData[U](pathGen: FAtomicPath => FQueryTranformWithOutData[U], oldPile: FPile, newPile: FPile): Either[FAtomicException, (FPile, FPile, List[FPile])] = {
    /*if (newPile.subs.isEmpty) {
      val transforms = newPile.paths.map(pathGen)
      if (transforms.forall(_.gen.isRight)) {
        Right(oldPile, newPile, List(oldPile))
      } else {
        Left(FAtomicException(transforms.map(_.gen).collect { case Left(FAtomicException(s)) => s }.flatten))
      }
    } else {*/
    /*if (newPile.subs.isEmpty) {
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
    }*/
    ???
  }

  def genTreeWithoutData[U](pathGen: FAtomicPath => FQueryTranformWithOutData[U], pile: FPile): Either[FAtomicException, (FPile, List[FPile])] = {
    genTreeTailCallWithoutData(pathGen, pile, pile).right.map { case (oldPile, newPile, piles) => newPile -> piles }
  }

  def transformTreeListWithoutData[U, T](pathGen: FAtomicPath => FQueryTranformWithOutData[U])(columnGen: List[U] => T): FPileSyntaxWithoutData.PileGen[T] = {
    prePiles: List[FPile] =>
      //防止定义 FPile 时最后一步使用了混合后不能识别最后一层 path
      /*val piles = prePiles.flatMap(eachPile => eachPile.genPiles)
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
      }*/
      ???
  }*/

  /*def transformOf[U, T](pathGen: FAtomicPath => FQueryTranform[U])(columnGen: List[U] => T): List[FAtomicPath] => Either[FAtomicException, List[FAtomicValue] => T] = {
     (initPaths: List[FAtomicPath]) =>
       ???
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
   }*/
}