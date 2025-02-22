package kyo.test.render

import kyo.*
import kyo.test.*
import kyo.test.ExecutionEvent.SectionEnd
import kyo.test.ExecutionEvent.SectionStart
import kyo.test.ExecutionEvent.Test
import kyo.test.ExecutionEvent.TestStarted
import kyo.test.ExecutionEvent.TopLevelFlush
import kyo.test.render.ExecutionResult.ResultType
import kyo.test.render.ExecutionResult.Status
import kyo.test.render.LogLine.Fragment
import kyo.test.render.LogLine.Fragment.Style
import kyo.test.render.LogLine.Line
import kyo.test.render.LogLine.Message

trait ConsoleRenderer extends TestRenderer:
    private val tabSize = 2

    override def renderEvent(event: ExecutionEvent, includeCause: Boolean)(implicit
        trace: Trace
    ): Seq[ExecutionResult] =
        event match
            case _: TestStarted => Nil
            case SectionStart(labelsReversed, _, _) =>
                val depth = labelsReversed.length - 1
                labelsReversed.reverse match
                    case Nil => Seq.empty
                    case nonEmptyList =>
                        Seq(
                            ExecutionResult.withoutSummarySpecificOutput(
                                ResultType.Suite,
                                label = nonEmptyList.last,
                                // We no longer know if the suite has passed here, because the output is streamed
                                Status.Passed,
                                offset = depth,
                                List(TestAnnotationMap.empty), // TODO Examine all results to get this
                                lines = List(fr(nonEmptyList.last).toLine),
                                duration = None
                            )
                        )
                end match

            case Test(labelsReversed, results, annotations, _, _, _, _) =>
                val labels       = labelsReversed.reverse
                val initialDepth = labels.length - 1
                val (streamingOutput, summaryOutput) =
                    testCaseOutput(labels, results, includeCause, annotations)

                Seq(
                    ExecutionResult(
                        ResultType.Test,
                        labels.headOption.getOrElse(""),
                        results match
                            case Left(_) => Status.Failed
                            case Right(value: TestSuccess) =>
                                value match
                                    case TestSuccess.Succeeded(_) => Status.Passed
                                    case TestSuccess.Ignored(_) =>
                                        Status.Ignored
                        ,
                        initialDepth,
                        List(annotations),
                        streamingOutput,
                        summaryOutput,
                        duration = None
                    )
                )
            case runtimeFailure @ ExecutionEvent.RuntimeFailure(_, _, failure, _) =>
                val depth = event.labels.length
                failure match
                    case TestFailure.Assertion(result, annotations) =>
                        Seq(renderAssertFailure(result, runtimeFailure.labels, depth, annotations))
                    case TestFailure.Runtime(cause, annotations) =>
                        Seq(
                            renderRuntimeCause(
                                cause,
                                runtimeFailure.labels,
                                depth,
                                includeCause
                            )
                        )
                end match
            case SectionEnd(_, _, _) =>
                Nil
            case TopLevelFlush(_) =>
                Nil

    override protected def renderOutput(results: Seq[ExecutionResult])(implicit trace: Trace): Seq[String] =
        results.map { result =>
            val message = Message(result.streamingLines).intersperse(Line.fromString("\n"))

            val output = result.resultType match
                case ResultType.Suite =>
                    renderSuite(result.status, result.offset, message)
                case ResultType.Test =>
                    renderTest(result.status, result.offset, message)
                case ResultType.Other =>
                    Message(result.streamingLines)

            renderToStringLines(output).mkString
        }

    private def renderOutput(output: List[ConsoleIO]): Message =
        if output.isEmpty then
            Message.empty
        else
            Message(
                Line.fromString("          Console IO Produced by Test         ".red.underlined, 2) +:
                    output.map(s => Line.fromString("| ".red + renderConsoleIO(s), 2)) :+
                    Line.fromString("==============================================\n".red, 2)
            )

    private def renderConsoleIO(s: ConsoleIO) =
        s match
            case ConsoleIO.Input(line)  => line.magenta
            case ConsoleIO.Output(line) => line.yellow

    def renderForSummary(results: Seq[ExecutionResult], testAnnotationRenderer: TestAnnotationRenderer): Seq[String] =
        results.map { result =>
            val testOutput: List[ConsoleIO] = result.annotations.flatMap(_.get(TestAnnotation.output))
            val renderedAnnotations         = renderAnnotations(result.annotations, testAnnotationRenderer)
            val message = (Message(result.summaryLines) ++ renderedAnnotations ++ renderOutput(testOutput))
                .intersperse(Line.fromString("\n"))

            val output = result.resultType match
                case ResultType.Suite =>
                    renderSuite(result.status, result.offset, message)
                case ResultType.Test =>
                    renderTest(result.status, result.offset, message)
                case ResultType.Other =>
                    Message(result.streamingLines)

            renderToStringLines(output).mkString
        }

    private def renderFailure(
        label: String,
        offset: Int,
        details: TestTrace[Boolean],
        annotations: TestAnnotationMap
    ): Message =
        renderFailureLabel(label, offset) +: (renderAnnotations(
            List(annotations),
            TestAnnotationRenderer.default
        ) ++ renderAssertionResult(details, offset)) :+ Line.empty

    private def renderSuite(status: Status, offset: Int, message: Message): Message =
        status match
            case Status.Passed => withOffset(offset)(info("+") + sp) +: message
            case Status.Failed => withOffset(offset)(Line.empty) +: message
            case Status.Ignored =>
                withOffset(offset)(Line.empty) +: message :+ fr(" - " + TestAnnotation.ignored.identifier + " suite").toLine

    private def renderTest(status: Status, offset: Int, message: Message) =
        status match
            case Status.Passed  => withOffset(offset)(info("+") + sp) +: message
            case Status.Ignored => withOffset(offset)(warn("-") + sp) +: message
            case Status.Failed  => message

    def renderToStringLines(message: Message): Seq[String] =
        def renderFragment(f: Fragment): String =
            f.style match
                case Style.Default             => f.text
                case Style.Primary             => ConsoleUtils.blue(f.text)
                case Style.Warning             => ConsoleUtils.yellow(f.text)
                case Style.Error               => ConsoleUtils.red(f.text)
                case Style.Info                => ConsoleUtils.green(f.text)
                case Style.Detail              => ConsoleUtils.cyan(f.text)
                case Style.Dimmed              => ConsoleUtils.dim(f.text)
                case Style.Bold(fr)            => ConsoleUtils.bold(renderFragment(fr))
                case Style.Underlined(fr)      => ConsoleUtils.underlined(renderFragment(fr))
                case Style.Ansi(fr, ansiColor) => ConsoleUtils.ansi(ansiColor, renderFragment(fr))

        message.lines.map { line =>
            renderOffset(line.offset)(line.optimized.fragments.foldLeft("")((str, f) => str + renderFragment(f)))
        }
    end renderToStringLines

    private def renderAnnotations(
        annotations: List[TestAnnotationMap],
        annotationRenderer: TestAnnotationRenderer
    ): Message =
        annotations match
            case annotations :: ancestors =>
                val rendered = annotationRenderer.run(ancestors, annotations)
                if rendered.isEmpty then
                    Message.empty
                else
                    Message(rendered.mkString(" - ", ", ", ""))
                end if
            case Nil =>
                Message.empty

    private def renderOffset(n: Int)(s: String) =
        " " * (n * tabSize) + s

    import kyo.duration2DurationOps
    def renderSummary(summary: Summary): String =
        s"""${summary.success} tests passed. ${summary.fail} tests failed. ${summary.ignore} tests ignored.
       |${summary.failureDetails}
       |Executed in ${summary.interval.duration.render}
       |""".stripMargin

    def render(cause: Cause[?], labels: List[String]): Option[String] =
        cause match
            case _: Cause.Interrupt =>
                val renderedInterruption =
                    ConsoleRenderer.renderToStringLines(
                        Message(Seq(LogLine.Line(Vector(LogLine.Fragment(labels.mkString(" - "), Style.Error)))))
                    )

                Some("Interrupted during execution: " + renderedInterruption.mkString("\n"))
            case _ => None
end ConsoleRenderer
object ConsoleRenderer extends ConsoleRenderer
