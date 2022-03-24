/**
 * Copyright 2018-2022, Azul Systems
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.azul.fuzzgen.fuzzer

import FuzzNode.FREE_ID

import scala.collection.mutable.ArrayBuffer

/**
  * Elementary node of fuzzing.
  */
abstract case class FuzzNode(){
  val id: Int = {
    FREE_ID += 1
    FREE_ID
  }

  def dump(): String
}

final class TerminalFuzzNode(val token: String) extends FuzzNode {
  override def toString: String = token

  override def dump(): String = {
    id.toString
  }
}

final class NonTerminalFuzzNode() extends FuzzNode {
  val children = new ArrayBuffer[FuzzNode]()
  override def toString: String = children.mkString("")

  def addChild(child: FuzzNode): Unit = {
    children += child
  }

  override def dump(): String = {
    id.toString + "(" + children.map(_.dump()).mkString(" ") + ")"
  }
}

object FuzzNode {
  var FREE_ID = 0
}
