package kyo.test.laws

import kyo.*
import kyo.test.Gen
import kyo.test.TestResult
import kyo.test.check

/** `ZLaws[CapsF, Caps, R]` describes a set of laws that a parameterized type `F[A]` with capabilities `CapsF` is expected to satisfy with
  * respect to all types `A` that have capabilities `Caps`. Laws can be run by providing a `GenF` that is capable of generating `F[A]`
  * values given a generator of `A` values and a generator of values of some type `A`. Laws can be combined using `+` to produce a set of
  * laws that require both sets of laws to be satisfied.
  */
object ZLawsF:

    /** `ZLawsF` for covariant type constructors.
      */
    abstract class Covariant[-CapsF[_[+_]], -Caps[_], -R]:
        self =>

        /** Test that values of type `F[+_]` satisfy the laws using the specified function to construct a generator of `F[A]` values given a
          * generator of `A` values.
          */
        def run[R1 <: R, F[+_]: CapsF, A: Caps](
            genF: GenF[R1, F],
            gen: Gen[R1, A]
        )(implicit trace: Trace): TestResult < Env[R1] & Abort[Nothing]

        /** Combine these laws with the specified laws to produce a set of laws that require both sets of laws to be satisfied.
          */
        def +[CapsF1[x[+_]] <: CapsF[x], Caps1[x] <: Caps[x], R1 <: R](
            that: Covariant[CapsF1, Caps1, R1]
        ): Covariant[CapsF1, Caps1, R1] =
            Covariant.Both(self, that)
    end Covariant

    object Covariant:

        final private case class Both[-CapsF[_[+_]], -Caps[_], -R](
            left: Covariant[CapsF, Caps, R],
            right: Covariant[CapsF, Caps, R]
        ) extends Covariant[CapsF, Caps, R]:
            final def run[R1 <: R, F[+_]: CapsF, A: Caps](
                genF: GenF[R1, F],
                gen: Gen[R1, A]
            )(implicit trace: Trace): TestResult < Env[R1] & Abort[Nothing] =
                left.run(genF, gen).zipWith(right.run(genF, gen))(_ && _)
        end Both

        /** Constructs a law from a pure function taking one parameterized value and two functions that can be composed.
          */
        abstract class ComposeLaw[-CapsF[_[+_]], -Caps[_]](label: String) extends Covariant[CapsF, Caps, Any]:
            self =>
            def apply[F[+_]: CapsF, A: Caps, B: Caps, C: Caps](fa: F[A], f: A => B, g: B => C): TestResult
            final def run[R, F[+_]: CapsF, A: Caps](
                genF: GenF[R, F],
                gen: Gen[R, A]
            )(implicit trace: Trace): URIO[R, TestResult] =
                check(genF(gen), Gen.function(gen), Gen.function(gen))(apply(_, _, _).label(label))
        end ComposeLaw

        /** Constructs a law from a parameterized value wrapped in two additional layers that can be flattened.
          */
        abstract class FlattenLaw[-CapsF[_[+_]], -Caps[_]](label: String) extends Covariant[CapsF, Caps, Any]:
            self =>
            def apply[F[+_]: CapsF, A: Caps](fffa: F[F[F[A]]]): TestResult
            final def run[R, F[+_]: CapsF, A: Caps](
                genF: GenF[R, F],
                gen: Gen[R, A]
            )(implicit trace: Trace): URIO[R, TestResult] =
                check(genF(genF(genF(gen))))(apply(_).label(label))
        end FlattenLaw

        /** Constructs a law from a pure function taking a single parameter.
          */
        abstract class Law1[-CapsF[_[+_]], -Caps[_]](label: String) extends Covariant[CapsF, Caps, Any]:
            self =>
            def apply[F[+_]: CapsF, A: Caps](fa: F[A]): TestResult
            final def run[R, F[+_]: CapsF, A: Caps](
                genF: GenF[R, F],
                gen: Gen[R, A]
            )(implicit trace: Trace): URIO[R, TestResult] =
                check(genF(gen))(apply(_).label(label))
        end Law1

        /** Constructs a law from an effectual function taking a single parameter.
          */
        abstract class Law1Kyo[-CapsF[_[+_]], -Caps[_], -R](label: String) extends Covariant[CapsF, Caps, R]:
            self =>
            def apply[F[+_]: CapsF, A: Caps](fa: F[A]): URIO[R, TestResult]
            final def run[R1 <: R, F[+_]: CapsF, A: Caps](
                genF: GenF[R1, F],
                gen: Gen[R1, A]
            )(implicit trace: Trace): TestResult < Env[R1] & Abort[Nothing] =
                check(genF(gen))(apply(_).map(_.label(label)))
        end Law1Kyo

        /** Constructs a law from a pure function taking two parameters.
          */
        abstract class Law2[-CapsF[_[+_]], -Caps[_]](label: String) extends Covariant[CapsF, Caps, Any]:
            self =>
            def apply[F[+_]: CapsF, A: Caps, B: Caps](fa: F[A], fb: F[B]): TestResult
            final def run[R, F[+_]: CapsF, A: Caps](
                genF: GenF[R, F],
                gen: Gen[R, A]
            )(implicit trace: Trace): URIO[R, TestResult] =
                check(genF(gen), genF(gen))(apply(_, _).label(label))
        end Law2

        /** Constructs a law from an effectual function taking two parameters.
          */
        abstract class Law2Kyo[-CapsF[_[+_]], -Caps[_], -R](label: String) extends Covariant[CapsF, Caps, R]:
            self =>
            def apply[F[+_]: CapsF, A: Caps, B: Caps](fa: F[A], fb: F[B]): URIO[R, TestResult]
            final def run[R1 <: R, F[+_]: CapsF, A: Caps](
                genF: GenF[R1, F],
                gen: Gen[R1, A]
            )(implicit trace: Trace): TestResult < Env[R1] & Abort[Nothing] =
                check(genF(gen), genF(gen))(apply(_, _).map(_.label(label)))
        end Law2Kyo

        /** Constructs a law from a pure function taking three parameters.
          */
        abstract class Law3[-CapsF[_[+_]], -Caps[_]](label: String) extends Covariant[CapsF, Caps, Any]:
            self =>
            def apply[F[+_]: CapsF, A: Caps, B: Caps, C: Caps](fa: F[A], fb: F[B], fc: F[C]): TestResult
            final def run[R, F[+_]: CapsF, A: Caps](
                genF: GenF[R, F],
                gen: Gen[R, A]
            )(implicit trace: Trace): URIO[R, TestResult] =
                check(genF(gen), genF(gen), genF(gen))(apply(_, _, _).label(label))
        end Law3

        /** Constructs a law from an effectual function taking three parameters.
          */
        abstract class Law3Kyo[-CapsF[_[+_]], -Caps[_], -R](label: String) extends Covariant[CapsF, Caps, R]:
            self =>
            def apply[F[+_]: CapsF, A: Caps, B: Caps, C: Caps](fa: F[A], fb: F[B], fc: F[C]): URIO[R, TestResult]
            final def run[R1 <: R, F[+_]: CapsF, A: Caps](
                genF: GenF[R1, F],
                gen: Gen[R1, A]
            )(implicit trace: Trace): TestResult < Env[R1] & Abort[Nothing] =
                check(genF(gen), genF(gen), genF(gen))(apply(_, _, _).map(_.label(label)))
        end Law3Kyo
    end Covariant

    /** `ZLawsF` for contravariant type constructors.
      */
    abstract class Contravariant[-CapsF[_[-_]], -Caps[_], -R]:
        self =>

        /** Test that values of type `F[+_]` satisfy the laws using the specified function to construct a generator of `F[A]` values given a
          * generator of `A` values.
          */
        def run[R1 <: R, F[-_]: CapsF, A: Caps](
            genF: GenF[R1, F],
            gen: Gen[R1, A]
        )(implicit trace: Trace): TestResult < Env[R1] & Abort[Nothing]

        /** Combine these laws with the specified laws to produce a set of laws that require both sets of laws to be satisfied.
          */
        def +[CapsF1[x[-_]] <: CapsF[x], Caps1[x] <: Caps[x], R1 <: R](
            that: Contravariant[CapsF1, Caps1, R1]
        ): Contravariant[CapsF1, Caps1, R1] =
            Contravariant.Both(self, that)
    end Contravariant

    object Contravariant:

        final private case class Both[-CapsF[_[-_]], -Caps[_], -R](
            left: Contravariant[CapsF, Caps, R],
            right: Contravariant[CapsF, Caps, R]
        ) extends Contravariant[CapsF, Caps, R]:
            final def run[R1 <: R, F[-_]: CapsF, A: Caps](
                genF: GenF[R1, F],
                gen: Gen[R1, A]
            )(implicit trace: Trace): TestResult < Env[R1] & Abort[Nothing] =
                left.run(genF, gen).zipWith(right.run(genF, gen))(_ && _)
        end Both

        /** Constructs a law from a pure function taking one parameterized value and two functions that can be composed.
          */
        abstract class ComposeLaw[-CapsF[_[-_]], -Caps[_]](label: String) extends Contravariant[CapsF, Caps, Any]:
            self =>
            def apply[F[-_]: CapsF, A: Caps, B: Caps, C: Caps](fa: F[A], f: B => A, g: C => B): TestResult
            final def run[R, F[-_]: CapsF, A: Caps](
                genF: GenF[R, F],
                gen: Gen[R, A]
            )(implicit trace: Trace): URIO[R, TestResult] =
                check(genF(gen), Gen.function[R, A, A](gen), Gen.function[R, A, A](gen))(apply(_, _, _).label(label))
        end ComposeLaw

        /** Constructs a law from a pure function taking a single parameter.
          */
        abstract class Law1[-CapsF[_[-_]], -Caps[_]](label: String) extends Contravariant[CapsF, Caps, Any]:
            self =>
            def apply[F[-_]: CapsF, A: Caps](fa: F[A]): TestResult
            final def run[R, F[-_]: CapsF, A: Caps](
                genF: GenF[R, F],
                gen: Gen[R, A]
            )(implicit trace: Trace): URIO[R, TestResult] =
                check(genF(gen))(apply(_).label(label))
        end Law1

        /** Constructs a law from an effectual function taking a single parameter.
          */
        abstract class Law1Kyo[-CapsF[_[-_]], -Caps[_], -R](label: String) extends Contravariant[CapsF, Caps, R]:
            self =>
            def apply[F[-_]: CapsF, A: Caps](fa: F[A]): URIO[R, TestResult]
            final def run[R1 <: R, F[-_]: CapsF, A: Caps](
                genF: GenF[R1, F],
                gen: Gen[R1, A]
            )(implicit trace: Trace): TestResult < Env[R1] & Abort[Nothing] =
                check(genF(gen))(apply(_).map(_.label(label)))
        end Law1Kyo

        /** Constructs a law from a pure function taking two parameters.
          */
        abstract class Law2[-CapsF[_[-_]], -Caps[_]](label: String) extends Contravariant[CapsF, Caps, Any]:
            self =>
            def apply[F[-_]: CapsF, A: Caps, B: Caps](fa: F[A], fb: F[B]): TestResult
            final def run[R, F[-_]: CapsF, A: Caps](
                genF: GenF[R, F],
                gen: Gen[R, A]
            )(implicit trace: Trace): URIO[R, TestResult] =
                check(genF(gen), genF(gen))(apply(_, _).label(label))
        end Law2

        /** Constructs a law from an effectual function taking two parameters.
          */
        abstract class Law2Kyo[-CapsF[_[-_]], -Caps[_], -R](label: String) extends Contravariant[CapsF, Caps, R]:
            self =>
            def apply[F[-_]: CapsF, A: Caps, B: Caps](fa: F[A], fb: F[B]): URIO[R, TestResult]
            final def run[R1 <: R, F[-_]: CapsF, A: Caps](
                genF: GenF[R1, F],
                gen: Gen[R1, A]
            )(implicit trace: Trace): TestResult < Env[R1] & Abort[Nothing] =
                check(genF(gen), genF(gen))(apply(_, _).map(_.label(label)))
        end Law2Kyo

        /** Constructs a law from a pure function taking three parameters.
          */
        abstract class Law3[-CapsF[_[-_]], -Caps[_]](label: String) extends Contravariant[CapsF, Caps, Any]:
            self =>
            def apply[F[-_]: CapsF, A: Caps, B: Caps, C: Caps](fa: F[A], fb: F[B], fc: F[C]): TestResult
            final def run[R, F[-_]: CapsF, A: Caps](
                genF: GenF[R, F],
                gen: Gen[R, A]
            )(implicit trace: Trace): URIO[R, TestResult] =
                check(genF(gen), genF(gen), genF(gen))(apply(_, _, _).label(label))
        end Law3

        /** Constructs a law from an effectual function taking three parameters.
          */
        abstract class Law3Kyo[-CapsF[_[-_]], -Caps[_], -R](label: String) extends Contravariant[CapsF, Caps, R]:
            self =>
            def apply[F[-_]: CapsF, A: Caps, B: Caps, C: Caps](fa: F[A], fb: F[B], fc: F[C]): URIO[R, TestResult]
            final def run[R1 <: R, F[-_]: CapsF, A: Caps](
                genF: GenF[R1, F],
                gen: Gen[R1, A]
            )(implicit trace: Trace): TestResult < Env[R1] & Abort[Nothing] =
                check(genF(gen), genF(gen), genF(gen))(apply(_, _, _).map(_.label(label)))
        end Law3Kyo
    end Contravariant

    /** `ZLawsF` for invariant type constructors.
      */
    abstract class Invariant[-CapsF[_[_]], -Caps[_], -R]:
        self =>

        /** Test that values of type `F[+_]` satisfy the laws using the specified function to construct a generator of `F[A]` values given a
          * generator of `A` values.
          */
        def run[R1 <: R, F[_]: CapsF, A: Caps](
            genF: GenF[R1, F],
            gen: Gen[R1, A]
        )(implicit trace: Trace): TestResult < Env[R1] & Abort[Nothing]

        /** Combine these laws with the specified laws to produce a set of laws that require both sets of laws to be satisfied.
          */
        def +[CapsF1[x[_]] <: CapsF[x], Caps1[x] <: Caps[x], R1 <: R](
            that: Invariant[CapsF1, Caps1, R1]
        ): Invariant[CapsF1, Caps1, R1] =
            Invariant.Both(self, that)
    end Invariant

    object Invariant:

        final private case class Both[-CapsF[_[_]], -Caps[_], -R](
            left: Invariant[CapsF, Caps, R],
            right: Invariant[CapsF, Caps, R]
        ) extends Invariant[CapsF, Caps, R]:
            final def run[R1 <: R, F[_]: CapsF, A: Caps](
                genF: GenF[R1, F],
                gen: Gen[R1, A]
            )(implicit trace: Trace): TestResult < Env[R1] & Abort[Nothing] =
                left.run(genF, gen).zipWith(right.run(genF, gen))(_ && _)
        end Both

        /** Constructs a law from a pure function taking a single parameter.
          */
        abstract class Law1[-CapsF[_[_]], -Caps[_]](label: String) extends Invariant[CapsF, Caps, Any]:
            self =>
            def apply[F[_]: CapsF, A: Caps](fa: F[A]): TestResult
            final def run[R, F[_]: CapsF, A: Caps](genF: GenF[R, F], gen: Gen[R, A])(implicit
                trace: Trace
            ): URIO[R, TestResult] =
                check(genF(gen))(apply(_).label(label))
        end Law1

        /** Constructs a law from an effectual function taking a single parameter.
          */
        abstract class Law1Kyo[-CapsF[_[_]], -Caps[_], -R](label: String) extends Invariant[CapsF, Caps, R]:
            self =>
            def apply[F[_]: CapsF, A: Caps](fa: F[A]): URIO[R, TestResult]
            final def run[R1 <: R, F[_]: CapsF, A: Caps](
                genF: GenF[R1, F],
                gen: Gen[R1, A]
            )(implicit trace: Trace): TestResult < Env[R1] & Abort[Nothing] =
                check(genF(gen))(apply(_).map(_.label(label)))
        end Law1Kyo

        /** Constructs a law from a pure function taking two parameters.
          */
        abstract class Law2[-CapsF[_[_]], -Caps[_]](label: String) extends Invariant[CapsF, Caps, Any]:
            self =>
            def apply[F[_]: CapsF, A: Caps, B: Caps](fa: F[A], fb: F[B]): TestResult
            final def run[R, F[_]: CapsF, A: Caps](genF: GenF[R, F], gen: Gen[R, A])(implicit
                trace: Trace
            ): URIO[R, TestResult] =
                check(genF(gen), genF(gen))(apply(_, _).label(label))
        end Law2

        /** Constructs a law from an effectual function taking two parameters.
          */
        abstract class Law2Kyo[-CapsF[_[_]], -Caps[_], -R](label: String) extends Invariant[CapsF, Caps, R]:
            self =>
            def apply[F[_]: CapsF, A: Caps, B: Caps](fa: F[A], fb: F[B]): URIO[R, TestResult]
            final def run[R1 <: R, F[_]: CapsF, A: Caps](
                genF: GenF[R1, F],
                gen: Gen[R1, A]
            )(implicit trace: Trace): TestResult < Env[R1] & Abort[Nothing] =
                check(genF(gen), genF(gen))(apply(_, _).map(_.label(label)))
        end Law2Kyo

        /** Constructs a law from a pure function taking three parameters.
          */
        abstract class Law3[-CapsF[_[_]], -Caps[_]](label: String) extends Invariant[CapsF, Caps, Any]:
            self =>
            def apply[F[_]: CapsF, A: Caps, B: Caps, C: Caps](fa: F[A], fb: F[B], fc: F[C]): TestResult
            final def run[R, F[_]: CapsF, A: Caps](genF: GenF[R, F], gen: Gen[R, A])(implicit
                trace: Trace
            ): URIO[R, TestResult] =
                check(genF(gen), genF(gen), genF(gen))(apply(_, _, _).label(label))
        end Law3

        /** Constructs a law from an effectual function taking three parameters.
          */
        abstract class Law3Kyo[-CapsF[_[_]], -Caps[_], -R](label: String) extends Invariant[CapsF, Caps, R]:
            self =>
            def apply[F[_]: CapsF, A: Caps, B: Caps, C: Caps](fa: F[A], fb: F[B], fc: F[C]): URIO[R, TestResult]
            final def run[R1 <: R, F[_]: CapsF, A: Caps](
                genF: GenF[R1, F],
                gen: Gen[R1, A]
            )(implicit trace: Trace): TestResult < Env[R1] & Abort[Nothing] =
                check(genF(gen), genF(gen), genF(gen))(apply(_, _, _).map(_.label(label)))
        end Law3Kyo
    end Invariant
end ZLawsF
