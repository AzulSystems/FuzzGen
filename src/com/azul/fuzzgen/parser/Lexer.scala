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

import com.azul.fuzzgen.parser.LexemeType._
import scala.collection.mutable.ArrayBuffer

/**
 * Splits text into a sequence of lexemes.
 */
class Lexer() {
  private var currentLexemeIndex = 0
  private var currentChar = 0
  private var chars: Array[Char] = _
  private val lexemes = new ArrayBuffer[Lexeme]()
  private val verbose = false
  private var grammarFilename = ""

  private def log(message: String): Unit = {
    if (verbose)
      println(message)
  }

  private def init(text: String, grammarFilename1: String): Unit = {
    currentChar = 0
    chars = text.toCharArray
    lexemes.clear()
    grammarFilename = grammarFilename1
  }

  private def hasCurrChar = currentChar < chars.length

  private def skip(amount: Int = 1): Unit = {
    currentChar += amount
  }

  private def nextChar: Char = {
    val c = currChar
    skip()
    c
  }

  private def currChar: Char = chars(currentChar)

  private def skipWhiteSpaces(): Unit = {
    while (hasCurrChar && Character.isWhitespace(currChar)) nextChar
  }

  private def isExtendedLetter(char: Char): Boolean = {
    char == '_' || Character.isLetter(char)
  }

  private def isExtendedLetterOrDigit(char: Char): Boolean = {
    (char ==  '_' ) || Character.isLetterOrDigit(char)
  }

  private def isOkForID(char: Char): Boolean = {
    isExtendedLetterOrDigit(char) || (char == '&' ) || (char == '%' )
  }

  private def isExtendedDigit(char: Char): Boolean = {
    char == '-' || Character.isDigit(char)
  }

  private def lookup(pattern: String): Boolean = {
    if (currentChar + pattern.length > chars.length)
      return false
    for (i <- Range(0, pattern.length))
      if (chars(currentChar + i) != pattern(i))
        return false
    skip(pattern.length)
    true
  }

  private def parseID: String = {
    skipWhiteSpaces()
    if (!hasCurrChar || !(isOkForID(currChar) )) {
      //if (!hasCurrChar || !(isExtendedLetter(currChar) || currChar == "+" || currChar == "*")) {
        error("ID should start from a letter, not " + currChar)

    }
    val sb = new StringBuilder
    //while (hasCurrChar && (isExtendedLetterOrDigit(currChar)|| currChar == "+" || currChar == "*")) {
      while (hasCurrChar && (isOkForID(currChar))) {
        sb.append(nextChar)
    }
    sb.toString
  }

  private def parseFilepath: String = {
    skipWhiteSpaces()
    val sb = new StringBuilder

    if (nextChar != '\"')
      error("Filepath for #INCLUDE should start with a symbol \".")

    while (hasCurrChar && currChar != '\"')
      sb.append(nextChar)

    if (nextChar != '\"')
      error("Filepath for #INCLUDE should end with a symbol \".")

    if (sb.isEmpty)
      error("Filepath for #INCLUDE should be not empty.")

    sb.toString()
  }

  private def parseParams: ArrayBuffer[Array[String]] = {
    val sb = new StringBuilder
    while (currChar != '\n') {
      sb.append(nextChar)
    }

    val paramsArray: ArrayBuffer[Array[String]] = new ArrayBuffer[Array[String]]
    val paramsString = sb.toString.trim().replaceAll(" +", " ")
    if (paramsString.isEmpty) return paramsArray

    if (paramsString.contains('(')) {
      if (!paramsString.matches("\\(.+\\)+")) {
        error("Parameters string \"" + paramsString + "\" contains \")\" but doesn't match (.+)+ pattern as expected")
      }
    }

    val paramsGroups = paramsString.split("\\(").filter(_.trim.nonEmpty)
    for (pg <- paramsGroups) {
      val parString = pg.reverse.replaceFirst("\\)", "").reverse.split(" ").filter(_.trim.nonEmpty)
      val parStringTrimmed = parString.map(s => s.trim())
      paramsArray.addOne(parStringTrimmed)
    }
    paramsArray
  }

  private def parseNumber1: String = {
    val resultNumber = if (currChar != '.') parseInteger else ""
    if (currChar == '.') {
      nextChar
      return resultNumber + "." + parseInteger
    }
    resultNumber
  }


  private def parseIntegerOrFail: String = {
    skipWhiteSpaces()
    val sb = new StringBuilder

    if (!hasCurrChar || !isExtendedDigit(currChar)) {
      if (hasCurrChar)
        error("Met currChar instead of Int: \"" + currChar + "\"")
      error("Expected - or digit!")
    }
    sb.append(nextChar)
    while (hasCurrChar && Character.isDigit(currChar))
      sb.append(nextChar)
    sb.toString
  }

  private def parseInteger: String = {
    val sb = new StringBuilder
    if (!hasCurrChar || !isExtendedDigit(currChar)) {
      return ""
    }
    sb.append(nextChar)
    while (hasCurrChar && Character.isDigit(currChar))
      sb.append(nextChar)
    sb.toString
  }

  private def parseExpressionInParentheses: Expression = {
    nextChar
    val inParentheses = parseExpression
    if (nextChar != ')') {
      error("Parenthesis open but not closed!")
    }
    inParentheses
  }

  private def parseExpression3: Expression = {
    if (currChar == '(')
      return parseExpressionInParentheses

    if (isExtendedDigit(currChar)) {
      val s = parseIntegerOrFail
      return ConstantExpression(s.toInt)
    }

    // TODO: else?
    EnvVarExpression(parseID)
  }

  // TODO: rewrite Division. The division priority equal to the multiplication priority.
  private def parseExpression2_5: Expression = {
    val op1 = parseExpression3
    if (!hasCurrChar || currChar != '/')
      return op1

    val divNode = new DivExpression
    divNode.addOperand(op1)
    while (hasCurrChar && currChar == '/') {
      nextChar
      divNode.addOperand(parseExpression3)
    }
    divNode
  }

  private def parseExpression2: Expression = {
    val op1 = parseExpression2_5
    if (!hasCurrChar || currChar != '*')
      return op1

    val mulNode = new MulExpression
    mulNode.addOperand(op1)
    while (hasCurrChar && currChar == '*') {
      nextChar
      mulNode.addOperand(parseExpression2_5)
    }
    mulNode
  }

  private def parseExpression1: Expression = {
    val op1 = parseExpression2
    if (!hasCurrChar || (currChar != '+' && currChar != '-'))
      return op1

    val addNode = new AddExpression
    addNode.addOperand(op1)
    while (hasCurrChar && (currChar == '+' || currChar == '-')) {
      val c = nextChar
      if (c == '+')
        addNode.addOperand(parseExpression2)
      else if (c == '-') {
        val mulNode = new MulExpression
        mulNode.addOperand(ConstantExpression.apply(-1))
        mulNode.addOperand(parseExpression2)
        addNode.addOperand(mulNode)
      }
    }
    addNode
  }

  private def parseExpression: Expression = {
    parseExpression1
  }

  private def parseSeparator: String = {
    skipWhiteSpaces()
    val sb = new StringBuilder

    if (nextChar != '\"')
      error("Separator string should start with a symbol \".")

    while (hasCurrChar && currChar != '\"')
      sb.append(nextChar)

    if (nextChar != '\"')
      error("Separator string should end with a symbol \".")

    if (sb.isEmpty)
      error("Separator string should not be empty.")

    sb.toString()
  }

  private def parseLexeme: Lexeme = {
    // First, skip whitespaces.
    skipWhiteSpaces()
    if (!hasCurrChar)
      return new Lexeme("", LexemeType.EOF)

    if (lookup("\\n")) return new Lexeme("\n", Literal)
    if (lookup("\\t")) return new Lexeme("\t", Literal)
    if (lookup("\\s")) return new Lexeme(" ", Literal)

    // Then, try to parse range or non-terminal.
    if (currChar == '@') {
      val sb = new StringBuilder().append(nextChar)
      if (hasCurrChar && currChar == '[') {
        // TODO: Factor out parsing of integral ranges.
        sb.append(nextChar)

        //if there are only doubles or int/long, treat them as numbers of double and long types
        val x = currentChar // keeping currentChar to move back if we need to parse range values again
        val tryParseNumberMin = parseNumber1
        var tryParseNumberMax = ""
        if (tryParseNumberMin.nonEmpty && hasCurrChar && currChar == ':') {
          nextChar
          tryParseNumberMax = parseNumber1
          if (tryParseNumberMax.nonEmpty && hasCurrChar && currChar == ']') {
            sb.append(tryParseNumberMin + ':' + tryParseNumberMax + ']')
            nextChar
            if (sb.contains('.')) {
              return new DoubleFromRangeLexeme(sb.toString(), DoubleFromRange)
            } else {
              return new LongFromRangeLexeme(sb.toString(), LongFromRange)
            }
          }
        }

        // at least one of the values is not a number but expression
        if (tryParseNumberMax.isEmpty || !sb.contains('.')) {
          currentChar = x // getting back to first char of range
          val minExpr = parseExpression
          sb.append(minExpr)
          if (!hasCurrChar || currChar != ':')
            error("Expected : as separator between numbers in range! Found after " + minExpr)
          sb.append(nextChar)

          val maxExpr = parseExpression
          sb.append(maxExpr)
          //}
          if (!hasCurrChar || currChar != ']')
            error("Expected ] as end of the range! Found after " + maxExpr)
          sb.append(nextChar)
          return new IntExprFromRangeLexeme(sb.toString(), minExpr, maxExpr, IntExprFromRange)
        }

      } else {
        // Non-terminal.
        while (hasCurrChar && isExtendedLetterOrDigit(currChar))
          sb.append(nextChar)
        var generationStage: Option[Int] = None
        if (lookup("[")) {
          if (!hasCurrChar || !isExtendedDigit(currChar))
            error("Stage is expected to be a number!")
          // Parse stage.
          val stage = parseInteger.toInt
          if (stage < 0)
            error("Stage is expected to be non-negative!")
          if (!lookup("]"))
            error("Expected ] after stage number!")
          generationStage = Some(stage)
        }
        return new NonTerminalLexeme(sb.toString(), NonTerminal, generationStage)
      }
    }

    if (lookup(Lexer.BEGIN_SCOPE))
      return new Lexeme(Lexer.BEGIN_SCOPE, BeginScope)

    if (lookup(Lexer.END_SCOPE))
      return new Lexeme(Lexer.END_SCOPE, EndScope)

    if (lookup(Lexer.LEND_SCOPE_WITH_ID_BY_PREFIX)) {
      skipWhiteSpaces()
      if (!lookup("(")) {
        error("Error in Lexer.LEND_SCOPE_WITH_ID_BY_PREFIX: \"(\" expected" )
      }
      val lex = new LendScopeWithIDByPrefixLexeme(LendScopeWithIDByPrefix, parseID, parseID, parseID)
      skipWhiteSpaces()
      if (!lookup(")")) {
        error("Error in Lexer.LEND_SCOPE_WITH_ID_BY_PREFIX: \")\" expected" )
      }
      return lex
    }


    if (lookup(Lexer.LEND_SCOPE_WITH_ID))
      return new LendScopeWithIDLexeme(parseID, LendScopeWithID, parseID)

    if (lookup(Lexer.LEND_SCOPE))
      return new IDLexeme(parseID, LendScope)

    if (lookup(Lexer.RETURN_SCOPE))
      return new Lexeme(Lexer.RETURN_SCOPE, ReturnScope)


    if (lookup(Lexer.BEGIN_RULE))
      return new BeginRuleLexeme(
        Lexer.BEGIN_RULE, BeginRule, if (hasCurrChar && currChar == ':') {
          nextChar
          parseExpression
        } else ConstantExpression(1))

    if (lookup(Lexer.APPEND_RULE))
      return new BeginRuleLexeme(
        Lexer.BEGIN_RULE, AppendRule, if (hasCurrChar && currChar == ':') {
          nextChar
          parseExpression
        } else ConstantExpression(1))

    if (lookup(Lexer.END_RULE))
      return new Lexeme(Lexer.END_RULE, EndRule)


    if (lookup(Lexer.CREATE_ID_FROM_LAST_ID)) {
      skipWhiteSpaces()
      if (!lookup("(")) {
        error("Error in Lexer.CREATE_ID_FROM_LAST_ID: \"(\" expected" )
      }
      val lex =new CreateIDFromLastIDLexeme(CreateIDFromLastID, parseID, parseID)
      skipWhiteSpaces()
      if (!lookup(")")) {
        error("Error in Lexer.CREATE_ID_FROM_LAST_ID: \")\" expected" )
      }
      return lex

    }

    if (lookup(Lexer.CREATE_ID)) {
      return new IDLexeme(parseID, CreateID)
    }



    if (lookup(Lexer.CREATE_LAZY_ID))
      return new IDLexeme(parseID, CreateLazyID)

    if (lookup(Lexer.REGISTER_LAZY_IDS))
      return new IDLexeme(Lexer.REGISTER_LAZY_IDS, RegisterLazyIDs)

    if (lookup(Lexer.REUSE_ID_AND_LINK))
      return new IDLexeme(parseID, ReuseIDAndLink)

    if (lookup(Lexer.REUSE_ID)) {
      return new IDLexeme(parseID, ReuseID)
    }

    if (lookup(Lexer.REUSE_LOCAL_ID))
      return new IDLexeme(parseID, ReuseLocalID)

    if (lookup(Lexer.GET_LOCAL_IDS_OR_EMPTY_SEPARATOR)) {
      return new GetLocalIDsLexemeSeparator(parseID, GetLocalIDsOrEmptySeparator, false, parseSeparator)
    }

    if (lookup(Lexer.GET_LOCAL_IDS_OR_EMPTY)) {
      return new GetLocalIDsLexeme(parseID, GetLocalIDsOrEmpty, false)
    }

    if (lookup(Lexer.GET_LAST_ID)) {
      return new IDLexeme(parseID, GetLastID)
    }


    if (lookup(Lexer.GET_LOCAL_IDS)) {
      return new GetLocalIDsLexeme(parseID, GetLocalIDs, true)
    }

    if (lookup(Lexer.GET_ALL_IDS)) {
      return new GetAllIDsLexeme(parseID, GetAllIDs)
    }

    if (lookup(Lexer.GET_LAST_REUSED_LOCAL_ID)) {
      return new Lexeme(Lexer.GET_LAST_REUSED_LOCAL_ID, GetLastReusedLocalID)
    }

    if (lookup(Lexer.SET_ENV_VAR)) {
      skipWhiteSpaces()
      val id = parseID
      if (nextChar != '=')
        error("Expected = symbol!")
      return new SetEnvVarLexeme(id, parseExpression)
    }

    if (lookup(Lexer.INCLUDE)) {
      val file = parseFilepath
      val params = parseParams
      return new IncludeLexeme(Lexer.INCLUDE, Include, file, params)
    }
    val literalQuote = '`'
    val sb = new StringBuilder
    if (lookup(literalQuote.toString)) {
      //  nextChar
      while (hasCurrChar && currChar != literalQuote) {
        sb += nextChar
      }
      if (!hasCurrChar || nextChar != literalQuote) {
        error("Expected `!")
      }
      return new Lexeme(sb.toString(), Literal)
    }

    val sbSkip = new StringBuilder
    val commentPrefix = "//"
    if (lookup(commentPrefix)) {
      while (hasCurrChar && (currChar != '\n' && currChar != '\r')) {
        sbSkip += nextChar
      }
      return new Lexeme(sbSkip.toString(), Comment)
    }

    while (hasCurrChar && !Character.isWhitespace(currChar))
      sb += nextChar

    //TODO:
    error("Expected ` before string: \"" + sb + "\"")
  }

  def parse(text: String, grammarFilename1: String): Unit = {
    if (text.isEmpty)
      error("Empty text!")

    init(text, grammarFilename1)

    while (hasCurrChar) {
      val lexeme = parseLexeme
      log("Parsed lexeme: " + lexeme)
      if (lexeme.lexemeType != LexemeType.Comment) {
        lexemes += lexeme
      } else {
        println("INFO: skipping comment " + lexeme.token)
      }
    }
  }

  private def error(message: String) = {
    throw new Exception(message + " in grammar file " + grammarFilename)
  }

  def hasMoreLexemes: Boolean = currentLexemeIndex < lexemes.length

  def currLexeme: Lexeme = lexemes(currentLexemeIndex)

  def nextLexeme: Lexeme = {
    val ret = currLexeme
    currentLexemeIndex += 1
    ret
  }
}

object Lexer {
  val BEGIN_SCOPE = "#BEGIN_SCOPE"
  val LEND_SCOPE = "#LEND_SCOPE"
  val LEND_SCOPE_WITH_ID = "#LEND_SCOPE_WITH_ID"
  val LEND_SCOPE_WITH_ID_BY_PREFIX = "#LEND_SCOPE_WITH_ID_BY_PREFIX"
  val END_SCOPE = "#END_SCOPE"
  val RETURN_SCOPE = "#RETURN_SCOPE"
  val BEGIN_RULE = "#BEGIN_RULE"
  val END_RULE = "#END_RULE"
  val APPEND_RULE = "#APPEND_RULE"
  val CREATE_ID = "#CREATE_ID"
  val CREATE_ID_FROM_LAST_ID = "#CREATE_ID_FROM_LAST_ID"
  val CREATE_LAZY_ID = "#CREATE_LAZY_ID"
  val REGISTER_LAZY_IDS = "#REGISTER_LAZY_IDS"
  val REUSE_ID = "#REUSE_ID"
  val REUSE_LOCAL_ID = "#REUSE_LOCAL_ID"
  val REUSE_ID_AND_LINK = "#REUSE_ID_AND_LINK"
  val GET_LOCAL_IDS_OR_EMPTY = "#GET_LOCAL_IDS_OR_EMPTY"
  val GET_LOCAL_IDS_OR_EMPTY_SEPARATOR = "#GET_LOCAL_IDS_OR_EMPTY_SEPARATOR"
  val GET_LOCAL_IDS = "#GET_LOCAL_IDS"
  val GET_ALL_IDS = "#GET_ALL_IDS"
  val GET_LAST_REUSED_LOCAL_ID = "#GET_LAST_REUSED_LOCAL_ID"
  val GET_LAST_ID = "#GET_LAST_ID"
  val SET_ENV_VAR = "#SET"
  val INCLUDE = "#INCLUDE"
  val MAIN = "@MAIN"
}
