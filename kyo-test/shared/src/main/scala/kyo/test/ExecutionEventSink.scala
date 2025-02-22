package kyo.test

import kyo.*
import kyo.test.ExecutionEvent
import kyo.test.ExecutionEventPrinter
import kyo.test.ReporterEventRenderer
// These are assumed to be defined in the kyo-test project:
import kyo.test.Summary
import kyo.test.TestOutput

trait ExecutionEventSink:
    def getSummary: Summary < IO
    def process(event: ExecutionEvent): Unit < IO
end ExecutionEventSink

object ExecutionEventSink:
    def getSummary: Summary < (Env[ExecutionEventSink] & IO) =
        service[ExecutionEventSink].flatMap(_.getSummary)

    def process(event: ExecutionEvent): Unit < (Env[ExecutionEventSink] & IO) =
        service[ExecutionEventSink].flatMap(_.process(event))

    def ExecutionEventSinkLive(testOutput: TestOutput): ExecutionEventSink < IO =
        for
            summary <- Var.make(Summary.empty)
        yield new ExecutionEventSink:
            override def process(event: ExecutionEvent): Unit < IO =
                summary.update(_.add(event)) *> testOutput.print(event)
            override def getSummary: Summary < IO = summary.get

    def live(console: Console, eventRenderer: ReporterEventRenderer): Layer[Any, Nothing, ExecutionEventSink] =
        Layer.make[ExecutionEventSink](
            ExecutionEventPrinter.live(console, eventRenderer),
            TestOutput.live,
            Layer.fromEffect(
                for
                    testOutput <- service[TestOutput]
                    sink       <- ExecutionEventSinkLive(testOutput)
                yield sink
            )
        )

    val live: Layer[TestOutput, Nothing, ExecutionEventSink] =
        Layer.fromEffect(
            for
                testOutput <- service[TestOutput]
                sink       <- ExecutionEventSinkLive(testOutput)
            yield sink
        )

    val silent: ULayer[ExecutionEventSink] =
        Layer.succeed(
            new ExecutionEventSink:
                override def getSummary: Summary < IO                  = suspend(Summary.empty)
                override def process(event: ExecutionEvent): Unit < IO = suspend(())
        )
end ExecutionEventSink
