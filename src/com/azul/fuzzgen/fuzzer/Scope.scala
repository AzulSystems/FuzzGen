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

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/**
 * Contains all identifiers visible at this point.
 */
class Scope(parent: Option[Scope], rnd: Random) {
  private case class LazyID(prefix: String, id: String)

  // Maps prefixes to all declared nodes from this scope.
  private val map = new mutable.TreeMap[String, ArrayBuffer[String]]()
  private val pendingLazyIDs = new ArrayBuffer[LazyID]()
  private var lastReusedId: Option[String] = None
  private var freeID = 0
  private val predecessors = new ArrayBuffer[Scope]()
  private val children = new ArrayBuffer[Scope]()

  {
    if (parent.isDefined) {
      parent.get.children += this
    }
  }
  def addPredecessor(scope: Scope): Unit = {
    if (scope.getParent != getParent)
      error("Predecessor links are only possible between siblings so far!")
    predecessors += scope
  }

  def getParent: Option[Scope] = {
    parent
  }

  private def getImmediateDominator: Option[Scope] = {
    if (getParent.isEmpty || predecessors.isEmpty) {
      getParent
    } else {
      // FIXME: Should have a better way of finding children.
      val entry = parent.get.children(0)
      if (this == entry)
        return getParent

      def computeReachable(exclude: Option[Scope]) = {
        val worklist = new ArrayBuffer[Scope]()
        val visited = new mutable.HashSet[Scope]()

        def enqueue(scope: Scope): Unit = {
          if (!exclude.contains(scope) && !visited.contains(scope)) {
            visited += scope
            worklist += scope
          }
        }

        for (pred <- predecessors)
          enqueue(pred)

        while (worklist.nonEmpty) {
          val curr = worklist.last
          worklist.remove(worklist.length - 1)
          for (pred <- curr.predecessors)
            enqueue(pred)
        }

        visited
      }
      // Check if entry is reachable.
      val allReachable = computeReachable(None)
      allReachable.remove(this)
      if (!allReachable.contains(entry))
        // Entry is not even reachable, bail.
        return getParent

      var bestCandidate: Option[Scope] = None
      var bestScore = 0
      for (candidate <- allReachable) {
        val reachableWithExclude = computeReachable(Some(candidate))
        if (!reachableWithExclude.contains(entry)) {
          // This is a dominator. Is it better than what we have?
          if (bestCandidate.isEmpty || bestScore > reachableWithExclude.size) {
            bestScore = reachableWithExclude.size
            bestCandidate = Some(candidate)
          }
        }
      }
      if (bestCandidate.isEmpty)
        return getParent
      bestCandidate
    }
  }

  private def nextFreeID: Int = {
    val dom = getImmediateDominator
    if (dom.isDefined)
      return dom.get.nextFreeID
    val ret = freeID
    freeID += 1
    ret
  }

  @tailrec
  private def containsID(prefix: String, id: String): Boolean = {
    if (map.contains(prefix) && map(prefix).contains(id))
      return true
    val dom = getImmediateDominator
    if (dom.isEmpty)
      return false
    dom.get.containsID(prefix, id)
  }

  private def createNewID(prefix: String) = {
    prefix + nextFreeID
  }

  private def getIDListFor(prefix: String) = {
    if (!map.contains(prefix))
      map.put(prefix, new ArrayBuffer[String]())
    map(prefix)
  }

  def declareID(prefix: String): TerminalFuzzNode = {
    var newID: String = createNewID(prefix)
    if (containsID(prefix, newID))
      error("Duplicating IDs detected: " + newID)
    getIDListFor(prefix) += newID
    new TerminalFuzzNode(newID)
  }

  def createLazyID(prefix: String): TerminalFuzzNode = {
    val newID: String = createNewID(prefix)
    if (containsID(prefix, newID))
      error("Duplicating IDs detected!")
    pendingLazyIDs += LazyID(prefix, newID)
    new TerminalFuzzNode(newID)
  }

  def registerLazyIDs(): Unit = {
    if (pendingLazyIDs.isEmpty)
      error("No pending lazy IDs to register!")

    for (lazyID <- pendingLazyIDs)
      getIDListFor(lazyID.prefix) += lazyID.id

    pendingLazyIDs.clear()
  }

  private def collectIDs(prefix: String, candidates: ArrayBuffer[String], lookupParentScopes: Boolean): Unit = {
    if (lookupParentScopes) {
      val dom = getImmediateDominator
      if (dom.isDefined)
        dom.get.collectIDs(prefix, candidates, lookupParentScopes)
    }
    val maybeMap = map.get(prefix)
    if (maybeMap.isEmpty)
      return
    candidates ++= maybeMap.get
  }

  def getID(prefix: String, lookupParentScopes: Boolean): Option[TerminalFuzzNode] = {
    val candidates = new ArrayBuffer[String]()
    collectIDs(prefix, candidates, lookupParentScopes)
    if (candidates.isEmpty)
      return None
    val index = rnd.nextInt(candidates.size)
    lastReusedId = Some(candidates(index))
    Some(new TerminalFuzzNode(candidates(index)))
  }

  def getLastReusedLocalID: Option[FuzzNode] = {
    if (lastReusedId.isEmpty)
      return None
    Some(new TerminalFuzzNode(lastReusedId.get))
  }

  def getLocalIDs(prefix: String, localIdSeparator: String): Option[FuzzNode] = {
    val collectedIDs = new ArrayBuffer[String]()
    collectIDs(prefix, collectedIDs, lookupParentScopes = false)
    if (collectedIDs.isEmpty) {
      return None
    }
    Some(new TerminalFuzzNode(collectedIDs.toList.mkString(localIdSeparator)))
  }

  def getAllIDs(prefix: String, localIdSeparator: String): Option[FuzzNode] = {
    val collectedIDs = new ArrayBuffer[String]()
    collectIDs(prefix, collectedIDs, lookupParentScopes = true)
    if (collectedIDs.isEmpty) {
      return None
    }
    Some(new TerminalFuzzNode(collectedIDs.toList.mkString(localIdSeparator)))
  }

  def dump(dumpParents: Boolean = false): Unit = {
    System.err.println("Scope:")
    for (key <- map.keys) {
      System.err.println("\tPrefix " + key + ":")
      for (value <- map.get(key))
        System.err.println("\t\t" + value)
    }
    if (dumpParents) {
      val dom = getImmediateDominator
      if (dom.isDefined)
        dom.get.dump(dumpParents)
    }
  }

  def verify(): Unit = {
    if (pendingLazyIDs.nonEmpty) {

      error("Unregistered pending values! " + pendingLazyIDs.mkString(","))
    }
  }

  private def error(message: String) = {
    throw new Exception(message)
  }
}
