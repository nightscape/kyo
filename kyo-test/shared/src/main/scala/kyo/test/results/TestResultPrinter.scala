package kyo.test.results

import kyo.*
import kyo.test.ExecutionEvent

trait ResultPrinter:
    def print[E](event: ExecutionEvent.Test[E]): Unit < Env[Any] & Abort[Nothing]

object ResultPrinter:
    val json: ZLayer[Any, Nothing, ResultPrinter] = ResultPrinterJson.live
