package net.scalax.fsn.json.operation

import net.scalax.fsn.common.atomic.{ DefaultValue, FDescribe, FProperty }
import net.scalax.fsn.core.FAtomicPathImpl

trait FAtomicHelper[D] {

  val path: FAtomicPathImpl[D]

  /*def appendAll(atomics: List[FAtomic[D]]): List[FAtomic[D]] = this.atomics ::: atomics
  def append(atomic: FAtomic[D]): List[FAtomic[D]] = atomic :: this.atomics*/

}

trait FPropertyAtomicHelper[D] extends FAtomicHelper[D] {

  def named(name: String) = {
    path.appendAtomic(FProperty.apply(name))
  }

  def describe(describe1: String) = {
    path.appendAtomic(FDescribe.apply(describe1))
  }

}

trait FDefaultAtomicHelper[D] extends FAtomicHelper[D] {

  def defaultValue(default: D) = {
    path.appendAtomic(DefaultValue.apply(default))
  }

}