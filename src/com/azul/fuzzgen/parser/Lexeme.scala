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

import scala.collection.mutable.ArrayBuffer
import com.azul.fuzzgen.grammar.Grammar
import com.azul.fuzzgen.parser.LexemeType.LexemeType

/**
 * Represents a single unit of text parsed by lexer.
 */
class Lexeme(val token: String, val lexemeType: LexemeType) {
  def this(char: Char, lexemeType: LexemeType) = this(char.toString, lexemeType)

  override def toString: String = token + "[" + lexemeType + "]"

  protected def error(message: String): Nothing = {
    throw new Exception(message)
  }
}

final class NonTerminalLexeme(token: String, lexemeType: LexemeType, val stage: Option[Int] = None)
  extends Lexeme(token: String, lexemeType: LexemeType) {

}

/**
 * This lexeme represents an integer number from some range.
 */
final class IntExprFromRangeLexeme(token: String, minExpr: Expression, maxExpr: Expression, lexemeType: LexemeType)
  extends Lexeme(token: String, lexemeType: LexemeType) {

  def min(grammar: Grammar): Int = {
    minExpr.eval(grammar)
  }

  def max(grammar: Grammar): Int = {
    maxExpr.eval(grammar)
  }

  override def toString: String = "@[" + minExpr + ":" + maxExpr + "][" + lexemeType + "]"
}

/**
 * This lexeme represents an double number from some range.
 */
final class DoubleFromRangeLexeme(token: String, lexemeType: LexemeType)
  extends Lexeme(token: String, lexemeType: LexemeType) {

  private val splitToken = token.replace("@[", "").replace("]", "").split(":")

  assert(splitToken.length == 2, "Wrong format!")

  val min: Double = splitToken(0).toDouble

  val max: Double = splitToken(1).toDouble

  if (min >= max) {
    error("Invalid bounds of Double range: min " + min + " >= " + max)
  }

  override def toString: String = "@[" + min + ":" + max + "][" + lexemeType + "]"
}

/**
 * This lexeme represents a long number from some range - temporary solution
 */
final class LongFromRangeLexeme(token: String, lexemeType: LexemeType)
  extends Lexeme(token: String, lexemeType: LexemeType) {

  private val splitToken = token.replace("@[", "").replace("]", "").split(":")

  assert(splitToken.length == 2, "Wrong format!")

  val min: Long = splitToken(0).toLong

  val max: Long = splitToken(1).toLong

  if (min >= max) {
    error("Invalid bounds of Long/Int range: min " + min + " >= " + max)
  }

  override def toString: String = "@[" + min + ":" + max + "][" + lexemeType + "]"
}

class IDLexeme(token: String, lexemeType: LexemeType)
  extends Lexeme(token: String, lexemeType: LexemeType) {
  override def toString: String = token + "[" + lexemeType + "]"
}


class LendScopeWithIDLexeme(token: String, lexemeType: LexemeType, IDtoken: String)
  extends Lexeme(token: String, lexemeType: LexemeType) {
  val ID: String = IDtoken

  override def toString: String = token + "." + IDtoken + "[" + lexemeType + "]"
}

class LendScopeWithIDByPrefixLexeme(lexemeType: LexemeType, scopePrefix_ : String, IDtoken_ : String, scopeContains_ : String)
  extends Lexeme(scopePrefix_ : String, lexemeType: LexemeType) {
  val scopePrefix = scopePrefix_
  val IDtoken = IDtoken_
  val scopeContains = scopeContains_
  override def toString = scopePrefix_ + IDtoken + " with " + scopeContains + "[" + lexemeType + "]"
}

class CreateIDFromLastIDLexeme(lexemeType: LexemeType, lastIDAsPrefix_ : String, suffix_ : String)
  extends Lexeme(lastIDAsPrefix_ : String, lexemeType: LexemeType) {
  val lastIDAsPrefix = lastIDAsPrefix_
  val suffix = suffix_
  override def toString = lastIDAsPrefix + suffix + "[" + lexemeType + "]"

}

final class IDLexemeWithDepth(token: String, d: Int, lexemeType: LexemeType)
  extends IDLexeme(token: String, lexemeType: LexemeType) {
  val depth: Int = d

  override def toString: String = token + "[" + lexemeType + ", " + depth + "]"
}

class GetLocalIDsLexeme(token: String, lexemeType: LexemeType, fail: Boolean)
  extends Lexeme(token, lexemeType) {
  val failIfEmpty: Boolean = fail
}

final class GetLocalIDsLexemeSeparator(token: String, lexemeType: LexemeType, fail: Boolean, sep: String)
  extends GetLocalIDsLexeme(token, lexemeType, fail) {
  val separator: String = sep
}

final class GetAllIDsLexeme(token: String, lexemeType: LexemeType)
  extends Lexeme(token, lexemeType) {
}

final class GetLastIDLexeme(token: String, lexemeType: LexemeType)
  extends Lexeme(token, lexemeType) {
}

final class BeginRuleLexeme(token: String, lexemeType: LexemeType, val weightExpression: Expression)
  extends Lexeme(token: String, lexemeType: LexemeType) {
  def weight(grammar: Grammar): Int = {
    val w = weightExpression.eval(grammar)
    if (w < 0)
      error(s"Expected non-negative rule weight, found $w at " + this)
    w
  }

  override def toString: String = token + "[" + lexemeType + s"][weight = $weightExpression]"
}

final class SetEnvVarLexeme(token: String, val value: Expression)
  extends Lexeme(token, LexemeType.SetEnvVar) {
  override def toString: String = token + "=" + value + "[" + lexemeType + "]"
}

final class ExpressionLexeme(expression: Expression)
  extends Lexeme(expression.toString, LexemeType.Expression) {
}

final class IncludeLexeme(token: String, lexemeType: LexemeType, val filePath: String, val params: ArrayBuffer[Array[String]])
  extends Lexeme(token: String, lexemeType: LexemeType) {
}

final class CommentLexeme(token: String, lexemeType: LexemeType)
  extends Lexeme(token, lexemeType) {
}
