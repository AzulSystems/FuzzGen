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
import com.azul.fuzzgen.FuzzGen.warning
import com.azul.fuzzgen.grammar.{Grammar, NonTerminal, Rule}
import com.azul.fuzzgen.parser.LexemeType._

import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}

/**
 * RulesParser is responsible for parsing description of the language into structures that can be used to fuzz texts
 * according to these rules.
 */
class RulesParser(filename: String, params: String) {

  private val lexer = new Lexer()
  private var grammar = new Grammar(filename)

  private val paramsRP = params
  private val filenameRP = filename

  def parseRule(nonTerminal: NonTerminal, expression: Expression): Unit = {
    val rule = new Rule(expression)
    while (lexer.hasMoreLexemes && lexer.currLexeme.lexemeType != EndRule)
      rule.addLexeme(lexer.nextLexeme)
    nonTerminal.addRule(rule)
  }


  private def parseExpectedID(weight: Expression): Expression = {
    // println("parseExpectedID")
    var weight_ = weight
    var lex = if (lexer.hasMoreLexemes) lexer.nextLexeme else lexer.currLexeme
    var functionExpressionFound = false

    //if ( lex.lexemeType == FunctionExpression) {
    while (lexer.hasMoreLexemes && (lex.lexemeType == ExpectID || lex.lexemeType == ExpectScopeWithID)) {
      // println("DEBUG: " + lex.asInstanceOf[FunctionExpressionLexeme].token)
      functionExpressionFound = true
      val tmpWeight = new MulExpression()
      tmpWeight.addOperand(weight)
      //tmpWeight.addOperand(ConstantExpression(lex.asInstanceOf[ExpectIDLexeme].count))
      tmpWeight.addOperand(IDsAvailableExpression(lex.asInstanceOf[ExpectIDLexeme].token))
      weight_ = tmpWeight
      //lex.asInstanceOf[FunctionExpressionLexeme].count
      lex = lexer.nextLexeme
      // }
    }
    //if (!functionExpressionFound)
    lexer.prevLexeme
    return weight_
  }

  private def parseNonTerminal(): Unit = {
    val nonTerminalLexeme = lexer.nextLexeme.asInstanceOf[NonTerminalLexeme]
    val nonTerminal = new NonTerminal(nonTerminalLexeme.token, nonTerminalLexeme.stage)
    val alreadyExists = grammar.nonTerminalExists(nonTerminal)

    var appendRuleFound: Boolean = false
    var beginRuleFound: Boolean = false
    var concatRuleFound: Boolean = false

    //Looking for BEGIN_RULE
    while (lexer.hasMoreLexemes && lexer.currLexeme.lexemeType == BeginRule) {
      beginRuleFound = true
      var weight = expect(BeginRule).asInstanceOf[BeginRuleLexeme].weightExpression
      weight = parseExpectedID(weight)
      //weight = parseExpectedID(weight, ExpectScopeWithID)
      //println("begin rule: weight" + weight.toString)
      parseRule(nonTerminal, weight)
      nonTerminal.appendable = false
      expect(EndRule)
    }

    // Looking for APPEND_RULE
    while (lexer.hasMoreLexemes && lexer.currLexeme.lexemeType == AppendRule) {
      appendRuleFound = true
      var weight = expect(AppendRule).asInstanceOf[BeginRuleLexeme].weightExpression
      weight = parseExpectedID(weight)
      parseRule(nonTerminal, weight)
      nonTerminal.appendable = true
      expect(EndRule)
    }

    // Looking for CONCAT_RULE
    while (lexer.hasMoreLexemes && lexer.currLexeme.lexemeType == ConcatRule) {
      concatRuleFound = true
      expect(ConcatRule)

      if (!alreadyExists) {
        nonTerminal.singleRuleOnly = true
        parseRule(nonTerminal, ConstantExpression(1))
      } else if (alreadyExists && !grammar.nonTerminals(nonTerminal.id).singleRuleOnly) {
        error("Trying to concat lexemes to non-concat rule: " + nonTerminal.id + ", " + "nonterminal singleRuleOnly == " + nonTerminal.singleRuleOnly)
      } else if (alreadyExists && grammar.nonTerminals(nonTerminal.id).singleRuleOnly) { // If nonterminal already exists and is concat rule, then append
        if (grammar.nonTerminals(nonTerminal.id).rules.length != 1) {
          error("Expecting concat nonterminal " + nonTerminal.id + " to have just one rule, but it has more: " +
            grammar.nonTerminals(nonTerminal.id).rules.length + ", see " +
            grammar.nonTerminals(nonTerminal.id).rules.mkString("; "))
        } else
        {

          while (lexer.hasMoreLexemes && lexer.currLexeme.lexemeType != EndRule) {
            var l = lexer.nextLexeme
            grammar.nonTerminals(nonTerminal.id).rules(0).addLexeme(l)
          }
        }

      }
      expect(EndRule)
    }


    if ((beginRuleFound && (appendRuleFound || concatRuleFound)) ||
      (appendRuleFound && (beginRuleFound || concatRuleFound)) ||
      (concatRuleFound && (beginRuleFound || appendRuleFound))) {
      error("Trying to append rules to non-appendable or concat (single rule) nonterminal " + nonTerminal)
    }

    // Not expecting to have either BEGIN_RULE or APPEND_RULE further
    if (appendRuleFound || beginRuleFound || concatRuleFound) {
      if (lexer.hasMoreLexemes && (lexer.currLexeme.lexemeType == AppendRule ||
        lexer.currLexeme.lexemeType == BeginRule ||
        lexer.currLexeme.lexemeType == ConcatRule)) {
        error("Both APPEND_RULE and BEGIN_RULE or CONCAT_RULE found for nonterminal " + nonTerminal)
      }
    }

    if (!appendRuleFound && !beginRuleFound && !concatRuleFound) { // no BEGIN_RULE or APPEND_RULE after NonTerminal - fwd declaration
      grammar.addFwDeclaration(nonTerminal) //it's OK to have duplicates?
      // Probably later we will need to understand in which grammar file this forward declaration was met
    } else {
      if (beginRuleFound && alreadyExists && !nonTerminal.isEqual(grammar.getNonTerminal(nonTerminal.id))) {
        error("Redefinition of nonTerminal " + nonTerminal)
      } else if (concatRuleFound && alreadyExists) {
        //do nothing
      } else if (beginRuleFound && alreadyExists) {
        if (grammar.nonTerminals(nonTerminal.id).appendable) {
          error("There is a non-appendable declaration of nonterminal " + nonTerminal + " in file  " + grammar.getFilename + " while an appendable nonterminal with the same name already exists")
        }
      } else if (beginRuleFound && !alreadyExists) {
        grammar.addNonTerminal(nonTerminal)
      } else if (concatRuleFound && !alreadyExists) {
        grammar.addNonTerminal(nonTerminal)
      } else if (appendRuleFound && alreadyExists) { // If nonterminal already exists and is appendable, then append found rules
        if (!grammar.nonTerminals(nonTerminal.id).appendable) {
          error("Trying to append rules to non-appendable nonterminal " + nonTerminal)
        } else {
          for (r <- nonTerminal.getAllRules) {
            val existingRules = grammar.nonTerminals(nonTerminal.id).rules
            var ruleEquals = false
            for (existingR <- existingRules) {
              if (existingR.toString.equals(r.toString)) {
                ruleEquals = true
              }
            }
            if (!ruleEquals) grammar.nonTerminals(nonTerminal.id).addRule(r)
          }

        }
      } else { // newly met appendable nonterminal
        grammar.addNonTerminal(nonTerminal)
      }
    }
  }

  def parseSetEnvVar(): Option[Int] = {
    val setVarLexeme = expect(LexemeType.SetEnvVar).asInstanceOf[SetEnvVarLexeme]
    println("DEBUG: setVarLexeme.token " + setVarLexeme.token + " = " + setVarLexeme.value.eval(grammar))
    if (grammar.containsEnvVar(setVarLexeme.token) && grammar.getEnvVar(setVarLexeme.token) != setVarLexeme.value.eval(grammar))
      warning("Might be a mistake: global scope environment variable " + setVarLexeme.token + " has already been set to " + grammar.getEnvVar(setVarLexeme.token) + ", now trying to set it to " + setVarLexeme.value.eval(grammar))
    grammar.setEnvVar(setVarLexeme.token, setVarLexeme.value.eval(grammar))
  }

  def getAndCheckIncludeLexemeTrace(startInclude: String): mutable.ListBuffer[String] = {
    var currentPath: String = startInclude
    var includedFrom: String = grammar.getParentIncludeLexeme(currentPath)
    val includeTrace = new ListBuffer[String]
    currentPath = includedFrom
    while (currentPath != null) {
      includedFrom = grammar.getParentIncludeLexeme(currentPath)
      if (includeTrace.contains(currentPath)) {
        var itr: String = ""
        for (s <- includeTrace) {
          itr += s + ", "
        }
        error("Cyclic dependency in files includes: " + currentPath + " is found in includes chain " + itr)
      }
      includeTrace += currentPath
      currentPath = includedFrom
    }
    includeTrace += RulesParser.INCLUDE_ROOT
  }

  def getAndCheckIncludeTrace(startInclude: String, startParams: String): mutable.ListBuffer[(String, String)] = {
    var currentPath: String = startInclude
    var currentParams: String = startParams
    var includedFrom: String = grammar.getParentIncludeFile(currentPath, currentParams)
    var includedParams: String = grammar.getParentIncludeParams(currentPath, currentParams)
    val includeTrace = new ListBuffer[(String, String)]
    currentPath = includedFrom
    currentParams = includedParams
    while (currentPath != null) {
      includedFrom = grammar.getParentIncludeFile(currentPath, currentParams)
      includedParams = grammar.getParentIncludeParams(currentPath, currentParams)

      if (includeTrace.contains((currentPath, currentParams))) {
        var itr: String = ""
        for (s <- includeTrace) {
          itr += s._1 + ", "
        }
        error("Cyclic dependency in files includes: " + currentPath + " is found in includes chain " + itr)
      }
      includeTrace.append((currentPath, currentParams))
      currentPath = includedFrom
      currentParams = includedParams
    }
    includeTrace.append((RulesParser.INCLUDE_ROOT, ""))
  }


  private def substituteParams(rulesText: String, params: String, filepath: String): String = {
    var rulesTextWithParams: String = rulesText
    var currParamIndex = 1
    if (params.nonEmpty) {
      for (param <- params.split(' ')) {
        val currParamTemplate = "$" + currParamIndex
        if (rulesTextWithParams.contains(currParamTemplate))
          rulesTextWithParams = rulesTextWithParams.replace(currParamTemplate, param.trim)
        else
          error(s"Grammar: $filepath doesn't contain parameter $currParamTemplate .")
        currParamIndex += 1
      }
    }

    if (rulesTextWithParams.contains("$" + currParamIndex)) {
      currParamIndex -= 1
      error(s"Grammar: $filepath needs to receive more parameters than $currParamIndex .")
    }
    rulesTextWithParams
  }

  def parseInclude(parentGrammarFilename: String, parentGrammarParams: String): Unit = {
    val includeLexeme = expect(LexemeType.Include).asInstanceOf[IncludeLexeme]

    for (a <- includeLexeme.params) {
      println("DEBUG: include file " + includeLexeme.filePath + " with param " + a.mkString("/"))

    }
    println("The grammar file with path: \"" + includeLexeme.filePath + "\" ." +
      " parentGrammarFilename " + parentGrammarFilename + ", filenameRP " + filenameRP)

    if (filenameRP.equals(includeLexeme.filePath)) {
      error("The grammar file with path: \"" + includeLexeme.filePath + "\" includes to itself." +
        " parentGrammarFilename " + parentGrammarFilename + ", filenameRP " + filenameRP)
    }

    // Need to build all possible combinations of params groups
    // (cartesian product of Arrays)
    val paramsArray = includeLexeme.params
    var paramsCombination = Array.fill[Int](paramsArray.length)(0)
    val paramsCombinationMax = Array.fill[Int](paramsArray.length)(0)
    var x: Int = 0
    while (x < paramsCombinationMax.length) {
      paramsCombinationMax(x) = paramsArray(x).length
      x += 1
    }
    val includeParamsStringArray = new ArrayBuffer[String]

    // building combinations of params as an array of indices of corresponding params groups (cartesian product of params groups):
    // paramsCombination=[0, 2, 1] means 0-th param of first (params group), 2nd param of second (params group), 1st param of
    // third (params group), e.g., for "(Float Double) (Int Short Byte) (Var Val) this combination gives
    // "Float Byte Val" params string.

    var paramsString = ""

    def nextCombination(values: Array[Int], maxLengths: Array[Int]): Array[Int] = {
      if (values.length != maxLengths.length) error("error while parsing include file params " + includeLexeme.filePath)
      if (values.length == 0) {
        return values
      }
      var i: Int = values.length - 1

      values(i) += 1
      while (values(i) == maxLengths(i) && i >= 0) {
        values(i) = 0
        i -= 1

        if (i >= 0) {
          values(i) += 1
        } else {
          return values
        }
      }

      values
    }

    do {
      paramsString = ""
      for (j <- paramsCombination.indices) {
        paramsString += paramsArray(j)(paramsCombination(j)) + " "
      }
      paramsCombination = nextCombination(paramsCombination, paramsCombinationMax)
      includeParamsStringArray.addOne(paramsString.trim)
    } while (paramsCombination.sum > 0)

    // actually calling method including files with params string paramsStr
    for (paramsStr <- includeParamsStringArray) {
      parseParamsString(paramsStr)
    }

    def parseParamsString(paramsString: String) {

      println("INFO: parseInclude: file " + includeLexeme.filePath + " with params \"" + paramsString + "\"")
      if (grammar.includesThisFileWithParams(includeLexeme.filePath, paramsString)) {
        println("INFO: skipping include because file " + includeLexeme.filePath + " with parameters " + paramsString +
          " has already been included to " + grammar.getParentIncludeFile(includeLexeme.filePath, paramsString) +
          " with params " + grammar.getParentIncludeParams(includeLexeme.filePath, paramsString))
      } else {
        println("INFO: including file " + includeLexeme.filePath + " with parameters " + paramsString +
          " because it hasn't been included before ")

        val reader = new RulesFileReader
        var rulesText = reader.readFrom(includeLexeme.filePath)
        rulesText = substituteParams(rulesText, paramsString, includeLexeme.filePath)

        grammar.addOneInclude(includeLexeme.filePath, paramsString, filenameRP, paramsRP)
        val includedGrammar = new RulesParser(includeLexeme.filePath, paramsString).parse(rulesText, grammar.getIncludes, filenameRP, paramsRP, grammar)

        val message2 = "INFO: verification of included file " + includeLexeme.filePath + " in grammar file " + grammar.getFilename
        grammar = includedGrammar
        val fwd: List[(String, NonTerminal)] = grammar.getFwDeclarations

        grammar.verify1(message2, checkEnvVar = false)
      }
    }
  }


  private def parseTopLevelEntry(f: String, p: String): Unit = {
    if (lexer.currLexeme.lexemeType == NonTerminal) {
      parseNonTerminal()
    } else if (lexer.currLexeme.lexemeType == Include) {
      parseInclude(f, p)
    } else if (lexer.currLexeme.lexemeType == SetEnvVar) {
      parseSetEnvVar()
    } else {
      error("Unexpected lexeme of type" + lexer.currLexeme.lexemeType + " in grammar file " + grammar.getFilename)
      // TODO: need more research on this error
    }
  }


  def parse(text: String, includes: List[((String, String), (String, String))], parentGrammarFilename: String, parentGramarParams: String, parentGrammar: Grammar): Grammar = {
    if (parentGrammar != null) {
      grammar = grammar + parentGrammar
    } else {
      var l: List[((String, String), (String, String))] = List()
      for (i <- includes) {
        l = l :+ i
      }
      grammar.addIncludes(l)
    }

    lexer.parse(text, grammar.getFilename)
    while (lexer.hasMoreLexemes && lexer.currLexeme.lexemeType != EOF) {
      parseTopLevelEntry(parentGrammarFilename, parentGramarParams)
    }
    expect(EOF)
    grammar
  }


  private def expect(expectedType: LexemeType): Lexeme = {
    if (!lexer.hasMoreLexemes)
      error("No more lexemes, expected " + expectedType + " in grammar file " + grammar.getFilename)
    val lex = lexer.nextLexeme
    if (lex.lexemeType != expectedType)
      error("Expected lexeme of type " + expectedType + ", found " + lex + " in grammar file " + grammar.getFilename)
    lex
  }

  private def error(message: String) = {
    throw new Exception(message)
  }
}

object RulesParser {
  val INCLUDE_ROOT = "FuzzGen.main"
}
