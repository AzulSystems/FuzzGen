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

import com.azul.fuzzgen.parser.LexemeType

import java.util
import scala.collection.mutable
import scala.util.Random

/**
 * Contains description of language being fuzzed.
 */
class Grammar(private val filename: String) {
  def this(other: Grammar) = {
    this(other.getFilename)
    this.nonTerminals ++= other.nonTerminals
    this.envVars ++= other.envVars
    this.includes ++= other.includes
    this.includeLexemes ++= other.includeLexemes
    this.fwDeclarations ++= other.fwDeclarations
  }

  val nonTerminals = new mutable.TreeMap[String, NonTerminal]() // can't be private because we can now append rules
  private val envVars = new mutable.TreeMap[String, Int]()
  private val includes = new mutable.TreeMap[(String, String), (String, String)]() // ((current file, params), (parent file, parent file params))
  private val includeLexemes = new mutable.TreeMap[String, (String, String)]() // (current, (parent, params))
  private val fwDeclarations = new mutable.TreeMap[String, NonTerminal]() // TODO: add filename?
  private val verbose = false

  private def log(message: String): Unit = {
    if (verbose)
      println(message)
  }

  def +(other: Grammar): Grammar = {
    val newGrammar = new Grammar(this)
    newGrammar.envVars ++= other.envVars

    for (nt <- other.nonTerminals) {
      if (this.nonTerminals.contains(nt._1)) { // upper level grammar already contains nonterminal with the same name
        val existingNT = this.nonTerminals(nt._1)
        if (existingNT != null && existingNT.asInstanceOf[NonTerminal].isEqual(nt._2)) {
          if (existingNT.appendable != nt._2.appendable) {
            error("Can't have appendable and non-appendable nonterminals with the same name " + nt._1)
          }
        } else if (existingNT != null && existingNT.appendable && nt._2.appendable) {
          for (r <- nt._2.rules) {
            // we don't add rules that are equal (as strings) to existing ones
            val existingRules = newGrammar.nonTerminals(nt._1).rules
            var ruleEquals = false
            for (existingR <- existingRules) {
              if (existingR.toString.equals(r.toString)) {
                ruleEquals = true
              }
            }
            if (!ruleEquals) newGrammar.nonTerminals(nt._1).addRule(r)
          }
        } else if (existingNT != null && (!existingNT.appendable || !nt._2.appendable)) {
          error("Non-Terminal named: " + nt._1 + " already exists and is not appendable. Choose a different name.")
        }
      } else {
        newGrammar.addNonTerminal(nt._2)
      }
    }

    newGrammar.includeLexemes ++= other.includeLexemes
    newGrammar.includes ++= other.includes
    newGrammar.fwDeclarations ++= other.fwDeclarations
    if (newGrammar.nonTerminals.size != this.nonTerminals.size + other.nonTerminals.size) {
      println("INFO: Number of non-terminals in a new grammar is not equal expected.")
    }
    if (newGrammar.envVars.size != this.envVars.size + other.envVars.size) {
      println("INFO: Number of environment variables in a new grammar is not equal to expected number.")
    }

    newGrammar
  }

  def nonTerminalExists(someNonTerminal: NonTerminal): Boolean = {
    if (nonTerminals.contains(someNonTerminal.id)) {
      log("nonTerminalExists: nonTerminal " + someNonTerminal.id + "exists")
      true
    } else {
      log("nonTerminalExists: nonTerminal " + someNonTerminal.id + "doesn't exist")
      false
    }
  }

  def getNonTerminal(id: String): NonTerminal = {
    if (!nonTerminals.contains(id)) {
      error("Non-terminal with id " + id + " is not found in grammar!")
    }
    nonTerminals(id)
  }

  def addNonTerminal(nonTerminal: NonTerminal): Unit = {
    if (nonTerminals.contains(nonTerminal.id)) {
      println("INFO: Non-terminal " + nonTerminal.id + " has already been described! " + nonTerminal)
    }
    nonTerminals.put(nonTerminal.id, nonTerminal)
  }


  def addFwDeclaration(nonTerminal: NonTerminal): Unit = {
    if (fwDeclarations.contains(nonTerminal.id)) {
      println("INFO: forward declaration " + nonTerminal.id + " has already been added")
    } else {
      fwDeclarations.put(nonTerminal.id, nonTerminal)
    }
  }

  def getFwDeclarations: List[(String, NonTerminal)] = {
    fwDeclarations.toList
  }

  def getStageFor(token: String): Option[Int] = {
    val nt: NonTerminal = nonTerminals.get(token).orElse(error(s"Non-terminal $token is not declared!")).get
    nt.stage
  }

  def getRuleFor(token: String, rnd: Random): Rule = {
    val nt: NonTerminal = nonTerminals.get(token).orElse(error(s"Non-terminal $token is not declared!")).get
    val rules = nt.getValidRules(this)
    if (rules.isEmpty)
      error(s"No valid rules found for $token")
    val ruleWeights = rules.map(_.weight(this))
    if (ruleWeights.exists(_ < 0))
      error("Rule with negative weight has been found!")
    val sumWeight = ruleWeights.sum

    assert(sumWeight > 0, "Sum weight of rules expected to be positive!")
    var index = 0
    var currentSum = ruleWeights(0)
    val threshold = rnd.nextInt(sumWeight)
    while (currentSum <= threshold) {
      index = index + 1
      currentSum += ruleWeights(index)
    }
    rules(index)
  }

  def getEnvVar(id: String): Int = {
    val envVar = envVars.get(id)
    if (envVar.isEmpty)
      error("Environment variable " + id + " was not defined in grammar file " + this.getFilename)
    envVar.get
  }

  def getAllEnvVars: util.ArrayList[String] = {
    val res = new util.ArrayList[String]
    for (ev <- envVars.toList) {
      res.add(ev._1)
    }
    res
  }

  def setEnvVar(varName: String, value: Int): Option[Int] = {
    log("Env variable " + varName + " set to " + value)
    envVars.put(varName, value)
  }

  def getFilename: String = filename

  def getIncludes: List[((String, String), (String, String))] = {
    includes.toList
  }

  def getIncludeLexemes: List[(String, (String, String))] = {
    includeLexemes.toList
  }


  def getParentIncludeFile(currentFilename: String, currentParams: String): String = {
    includes((currentFilename, currentParams))._1
  }

  def getParentIncludeParams(currentFilename: String, currentParams: String): String = {
    includes((currentFilename, currentParams))._2
  }

  def getParentIncludeLexeme(currentIncludeLexeme: String): String = {
    includeLexemes(currentIncludeLexeme)._1
  }

  def getIncludeLexemeParam(currentIncludeLexeme: String): String = {
    includeLexemes(currentIncludeLexeme)._2
  }

  def addOneInclude(currentID: String, currentParams: String, parentID: String, parentParams: String): Unit = {
    includes.put((currentID, currentParams), (parentID, parentParams))
  }

  def addOneIncludeLexeme(currentIL: String, parentIL: String, params: String): Unit = {
    includeLexemes.put(currentIL, (parentIL, params))

  }

  def addIncludes(otherIncludes: List[((String, String), (String, String))]): Unit = {
    includes ++= otherIncludes
  }

  def addIncludeLexemes(otherIncludeLexemes: List[(String, (String, String))]): Unit = {
    includeLexemes ++= otherIncludeLexemes
  }

  def includesThisFileWithParams(someFilename: String, someParams: String): Boolean = {
    includes.contains((someFilename, someParams))
  }

  def includesLexeme(someIncludeLexeme: String): Boolean = {
    includeLexemes.contains(someIncludeLexeme)
  }

  private def error(message: String) = {
    throw new GrammarVerificationException(message)
  }

  def verify(): Unit = {
    val usedNonTerminals = new mutable.HashSet[String]()
    for (nts <- nonTerminals.values)
      for (rule <- nts.getValidRules(this))
        for (usedNT <- rule.getLexemes.filter(_.lexemeType == LexemeType.NonTerminal))
          usedNonTerminals += usedNT.token

    for (usedNT <- usedNonTerminals)
      if (!nonTerminals.contains(usedNT))
        error(s"No rules declared for non-terminal $usedNT" + " in grammar file " + this.filename + ", full grammar: " + this.toString)
  }

  def verify1(message: String, checkEnvVar: Boolean): Unit = {
    val usedNonTerminals = new mutable.HashSet[String]()

    for (nts <- nonTerminals.values) {

      val rules = {
        if (checkEnvVar) nts.getValidRules(this) else nts.getAllRules
      }
      for (rule <- rules)
        for (usedNT <- rule.getLexemes.filter(_.lexemeType == LexemeType.NonTerminal))
          usedNonTerminals += usedNT.token
    }

    for (usedNT <- usedNonTerminals) {
      // used non-terminal is not in list of described non-terminals and is not in forward declaration list
      if (!nonTerminals.contains(usedNT) && !fwDeclarations.contains(usedNT))
        error(message + "\n" + s"No rules declared for non-terminal $usedNT" + " in grammar file " + this.filename + ", full grammar: " + this.toString)
    }
  }

  override def toString: String = {
    "Grammar with " + nonTerminals.size + " rules:\n\t" + nonTerminals.values.mkString("\n\t")
  }
}
