/*
 * Copyright 2019-2024 John A. De Goes and the Kyo Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kyo.test
// [Converted] This file has been partially converted from zio-test to Kyo effect system.
// All Kyo types (e.g., Kyo, UIO) have been replaced with Kyo effect types. Verify compatibility of macros.

import kyo.*
import scala.annotation.tailrec
import scala.compiletime.testing.Error
import scala.compiletime.testing.typeCheckErrors

trait CompileVariants:

    /** Returns either `Right` if the specified string type checks as valid Scala code or `Left` with an error message otherwise. Dies with
      * a runtime exception if specified string cannot be parsed or is not a known value at compile time.
      */
    inline def typeCheck(inline code: String): Unit < Env[Any] & Abort[Throwable] =
        try failWith(typeCheckErrors(code))
        catch
            case cause: Throwable =>
                // [Converted] Replaced Kyo.panic with Abort.fail. Manual conversion may be needed.
                Abort.fail(new RuntimeException("Compilation failed", cause))

    private def failWith(errors: List[Error])(using trace: Frame): Unit < Env[Any] & Abort[Throwable] =
        if errors.isEmpty then
            Kyo.pure(())
        else
            // [Converted] In Kyo, this was Kyo.left(...); manual conversion may be needed.
            Abort.fail(errors.iterator.map(_.message).mkString("\n"))

    inline def assertTrue(inline exprs: => Boolean*): TestResult =
        ${ SmartAssertMacros.smartAssert('exprs) } // [Converted] Verify macro compatibility with Kyo.

    inline def assert[A](inline value: => A)(
        inline assertion: Assertion[A]
    )(implicit trace: Trace, position: FramePosition): TestResult =
        ${ Macros.assert_impl('value)('assertion, 'trace, 'position) } // [Converted] Verify macro compatibility with Kyo.

    inline def assertKyo[R, E, A](
        inline effect: A < Env[R] & Abort[E]
    )(inline assertion: Assertion[A])(implicit trace: Trace, position: FramePosition): TestResult < Env[R] & Abort[E] =
        ${ Macros.assertKyo_impl('effect)('assertion, 'trace, 'position) }

    private[kyo] inline def showExpression[A](inline value: => A): String = ${ Macros.showExpression_impl('value) }
end CompileVariants

/** Proxy methods to call package private methods from the macro
  */
object CompileVariants:

    def assertProxy[A](value: => A, expression: String, assertionCode: String)(
        assertion: Assertion[A]
    )(implicit trace: Trace, position: Frame.Position): TestResult =
        // [Converted] Replaced zio.test.assertImpl with kyo.test.assertImpl. Verify macro compatibility.
        kyo.test.assertImpl(value, Some(expression), Some(assertionCode))(assertion)

    def assertKyoProxy[R, E, A](effect: A < Env[R] & Abort[E], expression: String, assertionCode: String)(
        assertion: Assertion[A]
    )(implicit trace: Trace, position: Frame.Position): TestResult < Env[R] & Abort[E] =
        // [Converted] Replaced zio.test.assertKyoImpl with kyo.test.assertKyoImpl. Verify macro compatibility.
        kyo.test.assertKyoImpl(effect, Some(expression), Some(assertionCode))(assertion)
end CompileVariants
