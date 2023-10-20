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

import com.azul.fuzzgen.FuzzGen
import com.azul.fuzzgen.fuzzer.{FuzzException, Fuzzer}
import com.azul.fuzzgen.grammar.Grammar
import com.azul.fuzzgen.parser.LexemeType.LendScopeWithID

import scala.collection.mutable.ArrayBuffer

/**
 * Represents expressions including int constants and/or variables.
 */
abstract sealed class Expression {
  def eval(grammar: Grammar): Int
}

final case class ConstantExpression(constant: Int) extends Expression {
  override def toString: String = constant.toString
  override def eval(grammar: Grammar): Int = {constant}
}

final case class EnvVarExpression(variable: String) extends Expression {
  override def toString: String = variable
  override def eval(grammar: Grammar): Int = grammar.getEnvVar(variable)
}

case class IDsAvailableExpression(id_ : String) extends Expression {
  val id: String = id_
  //protected var value = count
  override def eval(grammar: Grammar): Int = {
    //grammar.fuzzer.checkIDsCount(this, grammar.fuzzer)
//    println("--------------------------------------")
//    println("checkIDsCount: " + id + " -> " + grammar.fuzzer.checkIDsCount(this, grammar.fuzzer))
//    println("checkIDs: " + id + " -> " + grammar.fuzzer.checkIDs(this, grammar.fuzzer))
//    println("checkIDs1: " + id + " -> " + grammar.fuzzer.checkIDs1(id_, grammar.fuzzer))
//    println("--------------------------------------")


    //val c1 = grammar.fuzzer.checkIDsCount(this, grammar.fuzzer)
    //val c1_1 = grammar.fuzzer.checkIDsCountLight(this, grammar.fuzzer)

    //    val c2 = grammar.fuzzer.checkIDs(this, grammar.fuzzer)
    val c3 = grammar.fuzzer.checkIDs1(id, grammar.fuzzer)


//    if (c1==0 )  {
//      if ((c2 != 0) || (c3!=0))
//      {
//
//        println("--------------------------------------")
//        println("checkIDsCount: " + id + " -> " + c1)
//        println("checkIDs: " + id + " -> " + c2)
//        println("checkIDs1: " + id + " -> " + c3)
//        println("--------------------------------------")
//      }
//    } else if (!(c1/c1 == c2) || !(c1/c1 == c3) || (c2!=c3)) {
//      println("--------------------------------------")
//
//      println("checkIDsCount: " + id + " -> " + c1)
//      println("checkIDs: " + id + " -> " + c2)
//      println("checkIDs1: " + id + " -> " + c3)
//      println("--------------------------------------")
//    }
    c3
    //c2 //-- correct
    //c1 - wrong
  }

}

case class ScopeWithIDsAvailableExpression(scopeID_ : String, id_ : String) extends Expression {
val scopeID: String = scopeID_
  val id: String = id_
  override def eval(grammar: Grammar): Int = {
    //val c = grammar.fuzzer.checkScopeWithID( new LendScopeWithIDLexeme(scopeID, LendScopeWithID, id), false, grammar.fuzzer)
    val c = grammar.fuzzer.checkScopeWithIDLight( new LendScopeWithIDLexeme(scopeID, LendScopeWithID, id), false, grammar.fuzzer)
    //val c = f.checkScopeWithID( new LendScopeWithIDLexeme(scopeID, LendScopeWithID, id), false, f)
    return c;
  }
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
  override def operation(op1: Int, op2: Int) : Int = {
    if (op1 < 0 && op2 < 0) {
      FuzzGen.warning("Multiplying negative expressions is usually done by mistake and can lead to hidden errors: in expression " + this.toString + " found negative multipliers " + op1 + " and " + op2)
    }
    return op1 * op2
  }

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
