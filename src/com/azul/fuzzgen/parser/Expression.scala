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

package com.azul.fuzzgen.parser

import com.azul.fuzzgen.grammar.Grammar

import scala.collection.mutable.ArrayBuffer

/**
 * Represents expressions including int constants and/or variables.
 */
abstract sealed class Expression {
  def eval(grammar: Grammar): Int
}

final case class ConstantExpression(constant: Int) extends Expression {
  override def toString: String = constant.toString
  override def eval(grammar: Grammar): Int = constant
}

final case class EnvVarExpression(variable: String) extends Expression {
  override def toString: String = variable
  override def eval(grammar: Grammar): Int = grammar.getEnvVar(variable)
}

// TODO: need to convert NAry to binary
abstract class NAryOperationExpression extends Expression {
  protected val operands = new ArrayBuffer[Expression]()
  final def addOperand(op: Expression): Unit = operands += op

  final override def eval(grammar: Grammar): Int = {
    if (operands.size == 1)
      return operands(0).eval(grammar)
    var result = operation(operands(0).eval(grammar), operands(1).eval(grammar))
    for (i <- Range(2, operands.size))
      result = operation(result, operands(i).eval(grammar))
    result
  }
  def operation(op1: Int, op2: Int): Int
}

final class AddExpression extends NAryOperationExpression {
  override def operation(op1: Int, op2: Int) : Int = op1 + op2
  override def toString: String = operands.mkString("+")
}

final class MulExpression extends NAryOperationExpression {
  override def operation(op1: Int, op2: Int) : Int = op1 * op2

  def putParenthesesIfNeeded(expression: Expression): String = {
    if (expression.isInstanceOf[AddExpression])
      return "(" + expression + ")"
    expression.toString
  }
  override def toString: String = operands.map(putParenthesesIfNeeded).mkString("*")
}

final class DivExpression extends NAryOperationExpression {
  override def operation(op1: Int, op2: Int) : Int = op1 / op2

  def putParenthesesIfNeeded(expression: Expression): String = {
    if (expression.isInstanceOf[AddExpression])
      return "(" + expression + ")"
    expression.toString
  }
  override def toString: String = operands.map(putParenthesesIfNeeded).mkString("/")
}