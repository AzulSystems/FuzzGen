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

import com.azul.fuzzgen.grammar.Grammar
import com.azul.fuzzgen.parser.LexemeType.{LendScopeWithID, LexemeType}
import com.azul.fuzzgen.parser.{ExpectIDLexeme, LendScopeWithIDLexeme, _}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/**
  * Generates fuzz programs using grammar.
  */
class Fuzzer(seed: Long, localIdSeparator: String) {
  private val workList = new collection.mutable.ListBuffer[Lexeme]()
  private val nextStageNonTerms = new collection.mutable.ListBuffer[NonTerminalLexeme]()
  private var rootFuzzNode: NonTerminalFuzzNode = _
  private var currentFuzzNode: NonTerminalFuzzNode = _
  private val rnd = new Random(seed)
  private var currentScope: Option[Scope] = _
  private var returnToScope: Option[Scope] = _
  val allScopes = new ArrayBuffer[Scope]()
  var grammar: Grammar = _
  val lastID = new mutable.HashMap[String, String]() // last id with prefix: prefix -> last reused id with this prefix
  private var stage: Option[Int] = _
  private var seenNextStage: Option[Int] = _
  private val outputNodes = new mutable.HashMap[NonTerminalLexeme, NonTerminalFuzzNode]()
  private val nonTerminalScopes = new mutable.HashMap[NonTerminalLexeme, Option[Scope]]()

  // Initialize the fuzzer as a preparation step for new fuzz attempt. Drops all info other than the random state.
  private def init(g: Grammar): Unit = {
    workList.clear()
    nextStageNonTerms.clear()
    rootFuzzNode = new NonTerminalFuzzNode()
    currentFuzzNode = rootFuzzNode
    currentScope = None
    allScopes.clear()
    grammar = new Grammar(g)
    grammar.fuzzer=this
    stage = Some(0)
    seenNextStage = None
    outputNodes.clear()
    nonTerminalScopes.clear()
  }

  // Processes a single non-terminal lexeme, randomly choose a rule for it and applies it.
  private def processNonTerminal(curr: Lexeme): Unit = {
    assert(curr.lexemeType == LexemeType.NonTerminal, "Expected non-terminal lexeme!!")
    grammar.fuzzer = this
    val ntStage = grammar.getStageFor(curr.token)
    var delay = false
    if (stage.isDefined)
      if (ntStage.isEmpty || ntStage.get > stage.get)
        delay = true
    if (!delay) {
      val rule = grammar.getRuleFor(curr.token, rnd)
      workList.prependAll(rule.getLexemes)
    } else {
      // Update seenNextStage
      if (ntStage.isDefined) {
        if (stage.isEmpty) {
          fuzzingError("INFO: rule " + curr.token + " with stage "+ ntStage.get +" is invoked from rule with default stage")
        } else if  (stage.get > ntStage.get) {
          fuzzingError("INFO: rule " + curr.token + " with stage "+ ntStage.get +" is invoked from rule with stage " + stage.get)
        }
        if (seenNextStage.isEmpty || seenNextStage.get > ntStage.get)
          seenNextStage = ntStage
      }
      val oldNT = curr.asInstanceOf[NonTerminalLexeme]
      var nt = new NonTerminalLexeme(oldNT.token, oldNT.lexemeType, oldNT.stage)
      val fuzzNode = new NonTerminalFuzzNode()
      currentFuzzNode.addChild(fuzzNode)
      outputNodes.put(nt, fuzzNode)
      nonTerminalScopes.put(nt, currentScope)
      nextStageNonTerms += nt
    }
  }

  // Process a single literal lexeme, put it to the fuzz result.
  private def processLiteral(curr: Lexeme): Unit = {
    assert(curr.lexemeType == LexemeType.Literal, "Expected literal lexeme!")
    currentFuzzNode.addChild(new TerminalFuzzNode(curr.token))
  }

  // Creates a new scope and puts it on top of stack.
  private def beginScope(curr: Lexeme): Unit = {
    val scope = new Scope(currentScope, rnd)
    allScopes += scope
    currentScope = Some(scope)
  }

  // "Lends" scope containing local id specified as parameter and puts it on top of stack.
  private def lendScope(curr: IDLexeme): Unit = {
    if (returnToScope != null && returnToScope.isDefined)
      fuzzingError("LendScope: can't lend scope for ID declared with prefix \"" + curr.token + "\" because current scope is already lent")
    val matchingScopes =new ArrayBuffer[Scope] ()
    for (s <- allScopes) {
      if (s.getID(curr.token, lookupParentScopes = false).isDefined) {
        matchingScopes += s
      }
    }
    if (matchingScopes.isEmpty)
        fuzzingError("LendScope: No IDs declared with prefix \"" + curr.token + "\"")
    val index = rnd.nextInt(matchingScopes.size)
    returnToScope = Some(getCurrentScope)
    currentScope = Some(matchingScopes(index))
  }

  // "Lends" scope containing local ids specified as parameter and puts it on top of stack.
  private def lendScopeWithID(curr: LendScopeWithIDLexeme): Unit = {
    if (returnToScope != null && returnToScope.isDefined) {
      fuzzingError("LendScopeWithID: can't lend scope for ID declared with prefix \"" + curr.token + "\" because current scope is already lent")
    }
    val matchingScopes =new ArrayBuffer[Scope] ()
    for (s <- allScopes) {
      if (s.getID(curr.token, lookupParentScopes = false).isDefined && s.getID(curr.ID, lookupParentScopes = false).isDefined ) {
        matchingScopes += s
      }
    }
    if (matchingScopes.isEmpty)
      fuzzingError("LendScopeWithID: No IDs declared with prefix \"" + curr.token + " and " +curr.ID  +  "\"" )
    val index = rnd.nextInt(matchingScopes.size)
    returnToScope = Some(getCurrentScope)
    currentScope = Some(matchingScopes(index))
  }

  // "Lends" scope containing local ids specified as parameter and puts it on top of stack.
   def checkScopeWithID(curr: LendScopeWithIDLexeme, failIfNotFound: Boolean, f: Fuzzer): Int = {

    val matchingScopes =new ArrayBuffer[Scope] ()
    for (s <- f.allScopes) {
      if (s.getID(curr.token, lookupParentScopes = false).isDefined && s.getID(curr.ID, lookupParentScopes = false).isDefined ) {
        matchingScopes += s
      }
    }
    if (matchingScopes.isEmpty) {
      if (failIfNotFound) {
        fuzzingError("LendScopeWithID: No IDs declared with prefix \"" + curr.token + " and " + curr.ID + "\"")
      } else {
        return 0
      }
    }
    return matchingScopes.length
  }

  // "Lends" scope containing local ids specified as parameter and puts it on top of stack.
  def checkScopeWithIDLight(curr: LendScopeWithIDLexeme, failIfNotFound: Boolean, f: Fuzzer): Int = {

    for (s <- f.allScopes) {
      if (s.getID(curr.token, lookupParentScopes = false).isDefined && s.getID(curr.ID, lookupParentScopes = false).isDefined ) {
        return 1
      }
    }
    return 0
  }




  // "Lends" scope containing local ids specified as parameter and puts it on top of stack
  private def lendScopeWithIDByPrefix(curr: LendScopeWithIDByPrefixLexeme): Unit = {
      if (returnToScope != null && !returnToScope.isEmpty) {
          fuzzingError("LendScopeWithIDByPrefix: can't lend scope for ID declared with prefix \"" + curr.scopePrefix + "\" because current scope is already lent")
      }

      val firstTerminalMask = curr.scopePrefix + "%" + curr.IDtoken  // Cls_%_ObjVar_

          if (!getCurrentScope.getID(firstTerminalMask, true).isDefined) {
              fuzzingError("LendScopeWithIDByPrefix: can't lend scope for ID declared with prefix " + firstTerminalMask + " because this ID is not found in current scope")
          }

      val firstTerminalNode = getCurrentScope.getID(firstTerminalMask, true).get // Cls_0_ObjVar_1
          val firstTerminalPrefix = curr.scopePrefix // Cls_
          val firstTerminalSuffix =  curr.IDtoken //firstTerminalMask.split("%")(1) // _ObjVar_
          val firstTerminalPrefixWithID = firstTerminalNode.token.split(firstTerminalSuffix)(0) //Cls_0
          val secondTerminalPrefix = curr.scopeContains // method_

          lastID(firstTerminalMask) = firstTerminalNode.token
          lastID(curr.token) = firstTerminalNode.token
          lastID(firstTerminalPrefix) = firstTerminalPrefixWithID
          val matchingScopes =new ArrayBuffer[Scope] ()
          for (s <- allScopes) {

              if (s.getID(firstTerminalPrefix, false).isDefined &&
                      s.getID(firstTerminalPrefix, false).get.token == firstTerminalPrefixWithID &&
                      s.getID(secondTerminalPrefix, true).isDefined ){
                  matchingScopes += s
              }
          }

      if (matchingScopes.isEmpty)
          fuzzingError("LendScopeWithIDByPrefix: No IDs declared with prefix \"" + firstTerminalMask + "\" and \"" +curr.IDtoken  +  "\"" )
              val index = rnd.nextInt(matchingScopes.size)
              returnToScope = Some(getCurrentScope)
              currentScope = Some(matchingScopes(index))
  }


  // End the current scope and drop all its contents.
  private def endScope(curr: Lexeme): Unit = {
    if (currentScope.isEmpty)
      fuzzingError("Scope closed before it was open!")
    currentScope.get.verify()
    currentScope = currentScope.get.getParent()
  }

  // Return the current scope if it was lended and drop all its contents and remove from scopes stack.
  def returnScope(): Unit = {
    if (currentScope.isEmpty)
      fuzzingError("Scope closed before it was open!")
    currentScope = returnToScope
    returnToScope = None
  }

  private def getCurrentScope: Scope = {
    if (currentScope.isEmpty)
      fuzzingError("Attempt to access non-existent scope!")
    currentScope.get
  }

  // Utility function: get a random int within the range.
  private def getIntFromRange(curr: IntExprFromRangeLexeme): Unit = {
    val min = curr.min(grammar)
    val max = curr.max(grammar)
    val number = rnd.nextInt(max - min) + min
    currentFuzzNode.addChild(new TerminalFuzzNode(number.toString))
  }

  // Utility function: get a random long within the range.
  private def getLongFromRange(curr: LongFromRangeLexeme): Unit = {
    val min = curr.min
    val max = curr.max
    val number = rnd.nextLong(max - min) + min
    currentFuzzNode.addChild(new TerminalFuzzNode(number.toString))
  }

  // Utility function: get a random double within the range.
  private def getDoubleFromRange(curr: DoubleFromRangeLexeme): Unit = {
    val min = curr.min
    val max = curr.max
    val number = min + (max - min) * rnd.nextDouble()
    currentFuzzNode.addChild(new TerminalFuzzNode(number.toString))
  }

  // Creates a new ID in the current scope.
  def createID(lexeme: IDLexeme): Unit = {
    val f = getCurrentScope.declareID(lexeme.token)
    lastID(lexeme.token) = f.token
    currentFuzzNode.addChild(f)
  }

  // Creates a new ID in the current scope using last id as a prefix (e.g.: Cls_5_ObjVar_)
  def createIDFromLastID(lexeme: CreateIDFromLastIDLexeme) = {

      val lastIDStr = lastID(lexeme.lastIDAsPrefix)
          val newPrefix = lastIDStr + lexeme.suffix

          currentFuzzNode.addChild(getCurrentScope.createLazyID(newPrefix))
          //    val f = getCurrentScope.declareID(newPrefix)
          //    lastID(newPrefix) = f.token
          //    currentFuzzNode.addChild(f)
  }

  // Creates a "lazy" ID in the current scope which will only be published when registerLazyIDs is called.
  def createLazyID(lexeme: IDLexeme): Unit = {
    currentFuzzNode.addChild(getCurrentScope.createLazyID(lexeme.token))
  }

  // Returns an existing ID from current scope if lookupParentScopes is false, from any active scope if it is true.
  def reuseID(lexeme: IDLexeme, lookupParentScopes: Boolean): Unit = {
    val existingID = getCurrentScope.getID(lexeme.token, lookupParentScopes)
    if (existingID.isEmpty)
      fuzzingError("reuseID: No IDs declared with prefix " + lexeme.token + "lookupParentScopes = " + lookupParentScopes.toString + ", last used IDs " + lastID)
    lastID(lexeme.token) = existingID.get.token
    currentFuzzNode.addChild(existingID.get)
  }

  // Find an arbitrary scope that contains an ID of given kind. Returns this ID and adds current scope to the
  // predecessors of the found scope.
  def reuseIDAndLink(lexeme: IDLexeme): Unit = {
    val token = lexeme.token
    val candidates = new ArrayBuffer[Scope]()
    // Collect scopes that contain IDs of this token.
    for (scope <- allScopes) {
      if (scope.getID(token, lookupParentScopes = false).isDefined) {
        candidates += scope
      }
    }

    if (candidates.isEmpty)
      fuzzingError("reuseIDAndLink: No IDs declared with prefix \"" + lexeme.token + "\"")
    val idx = rnd.nextInt(candidates.length)
    val selectedScope = candidates(idx)
    val existingID = selectedScope.getID(token, lookupParentScopes = false).get
    selectedScope.addPredecessor(getCurrentScope)
    currentFuzzNode.addChild(existingID)
  }

  def getLastID(lexeme: IDLexeme): Unit = {
    if (lastID(lexeme.token).isEmpty)
      fuzzingError("getLastID: No IDs declared with prefix " + lexeme.token)
    currentFuzzNode.addChild(new TerminalFuzzNode(lastID(lexeme.token)))
  }

  // Returns an existing IDs from current scope with specified prefix.
  def getLocalIDs(lexeme: GetLocalIDsLexeme, failIfEmpty: Boolean, separator: String = localIdSeparator): Unit = {
    val collectedIDs = getCurrentScope.getLocalIDs(lexeme.token, separator)
    if (collectedIDs.isEmpty && failIfEmpty)
      fuzzingError("getLocalIDs: No IDs declared with prefix " + lexeme.token)
    if (collectedIDs.isDefined) {
      currentFuzzNode.addChild(collectedIDs.get)
    }
  }

  // Returns a list of existing ID from current scope + scopes depth levels up.
  def getAllIDs(lexeme: GetAllIDsLexeme): Unit = {
    val collectedIDs = getCurrentScope.getAllIDs(lexeme.token, localIdSeparator)
    if (collectedIDs.isEmpty)
      fuzzingError("getIDsUpToDepth: No IDs declared with prefix " + lexeme.token + " ")
    currentFuzzNode.addChild(collectedIDs.get)
  }

  // Returns a number of existing ID from current scope + scopes depth levels up.
  def getAllIDsCount(lexeme: ExpectIDLexeme, f: Fuzzer): Int = {

    val c = f.getCurrentScope.IDsCount(lexeme.token, new ArrayBuffer[String](), true)
      return c
  }

  // Returns a number of existing ID from current scope + scopes depth levels up.
  def getAllIDsCount1(fe: IDsAvailableExpression, f: Fuzzer): Int = {

    //getCurrentScope.IDsCount(fe.id, new ArrayBuffer[String](), true)
    f.getCurrentScope.IDsCount(fe.id, new ArrayBuffer[String](), true)
  }

  // Returns last reused ID from current scope.
  def getLastReusedLocalID(): Unit = {
    val lastReusedLocalID = getCurrentScope.getLastReusedLocalID
    if (lastReusedLocalID.isEmpty)
      fuzzingError("Local IDs have not been reused yet")
    currentFuzzNode.addChild(lastReusedLocalID.get)
  }

  // Makes all lazily created IDs available for reuse.
  def registerLazyIDs(): Unit = {
    getCurrentScope.registerLazyIDs(this)
  }

  // Sets an environment variable to the grammar.
  def setEnvVar(lexeme: SetEnvVarLexeme): Option[Int] = {
    grammar.setEnvVar(lexeme.token, lexeme.value.eval(this.grammar))
  }

  // get number of IDs available
  def IDsCount(lexeme: ExpectIDLexeme, f: Fuzzer): Int = {
    val c = f.getAllIDsCount(lexeme, f)
    return c
  }

  def checkIDs1(id: String, f: Fuzzer): Int = {
    val scope_ = f.getCurrentScope



    if ( scope_.collectIDsLight(id, true)) {
      return 1
    } else {
      return 0
    }

//        val candidates = new ArrayBuffer[String]()
//
//    scope_.collectIDs(id, candidates, true)
//
//    if (candidates.isEmpty) {
//      return 0
//    } else {
//      return 1
//    }

  }

  def checkIDs(fe: IDsAvailableExpression, f:Fuzzer) : Int = {
  val existingID = f.getCurrentScope.getID(fe.id, true)

  if (existingID.isEmpty) {
    return 0
  }
    return 1
  }

  def checkIDsCount(fe: IDsAvailableExpression, f:Fuzzer) : Int = {
    val matchingScopes =new ArrayBuffer[Scope] ()
    for (s <- f.allScopes) {
      if (s.getID(fe.id, lookupParentScopes = false).isDefined  ) {
        matchingScopes += s
      }
    }
    if (matchingScopes.isEmpty) {
        return 0
      }
    return matchingScopes.length

    }

  def checkIDsCountLight(fe: IDsAvailableExpression, f:Fuzzer) : Int = {
    val matchingScopes =new ArrayBuffer[Scope] ()
    for (s <- f.allScopes) {
      if (s.getID(fe.id, lookupParentScopes = false).isDefined  ) {
        return 1
      }
    }

    return 0

  }



  // Processes the next item in the worklist.
  private def processNext(): Unit = {
    val curr = workList.head
    workList.remove(0)
    curr.lexemeType match {
      case LexemeType.NonTerminal => processNonTerminal(curr)
      case LexemeType.Literal => processLiteral(curr)
      case LexemeType.BeginScope => {beginScope(curr)}
      case LexemeType.EndScope => endScope(curr)
      case LexemeType.LendScopeWithIDByPrefix => lendScopeWithIDByPrefix(curr.asInstanceOf[LendScopeWithIDByPrefixLexeme])
      case LexemeType.LendScopeWithID => lendScopeWithID(curr.asInstanceOf[LendScopeWithIDLexeme])
      case LexemeType.LendScope => lendScope(curr.asInstanceOf[IDLexeme])
      case LexemeType.ReturnScope => returnScope()
      case LexemeType.IntExprFromRange => getIntFromRange(curr.asInstanceOf[IntExprFromRangeLexeme])
      case LexemeType.LongFromRange => getLongFromRange(curr.asInstanceOf[LongFromRangeLexeme])
      case LexemeType.DoubleFromRange => getDoubleFromRange(curr.asInstanceOf[DoubleFromRangeLexeme])
      case LexemeType.CreateID => createID(curr.asInstanceOf[IDLexeme])
      case LexemeType.CreateIDFromLastID => createIDFromLastID(curr.asInstanceOf[CreateIDFromLastIDLexeme])
      case LexemeType.ReuseID => reuseID(curr.asInstanceOf[IDLexeme], lookupParentScopes = true)
      case LexemeType.ReuseIDAndLink => reuseIDAndLink(curr.asInstanceOf[IDLexeme])
      case LexemeType.ReuseLocalID => reuseID(curr.asInstanceOf[IDLexeme], lookupParentScopes = false)
      case LexemeType.GetLocalIDs => getLocalIDs(curr.asInstanceOf[GetLocalIDsLexeme], failIfEmpty = true)
      case LexemeType.GetLocalIDsOrEmpty => getLocalIDs(curr.asInstanceOf[GetLocalIDsLexeme], failIfEmpty = false)
      case LexemeType.GetLocalIDsOrEmptySeparator => getLocalIDs(curr.asInstanceOf[GetLocalIDsLexemeSeparator], failIfEmpty = false, curr.asInstanceOf[GetLocalIDsLexemeSeparator].separator)
      case LexemeType.GetAllIDs => getAllIDs(curr.asInstanceOf[GetAllIDsLexeme])
//      case LexemeType.ExpectID => { (curr.asInstanceOf[ExpectIDLexeme]).count  = IDsCount(curr.asInstanceOf[ExpectIDLexeme], this);
//        }
//      case LexemeType.ExpectScopeWithID => { (curr.asInstanceOf[ExpectScopeWithIDLexeme]).count  =
//        {  val c = checkScopeWithID( new LendScopeWithIDLexeme(curr.asInstanceOf[ExpectScopeWithIDLexeme].token, LendScopeWithID, curr.asInstanceOf[ExpectScopeWithIDLexeme].IDToken),
//        false, this); /*returnScope();*/  c } }

      case LexemeType.GetLastReusedLocalID => getLastReusedLocalID()
      case LexemeType.GetLastID => getLastID(curr.asInstanceOf[IDLexeme])
      case LexemeType.CreateLazyID => createLazyID(curr.asInstanceOf[IDLexeme])
      case LexemeType.RegisterLazyIDs => registerLazyIDs()
      case LexemeType.SetEnvVar => setEnvVar(curr.asInstanceOf[SetEnvVarLexeme])
      case _ => {throw new RuntimeException("Unknown lexeme type: " + curr)}
    }
  }

  private def fuzzOneStage() {
    seenNextStage = None
    val currentStageNonTerms = nextStageNonTerms.clone()
    nextStageNonTerms.clear()
    for (nonTerm <- currentStageNonTerms) {
      workList += nonTerm
      currentFuzzNode = outputNodes(nonTerm)
      currentScope = nonTerminalScopes(nonTerm)
      while (workList.nonEmpty)
        processNext()
    }
    stage = seenNextStage
  }

  private def fuzzImpl(): Option[String] = {
    while (stage.isDefined)
      fuzzOneStage()
    fuzzOneStage()

    // TODO: We need to validly return this check somehow.
    Some(rootFuzzNode.toString)
  }

  // Attempts to fuzz a test from grammar g, using maxAttempts attempts at most.
  def fuzz(g: Grammar, maxAttempts: Int = 100): FuzzingResults = {
    //g.fuzzer = this
    var attempt = 0
    var result: Option[String] = None
    var lastSeenFuzzException: Option[FuzzException] = None
    rnd.setSeed(seed)
    while (attempt < maxAttempts && result.isEmpty) {
      attempt += 1
      try {
        init(g)
        g.fuzzer = this
       // g.verify();
        //TODO: why can't verify???
        //assert({grammar.verify(); true}, "Verification failed")
        val mainLexeme = new NonTerminalLexeme(Lexer.MAIN, LexemeType.NonTerminal, None)
        outputNodes.put(mainLexeme, rootFuzzNode)
        nonTerminalScopes.put(mainLexeme, currentScope)
        nextStageNonTerms += mainLexeme
        result = fuzzImpl()
      } catch {
        case fex: FuzzException =>
          { lastSeenFuzzException = Some(fex)}
        case ex: Exception => { throw ex}
      }
    }
    //grammar.fuzzer = this
    FuzzingResults(result, attempt, maxAttempts, lastSeenFuzzException)

  }

  def fuzzingError(message: String) = {
    System.err.println("FUZZING ERROR: " + message)
    if (returnToScope != null && returnToScope.isDefined) returnScope()
    throw new FuzzException(message)
  }
}
