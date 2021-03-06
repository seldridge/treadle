// See LICENSE for license details.

package treadle.executable

import treadle.{ExecutionEngine, TreadleException}
import treadle.chronometry.UTC

trait ClockStepper {
  var cycleCount: Long = 0L
  def run(steps: Int): Unit
  def getCycleCount: Long = cycleCount
  def addTask(taskTime: Long)(task: () => Unit): Unit
}

class NoClockStepper extends ClockStepper {
  override def run(steps: Int): Unit = {}

  override def addTask(taskTime: Long)(task: () => Unit): Unit = {
    throw TreadleException(s"Timed task cannot be added to circuits with no clock")
  }
}

class SimpleSingleClockStepper(
  engine: ExecutionEngine,
  dataStore: DataStore,
  clockSymbol: Symbol,
  resetSymbolOpt: Option[Symbol],
  clockPeriod: Long,
  wallTime: UTC
) extends ClockStepper {

  var clockIsHigh: Boolean = false
  def clockIsLow: Boolean = ! clockIsHigh

  private val upPeriod = clockPeriod / 2
  private val downPeriod = clockPeriod - upPeriod

  var resetTaskTime: Long = -1L

  override def run(steps: Int): Unit = {

    for(_ <- 0 until steps) {
      if (engine.inputsChanged) {
        engine.evaluateCircuit()
      }

      cycleCount += 1
      wallTime.incrementTime(downPeriod)
      if(resetTaskTime >= 0 && wallTime.currentTime >= resetTaskTime) {
        resetSymbolOpt.foreach { resetSymbol =>
          engine.setValue(resetSymbol.name, 0)
        }
        resetTaskTime = -1L
      }
      engine.setValue(clockSymbol.name, 1)
      engine.evaluateCircuit()

      wallTime.incrementTime(upPeriod)
      if(resetTaskTime >= 0 && wallTime.currentTime >= resetTaskTime) {
        resetSymbolOpt.foreach { resetSymbol =>
          engine.setValue(resetSymbol.name, 0)
        }
        resetTaskTime = -1L
      }
      engine.setValue(clockSymbol.name, 0)
    }
  }

  override def addTask(taskTime: Long)(task: () => Unit): Unit = {
    if(resetTaskTime >= 0) {
      throw TreadleException(s"Timed add second reset task to single clock")
    }
    resetTaskTime = taskTime
  }
}

class MultiClockStepper(engine: ExecutionEngine, clockName: String, wallTime: UTC) extends ClockStepper {
  override def run(steps: Int): Unit = {
    for (_ <- 0 until steps) {
      if (engine.inputsChanged) {
        engine.evaluateCircuit()
      }

      cycleCount += 1
      if (engine.verbose) println(s"step $cycleCount at ${wallTime.currentTime}")
      wallTime.runToTask(s"$clockName/up")
      wallTime.runUntil(wallTime.currentTime)
      if (engine.verbose) println(s"clock raised at ${wallTime.currentTime}")
      engine.evaluateCircuit()
      wallTime.runToTask(s"$clockName/down")
      wallTime.runUntil(wallTime.currentTime)
      if (engine.verbose) println(s"Step finished step at ${wallTime.currentTime}")
    }
  }

  override def addTask(taskTime: Long)(task: () => Unit): Unit = {
    wallTime.addOneTimeTask(taskTime)(task)
  }
}
