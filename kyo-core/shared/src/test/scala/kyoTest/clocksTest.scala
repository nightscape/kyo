package kyoTest

import java.time.Instant
import kyo.*

class clocksTest extends KyoTest:

    object testClock extends Clock:

        var nows = List.empty[Instant]

        def now: Instant < IOs =
            IOs {
                val v = nows.head
                nows = nows.tail
                v
            }
    end testClock

    "now" in run {
        val instant = Instant.now()
        testClock.nows = List(instant)
        val io = Clocks.let(testClock)(Clocks.now)
        assert(IOs.run(io) == instant)
    }
end clocksTest
