package kyoTest

import kyo.core._
import kyo.tries._
import kyo.options._
import kyo.direct._
import kyo.direct
import kyo.ios._
import kyo.envs._
import kyo.concurrent.fibers._
import scala.util.Try
import kyo.consoles._
import kyo.lists._
import kyo.concurrent.refs._

class directTest extends KyoTest {

  "one run" in {
    val io = defer {
      val a = direct.run(IOs("hello"))
      a + " world"
    }
    assert(IOs.run(io) == "hello world")
  }

  "two runs" in {
    val io =
      defer {
        val a = direct.run(IOs("hello"))
        val b = direct.run(IOs("world"))
        a + " " + b
      }
    assert(IOs.run(io) == "hello world")
  }

  "two effects" in {
    val io: String > (IOs | Options) =
      defer {
        val a = direct.run(Options.get(Some("hello")))
        val b = direct.run(IOs("world"))
        a + " " + b
      }
    assert(IOs.run(io < Options) == Some("hello world"))
  }

  "if" in {
    var calls = List.empty[Int]
    val io: Boolean > IOs =
      defer {
        if (direct.run(IOs { calls :+= 1; true }))
          direct.run(IOs { calls :+= 2; true })
        else
          direct.run(IOs { calls :+= 3; true })
      }
    assert(IOs.run(io))
    assert(calls == List(1, 2))
  }

  "booleans" - {
    "&&" - {
      "plain" in {
        var calls = List.empty[Int]
        { calls :+= 1; true } && { calls :+= 2; true }
        assert(calls == List(1, 2))
      }
      "direct" in {
        var calls = List.empty[Int]
        val io: Boolean > IOs =
          defer {
            (direct.run(IOs { calls :+= 1; true }) && direct.run(IOs { calls :+= 2; true }))
          }
        assert(IOs.run(io))
        assert(calls == List(2, 1))
      }
    }
    "||" - {
      "plain" in {
        var calls = List.empty[Int]
        { calls :+= 1; true } || { calls :+= 2; true }
        assert(calls == List(1))
      }
      "direct" in {
        var calls = List.empty[Int]
        val io: Boolean > IOs =
          defer {
            (direct.run(IOs { calls :+= 1; true }) || direct.run(IOs { calls :+= 2; true }))
          }
        assert(IOs.run(io))
        assert(calls == List(2, 1))
      }
    }
  }

  "options" in {
    def test[T](opt: Option[T]) =
      assert(opt == defer(direct.run(opt > Options)) < Options)
    test(Some(1))
    test(None)
    test(Some("a"))
  }
  "tries" in {
    def test[T](t: Try[T]) =
      assert(t == defer(direct.run(t > Tries)) < Tries)
    test(Try(1))
    test(Try(throw new Exception("a")))
    test(Try("a"))
  }
  "consoles" in {
    object console extends Console {

      def printErr(s: => String): Unit > IOs = ???

      def println(s: => String): Unit > IOs = ???

      def print(s: => String): Unit > IOs = ???

      def readln: String > IOs = "hello"

      def printlnErr(s: => String): Unit > IOs = ???
    }
    val io: String > IOs = Consoles.run(console)(defer(direct.run(Consoles.readln)))
    assert(IOs.run(io) == "hello")
  }

  "kyo computations must be within a run block" in {
    assertDoesNotCompile("defer(IOs(1))")
    assertDoesNotCompile("""
      defer {
        val a = IOs(1)
        10
      }
    """)
    assertDoesNotCompile("""
      defer {
        val a = {
          val b = IOs(1)
          10
        }
        10
      }
    """)
  }
}