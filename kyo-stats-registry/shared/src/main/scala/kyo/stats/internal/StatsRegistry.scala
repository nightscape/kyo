package kyo.stats.internal

import java.lang.ref.WeakReference
import java.util.Collections
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder
import org.HdrHistogram.ConcurrentDoubleHistogram as HdrHistogram
import scala.annotation.tailrec

object StatsRegistry {

    import internal.*

    def scope(path: String*): Scope = new Scope(path.reverse.toList)

    class Scope private[kyo] (reversePath: List[String]) {

        def path: List[String] = reversePath.reverse

        def scope(p: String*) = new Scope(p.reverse.toList ::: reversePath)

        def counter(name: String, description: String = "empty"): UnsafeCounter =
            counters.get(name :: reversePath, description, new UnsafeCounter())

        def histogram(
            name: String,
            description: String = "empty",
            numberOfSignificantValueDigits: Int = 4,
            highestToLowestValueRatio: Long = 2
        ): UnsafeHistogram =
            histograms.get(name :: reversePath, description, new UnsafeHistogram(numberOfSignificantValueDigits, highestToLowestValueRatio))

        def gauge(name: String, description: String = "empty")(run: => Double): UnsafeGauge =
            gauges.get(name :: reversePath, description, new UnsafeGauge(() => run))

        def counterGauge(name: String, description: String = "empty")(run: => Long): UnsafeCounterGauge =
            counterGauges.get(name :: reversePath, description, new UnsafeCounterGauge(() => run))
    }

    def addExporter(exporter: StatsExporter) =
        exporters.add(exporter)

    def removeExporter(exporter: StatsExporter) =
        exporters.remove(exporter)

    private[kyo] object internal extends StatsRefresh {

        val counters       = new Store[UnsafeCounter]
        val histograms     = new Store[UnsafeHistogram]
        val gauges         = new Store[UnsafeGauge]
        val counterGauges  = new Store[UnsafeCounterGauge]
        lazy val exporters = Collections.newSetFromMap(new ConcurrentHashMap[StatsExporter, java.lang.Boolean])

        class Store[T] {
            val map = new ConcurrentHashMap[List[String], (WeakReference[T], String)]

            @tailrec final def get(reversePath: List[String], description: String, init: => T): T = {
                val path  = reversePath.reverse
                val ref   = map.computeIfAbsent(path, _ => (new WeakReference(init), description))._1
                val value = ref.get()
                if (value == null) {
                    map.remove(path)
                    get(reversePath, description, init)
                } else {
                    value
                }
            }
            def gc(): Unit =
                map.forEach { (path, ref) =>
                    if (ref._1.get() == null) {
                        map.remove(path)
                        ()
                    }
                }
        }

        def refresh() = {
            counters.gc()
            histograms.gc()
            gauges.gc()
            if (!exporters.isEmpty()) {
                counters.map.forEach { (path, tuple) =>
                    val (ref, desc) = tuple
                    val counter     = ref.get()
                    if (counter != null) {
                        val delta = counter.delta()
                        exporters.forEach(_.counter(path, desc, delta))
                    }
                }
                histograms.map.forEach { (path, tuple) =>
                    val (ref, desc) = tuple
                    val histogram   = ref.get()
                    if (histogram != null) {
                        val summary = histogram.summary()
                        exporters.forEach(_.histogram(path, desc, summary))
                    }
                }
                gauges.map.forEach { (path, tuple) =>
                    val (ref, desc) = tuple
                    val gauge       = ref.get()
                    if (gauge != null) {
                        val current = gauge.collect()
                        exporters.forEach(_.gauge(path, desc, current))
                    }
                }
                counterGauges.map.forEach { (path, tuple) =>
                    val (ref, desc) = tuple
                    val counter     = ref.get()
                    if (counter != null) {
                        val delta = counter.delta()
                        exporters.forEach(_.counter(path, desc, delta))
                    }
                }
            }
        }

    }
}
