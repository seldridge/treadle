// See LICENSE for license details.

package treadle.chronometry

import scala.collection.mutable

class UTC private (scaleName: String = "picoseconds") {
  private var internalTime: Long = 0L
  def currentTime:  Long = internalTime
  def setTime(time: Long): Unit = {
    internalTime = time
    onTimeChange()
  }

  val eventQueue = new mutable.PriorityQueue[Task]()

  var onTimeChange: () => Unit = () => {}

  def addRecurringTask(period: Long, initialOffset: Long = 0, taskName: String = "")(thunk: () => Unit): Unit = {
    val task = RecurringTask(internalTime + initialOffset, period, taskName, thunk)
    eventQueue.enqueue(task)
  }

  def addOneTimeTask(time: Long, taskName: String = "")(thunk: () => Unit): Unit = {
    val task = OneTimeTask(time, taskName, thunk)
    eventQueue.enqueue(task)
  }

  def hasNextTask: Boolean = {
    eventQueue.nonEmpty
  }

  def runNextTask(): Unit = {
    if(hasNextTask) {
      eventQueue.dequeue() match {
        case recurringTask: RecurringTask =>
          setTime(recurringTask.time)
          recurringTask.run()
          eventQueue.enqueue(recurringTask.copy(time = internalTime + recurringTask.period))
        case oneTimeTask: OneTimeTask =>
          setTime(oneTimeTask.time)
          oneTimeTask.run()
        case _ =>
          // do nothing
      }
    }
  }

  def runToTask(taskName: String): Unit = {
    if(eventQueue.nonEmpty) {
      val done = eventQueue.head.taskName == taskName
      runNextTask()
      if(! done) runToTask(taskName)
    }
  }

  def runUntil(time: Long): Unit = {
    while(eventQueue.nonEmpty && eventQueue.head.time <= time) {
      runNextTask()
    }
    setTime(time)
  }

  def incrementTime(increment: Long): Unit = {
    runUntil(internalTime + increment)
  }
}

object UTC {
  def apply(scaleName: String = "picoseconds"): UTC = new UTC(scaleName)
}


trait Task extends Ordered[Task] {
  def taskName: String
  def run(): Unit
  def time: Long
  override def compare(that: Task): Int = {
    if(this.time < that.time) {
      1
    }
    else if(this.time == that.time) {
      0
    }
    else {
      -1
    }
  }
}

case class OneTimeTask(time: Long, taskName: String, thunk: () => Unit) extends Task {
  def run(): Unit = {
    thunk()
  }
}

case class RecurringTask(time: Long, period: Long, taskName: String, thunk: () => Unit) extends Task {
  def run(): Unit = {
    thunk()
  }
}
