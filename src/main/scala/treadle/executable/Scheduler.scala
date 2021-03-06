// See LICENSE for license details.

package treadle.executable

import logger.LazyLogging

import scala.collection.mutable

class Scheduler(val dataStore: DataStore, val symbolTable: SymbolTable) extends LazyLogging {
  var activeAssigns   : mutable.ArrayBuffer[Assigner] = new mutable.ArrayBuffer
  val orphanedAssigns : mutable.ArrayBuffer[Assigner] = new mutable.ArrayBuffer

  def setVerboseAssign(isVerbose: Boolean): Unit = {
    def setMode(assigner: Assigner): Unit = {
      assigner.setVerbose(isVerbose)
    }
    activeAssigns.foreach { setMode }
    orphanedAssigns.foreach { setMode }
  }

  def setLeanMode(setLean: Boolean): Unit = {
    def setMode(assigner: Assigner): Unit = {
      assigner.setLeanMode(setLean)
    }
    activeAssigns.foreach { setMode }
    orphanedAssigns.foreach { setMode }
  }

  /**
    * Execute the seq of assigners
    * @param assigners list of assigners
    */
  def executeAssigners(assigners: Seq[Assigner]): Unit = {
    var index = 0
    val lastIndex = assigners.length
    // val t0 = System.nanoTime()
    while(index < lastIndex) {
      assigners(index).run()
      index += 1
    }
    //  val t1 = System.nanoTime()
    //  val isLean = assigners.forall(x => !x.verboseAssign)
    //  println(s"$index assigners in ${t1 - t0} ns, ${index.toDouble * 1000000000 / (t1 - t0)} nodes/sec" +
    //          s" ${1000000000 / (t1 - t0)} Hz  isLean $isLean")
  }

  /**
    *  updates signals that depend on inputs
    */
  def executeActiveAssigns(): Unit = {
    executeAssigners(activeAssigns)
  }

  /**
    * de-duplicates and sorts assignments that depend on top level inputs.
    */
  def sortInputSensitiveAssigns(): Unit = {
    val deduplicatedAssigns = activeAssigns.distinct
    activeAssigns = deduplicatedAssigns.sortBy { assigner: Assigner =>
      assigner.symbol.cardinalNumber
    }
  }

  def setOrphanedAssigners(assigners: Seq[Assigner]): Unit = {
    orphanedAssigns.clear()
    orphanedAssigns ++= assigners
  }

  /**
    * Render the assigners managed by this scheduler
    * @return
    */
  def render: String = {
    s"Static assigns (${orphanedAssigns.size})\n" +
      orphanedAssigns.map { assigner =>
        assigner.symbol.render
      }.mkString("\n") + "\n\n" +
    s"Active assigns (${activeAssigns.size})\n" +
    activeAssigns.map { assigner =>
      assigner.symbol.render
    }.mkString("\n") + "\n\n"
  }
}

object Scheduler {
  def apply(dataStore: DataStore, symbolTable: SymbolTable): Scheduler = new Scheduler(dataStore, symbolTable)
}
