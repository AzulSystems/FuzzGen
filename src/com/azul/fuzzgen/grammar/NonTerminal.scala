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

package com.azul.fuzzgen.grammar

import scala.collection.mutable.ArrayBuffer

/**
  * Represents a non-terminal that can be lowered into terminals with a number of ways according to rules.
  */
class NonTerminal(val id: String, val stage: Option[Int]) {
  val rules = new ArrayBuffer[Rule]() // can't be private because we can now append rules
  var appendable: Boolean = false

  def addRule(rule: Rule): Unit = {
    rules += rule
  }

  def getValidRules(grammar: Grammar): Vector[Rule] = {
    rules.filter(_.weight(grammar) > 0).toVector
  }

  def getAllRules: Vector[Rule] = {
    rules.toVector
  }

  override def toString: String = {
    id + "\n\t\t" + rules.mkString("\n\t\t")
  }

  def isEqual(otherNonTerminal : NonTerminal): Boolean = this.toString() == otherNonTerminal.toString()
}
