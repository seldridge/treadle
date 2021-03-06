// See LICENSE for license details.

package treadle.executable

import scala.collection.mutable


object RenderHelper {

  implicit class ExpressionHelper(val sc: StringContext) extends AnyVal {
    def expression(args: Any*): ExpressionView = {
      new ExpressionView(sc, args.toSeq)
    }
  }

}

class ExpressionView(val sc: StringContext, val args: Seq[Any])

class SymbolAtDepth(val symbol: Symbol, val displayDepth: Int, val lookBackDepth: Int)

object SymbolAtDepth {
  def apply(symbol: Symbol, displayDepth: Int, lookBackDepth: Int): SymbolAtDepth = {
    new SymbolAtDepth(symbol, displayDepth, lookBackDepth)
  }
}


/**
  * This class answers the question why does the given symbol have a particular value,
  * it shows all arguments of PrimOPs and should only show any symbols value once.
  * Muxes only show the expanded derivation of the branch taken
  * Display goes from top to bottom since it is usually the top value one wants
  * to see rendered last.
  *
  * @param dataStore        current state
  * @param symbolTable      the symbol table
  * @param expressionViews  expression information
  */
class ExpressionViewRenderer(
    dataStore: DataStore,
    symbolTable: SymbolTable,
    expressionViews: Map[Symbol, ExpressionView]
) {

  private def order(symbolAtDepth: SymbolAtDepth) = symbolAtDepth.displayDepth

  private val symbolsToDo = new mutable.PriorityQueue[SymbolAtDepth]()(Ordering.by(order))
  private val symbolsSeen = new mutable.HashSet[Symbol]()

  //scalastyle:off cyclomatic.complexity method.length
  private def renderInternal(currentOutputFormat: String = "d"): String = {
    val builder = new StringBuilder()

    def formatOutput(value: BigInt): String = {
      currentOutputFormat match {
        case "d" => value.toString
        case "h" | "x" => f"0x$value%x"
        case "b" => s"b${value.toString(2)}"
      }
    }

    def renderView(view: ExpressionView, displayDepth: Int, lookBackDepth: Int): String = {
      val builder = new StringBuilder()

      val sc = view.sc
      val args = view.args

      /**
        * If the current view is a Mux it would ordinarily show the derivation of all of
        * its arguments, to compact things we will mark the symbols associated with the
        * mux branch NOT taken as having been seen, so we won't pursue them
        */
      def checkForMux(): Unit = {
        if(sc.parts.head == "Mux(") {
          args.head match {
            case ev: ExpressionView =>
              ev.args.head match {
                case ms: Symbol =>
                  val arg = args.drop(if(dataStore(ms) > 0) 2 else 1).head
                  arg match {
                    case ev2: ExpressionView =>
                      ev2.args.head match {
                        case sss: Symbol =>
                          symbolsSeen += sss
                        case _ =>
                      }
                    case _ =>
                  }
                case value: Big =>
                  val arg = args.drop(if(value > 0) 2 else 1).head
                  arg match {
                    case ev2: ExpressionView =>
                      ev2.args.head match {
                        case sss: Symbol =>
                          symbolsSeen += sss
                        case _ =>
                      }
                    case _ =>
                  }
                case x =>
                  x.toString
              }
            case ms: Symbol =>
              val arg = args.drop(if(dataStore(ms) > 0) 2 else 1).head
              arg match {
                case ev2: ExpressionView =>
                  ev2.args.head match {
                    case sss: Symbol =>
                      symbolsSeen += sss
                    case _ =>
                  }
                case _ =>
              }
            case x =>
              x.toString

          }
        }
      }

      checkForMux()

      builder ++= sc.parts.head
      val argStrings = args.map {
        case symbol: Symbol =>
          if(! (
                  symbolTable.isRegister(symbol.name) ||
                          symbolTable.inputPortsNames.contains(symbol.name) ||
                          symbolsSeen.contains(symbol)
                  )) {
            symbolsToDo.enqueue(SymbolAtDepth(symbol, displayDepth + 1, lookBackDepth))
          }

          val value = symbol.normalize(dataStore.earlierValue(symbol, lookBackDepth))

          val string = s"${symbol.name} <= " +
                  (if(lookBackDepth > 0) Console.RED else "") +
                  s"${formatOutput(value)}" +
                  (if(lookBackDepth > 0) Console.RESET else "")
          string

        case subView: ExpressionView =>
          renderView(subView, displayDepth + 1, lookBackDepth)

        case other => other.toString
      }

      argStrings.zip(sc.parts.tail).foreach { case (s1, s2) =>
        builder ++= s1
        builder ++= s2
      }
      builder.toString()
    }

    while (symbolsToDo.nonEmpty) {
      val symbolAtDepth = symbolsToDo.dequeue()
      val symbol = symbolAtDepth.symbol

      if(! symbolsSeen.contains(symbol)) {
        symbolsSeen += symbol
        val lookBackDepth = symbolAtDepth.lookBackDepth
        val currentValue = symbol.normalize(dataStore.earlierValue(symbol, lookBackDepth))
        val adjustedLookBackDepth = lookBackDepth + (if (symbolTable.isRegister(symbol.name)) 1 else 0)

        expressionViews.get(symbol).foreach { view =>
          builder ++= "  " * symbolAtDepth.displayDepth
          builder ++= s"${symbol.name} <= "
          if (lookBackDepth > 0) {
            builder ++= Console.RED
          }
          builder ++= s"${formatOutput(currentValue)} : "
          if (lookBackDepth > 0) {
            builder ++= Console.RESET
          }
          builder ++= renderView(view, symbolAtDepth.displayDepth, adjustedLookBackDepth)
          if (adjustedLookBackDepth > lookBackDepth) {
            builder ++= s" :  Values in red are from $adjustedLookBackDepth cycle"
            builder ++= (if (adjustedLookBackDepth > 1) "s before" else " before")
          }
          builder ++= "\n"
        }
      }
    }

    // This reverses the top to bottom display order, leaves selected rendered symbol and end of output
    // making it easiser to see in repl mode
    val result = builder.toString().split("""\n""").reverse.mkString("\n")
    result
  }

  def render(symbol: Symbol, lookBackDepth: Int = 0, outputFormat: String = "d"): String = {
    symbolsToDo.enqueue(SymbolAtDepth(symbol, 0, lookBackDepth))

    renderInternal(outputFormat)
  }
}





