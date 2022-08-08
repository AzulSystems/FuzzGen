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

package com.azul.fuzzgen

import java.io.PrintWriter
import com.azul.fuzzgen.fuzzer.Fuzzer
import com.azul.fuzzgen.grammar.Grammar
import com.azul.fuzzgen.parser.{RulesFileReader, RulesParser}

import scala.collection.mutable

/**
  * Main runner for the Fuzzer.
  */

object FuzzGen {
  private val version: String = "FuzzGen v0.1"
  private val help: String = {
    "You can use the following options:\n" +
      "\t--version\tPrint information about current FuzzGen's version.\n" +
      "\t--help\tPrint this help message.\n" +
      "\t--verbose\tPrint details as generation goes on.\n" +
      "\t--dont-fail-on-warnings\tIgnore warning messages and proceed fuzzing.\n" +
      "\t--grammar=<path>\tSpecify path to grammar file. Must be specified.\n" +
      "\t--grammarParams=<path>\tSpecify path to additional grammar file, e.g., parameters for the current grammar. Optional.\n" +
      "\t--output=<path>\tPath to output directory. Defaulted to current directory.\n" +
      "\t--seed=<int>\tSpecify seed for generation. Defaulted to 0.\n" +
      "\t--num-tests=<int>\tSpecify the number of tests to generate. Defaulted to 1.\n" +
      "\t--num-attempts=<int>\tSpecify the number of fuzz attempts on one test. Defaulted to 20.\n" +
      "\t--parallel\tEnable parallel test generation.\n" +
      "\t--local-id-separator=<string>\tSpecify the separator string used in output of GetLocalIDs Lexeme. Defaulted to \",\".\n"
  }
  var dontFailOnWarnings: Boolean = false

  def error(message: String) = {
    throw new Exception(message)
  }

  def warning(message: String) = {
    if (FuzzGen.dontFailOnWarnings) {
      println("WARNING: " + message)
    } else {
      throw new Exception(message + "\n Use --dont-fail-on-warnings to ignore this warning")
    }
  }

  def main(args: Array[String]): Unit = {
    var pathToGrammar: Option[String] = None
    var pathToGrammarParams: Option[String] = None
    var pathToOutputDir: String = "."
    var seed = 0
    var verbose = false
    var numTests = 1
    var fuzzAttempts = 20
    var parallel = false
    var localIdSeparator: String = ","

    for (currArg <- args) {
      if (currArg.equals("--version")) {
        println(version)
        return
      } else if (currArg.equals("--help")) {
        println(help)
        return
      } else if (currArg.startsWith("--grammar=")) {
        pathToGrammar = Some(currArg.substring("--grammar=".length))
      } else if (currArg.startsWith("--grammarParams=")) {
        pathToGrammarParams = Some(currArg.substring("--grammarParams=".length))
      } else if (currArg.equals("--dont-fail-on-warnings")) {
        this.dontFailOnWarnings = true
      } else if (currArg.startsWith("--output=")) {
        pathToOutputDir = currArg.substring("--output=".length)
      } else if (currArg.startsWith("--seed=")) {
        seed = currArg.substring("--seed=".length).toInt
      } else if (currArg.equals("--verbose")) {
        verbose = true
      } else if (currArg.equals("--parallel")) {
        parallel = true
      } else if (currArg.startsWith("--num-tests=")) {
        numTests = currArg.substring("--num-tests=".length).toInt
      } else if (currArg.startsWith("--num-attempts=")) {
        fuzzAttempts = currArg.substring("--num-attempts=".length).toInt
      } else if (currArg.startsWith("--local-id-separator=")) {
        localIdSeparator = currArg.substring("--local-id-separator=".length)
      } else {
        throw new Exception("Unrecognized option: " + currArg)
      }
    }

    if (pathToGrammar.isEmpty)
      throw new Exception("Grammar file not specified! Use --grammar=<path> to specify it.")

    println(s"Requested generation of $numTests test(s) with starting seed $seed.\n" +
      "Grammar read from \"" + pathToGrammar.get + "\", output written to \"" + pathToOutputDir + "\"")

    val reader = new RulesFileReader
    val readerParams = new RulesFileReader
    var grammarParams:Grammar = null

    val rulesText = reader.readFrom(pathToGrammar.get)

    if (!pathToGrammarParams.isEmpty) {
      val rulesTextParams = readerParams.readFrom(pathToGrammarParams.get)
      val includesParams = new mutable.TreeMap[(String, String), (String, String)]()
      includesParams.put((pathToGrammarParams.get, ""), (null, ""))
      grammarParams = new RulesParser(pathToGrammarParams.get, "").parse(rulesTextParams, includesParams.toList, "", "", null)
    }

    val includeLexemes = new mutable.TreeMap[String, (String, String)]()
    includeLexemes.put(pathToGrammar.get, (null, ""))
    val includes = new mutable.TreeMap[(String,String), (String, String)]()
    includes.put((pathToGrammar.get, ""), (null, ""))
    val grammar_ = new RulesParser(pathToGrammar.get, "").parse(rulesText, includes.toList, "", "", null)
    val grammar = if (pathToGrammarParams.isEmpty) grammar_
    else grammarParams + grammar_
    if (verbose)
      println(grammar)
    val fuzzStartTime = System.currentTimeMillis()
    // FIXME: Enable parallel execution.
    val range = /*if (parallel) Range(0, numTests).par else*/ Range(0, numTests)
    val fuzzResults = range.map(i => {
      val currSeed = seed + i
      val smartNum = smartTestNumber(i, numTests)
      val outputFilePath = pathToOutputDir + "/" + "gen_" + smartNum
      var startTime =0L
      if (verbose) {
        println("Generating test #" + smartNum + " with seed " + currSeed + ", " + fuzzAttempts + " attempts per fuzz.")
        startTime = System.currentTimeMillis()
      }
      val result = new Fuzzer(currSeed, localIdSeparator).fuzz(grammar, fuzzAttempts)
      if (verbose) {
        val duration = System.currentTimeMillis() - startTime
        println("Generation of test #" + smartNum + " took " + duration + "ms")
      }
      if (result.isSuccessful) {
        if (verbose) {
          println(result)
          println("Writing results to " + outputFilePath)
        }
        val pw = new PrintWriter(outputFilePath)
        pw.println(result.fuzzText.get)
        pw.close()
      } else {
        println("Generation of test #" + smartNum + " FAILED: " + result + "!")
        if (verbose) {
          println("Last failure was due to: " + result.lastFailureReason.get)
        }
      }
      result
    })

    val numSuccess = fuzzResults.count(_.isSuccessful)
    val numFailures = fuzzResults.count(!_.isSuccessful)
    val fuzzDuration = System.currentTimeMillis() - fuzzStartTime
    println(s"Generation finished in $fuzzDuration ms: $numSuccess succeeded, $numFailures failed.")
    if (verbose) {
      val avgNumAttempts = fuzzResults.map(_.numAttempts).sum.toDouble / fuzzResults.size
      val maxNumAttempts = fuzzResults.map(_.numAttempts).max
      println("Average num attempts: " + avgNumAttempts)
      println("Max num attempts: " + maxNumAttempts)
    }
  }

  private def smartTestNumber(currentTestNumber: Int, maxTests: Int) = {
    val requiredLength = maxTests.toString.length
    val actualLength = currentTestNumber.toString.length
    ("0" * (requiredLength - actualLength)) + currentTestNumber
  }
}
