package kyo

type Options = Aborts[Option[Nothing]]

object Options:

    private val aborts = Aborts[Option[Nothing]]
    private val none   = aborts.fail(None)

    val empty: Nothing < Options = none

    def apply[T](v: T): T < Options =
        if v == null then
            empty
        else
            v

    def get[T, S](v: Option[T] < S): T < (Options & S) =
        v.map {
            case Some(v) => v
            case None    => empty
        }

    def getOrElse[T, S1, S2](v: Option[T] < S1, default: => T < S2): T < (S1 & S2) =
        v.map {
            case None    => default
            case Some(v) => v
        }

    def run[T, S](v: T < (Options & S))(using f: Flat[T < (Options & S)]): Option[T] < S =
        aborts.run[T, S](v).map {
            case Left(e)  => None
            case Right(v) => Some(v)
        }

    def orElse[T, S](l: (T < (Options & S))*)(implicit
        f: Flat[T < (Options & S)]
    ): T < (Options & S) =
        l.toList match
            case Nil => Options.empty
            case h :: t =>
                run[T, S](h).map {
                    case None => orElse[T, S](t*)
                    case v    => get(v)
                }

    def layer[Se](onEmpty: => Nothing < Se): Layer[Options, Se] =
        new Layer[Options, Se]:
            override def run[T, S](effect: T < (Options & S))(implicit
                fl: Flat[T < (Options & S)]
            ): T < (S & Se) =
                Options.run[T, S](effect).map {
                    case None    => onEmpty
                    case Some(t) => t
                }
end Options
