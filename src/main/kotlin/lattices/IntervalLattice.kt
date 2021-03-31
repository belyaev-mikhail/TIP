package lattices

import jdk.vm.ci.sparc.SPARC.l2
import java.lang.IllegalArgumentException
import kotlin.math.sign

typealias Element = Pair<IntervalLattice.Num, IntervalLattice.Num>

/**
 * The interval lattice.
 */
class IntervalLattice : Lattice<Any>, LatticeOps<Element> {

    /**
     * The element of the IntervalLattice.
     * An interval of the form (x, y) ranges from x to y (x < y), x and y included.
     * The interval (PInf, MInf) is the canonical empty interval, i.e. the bottom element.
     * The interval (MInf, PInf) is the top element.
     */

    public val FullInterval = Pair(MInf, PInf)

    val EmptyInterval = Pair(PInf, MInf)

    fun Int.toNum(): IntNum = IntNum(this)

    override val bottom = EmptyInterval

    override fun lub(x: Any, y: Any): Any = when {
        x == FullInterval -> FullInterval
        x == EmptyInterval -> y
        x is Pair<*, *> && y is Pair<*, *> -> when {
            x.first is MInf && y.second is PInf -> FullInterval
            x.first is MInf && x.second is IntNum && y.second is IntNum ->
                Pair(MInf, IntNum((x.second as IntNum).i.coerceAtLeast((y.second as IntNum).i)))
            x.first is IntNum && x.second is PInf && y.first is IntNum ->
                Pair(IntNum(kotlin.math.min((x.first as IntNum).i, (y.first as IntNum).i)), PInf)
            x.first is IntNum && x.second is IntNum && y.first is IntNum && y.second is IntNum ->
                Pair(
                    IntNum((x.first as IntNum).i.coerceAtMost((y.first as IntNum).i)),
                    IntNum((x.second as IntNum).i.coerceAtLeast((y.second as IntNum).i))
                )
            else -> lub(y, x)
        }
        else -> lub(y, x)
    }

    /**
     * A Num is an int, +infinity, or -infinity.
     */
    interface Num : Comparable<Num> {
        override fun compareTo(other: Num): Int = when {
            this == other -> 0
            this is IntNum && other is IntNum -> this.i - other.i
            this is MInf -> -1
            other is PInf -> -1
            this is PInf -> 1
            other is MInf -> 1
            else -> throw IllegalArgumentException()
        }
    }

    data class IntNum(val i: Int) : Num {
        override fun toString() = "$i"
    }

    object PInf : Num {
        override fun toString() = "+inf"
    }

    object MInf : Num {
        override fun toString() = "-inf"
    }

    /**
     * Abstract binary `+` on intervals.
     */
    override fun plus(a: Element, b: Element): Element {
        val low =
            if (b.first is MInf || a.first is MInf) MInf
            else if (b.first is PInf || a.first is PInf) PInf
            else if (b.first is IntNum && a.first is IntNum) IntNum((a.first as IntNum).i + (b.first as IntNum).i)
            else MInf
        val high =
            if (b.second is MInf || a.second is MInf) MInf
            else if (b.second is PInf || a.second is PInf) PInf
            else if (b.second is IntNum && a.second is IntNum) IntNum((a.second as IntNum).i + (b.second as IntNum).i)
            else MInf
        return Pair(low, high)
    }

    /**
     * Abstract binary `-` on intervals.
     */
    override fun minus(a: Element, b: Element): Element = plus(a, inv(b))

    /**
     * Abstract `/` on intervals.
     */
    override fun div(a: Element, b: Element): Element = when {
        a.first is PInf -> EmptyInterval
        b.first is PInf -> EmptyInterval
        else -> {
            val sb = signs(b)
            val sbNoZero = sb - 0
            val d = { x: Int, y: Int -> x / y }
            val arange = sbNoZero.map { opNum(a, it, d) }
            Pair(
                min(arange.map { it.first }.toSet()),
                max(arange.map { it.second }.toSet())
            )
        }
    }

    /**
     * Finds the minimum of the given set of Num values.
     */
    fun min(s: Set<Num>): Num =
        if (s.isEmpty()) PInf
        else {
            s.reduce { a, b ->
                when {
                    a is PInf -> b
                    b is PInf -> a
                    a is MInf || b is MInf -> MInf
                    a is IntNum && b is IntNum -> IntNum(kotlin.math.min(a.i, b.i))
                    else -> throw IllegalArgumentException()
                }
            }
        }

    /**
     * Finds the maximum of the given set of Num values.
     */
    fun max(s: Set<Num>): Num =
        if (s.isEmpty()) MInf
        else {
            s.reduce { a, b ->
                when {
                    a is PInf || b is PInf -> PInf
                    b is MInf -> a
                    a is MInf -> b
                    a is IntNum && b is IntNum -> IntNum(kotlin.math.max(a.i, b.i))
                    else -> throw IllegalArgumentException()
                }
            }
        }

    /**
     * Returns the set of signs of the integers in the given interval.
     */
    private fun signs(a: Element): Set<Int> = when {
        a == FullInterval -> setOf(-1, 0, +1)
        a.first is MInf && a.second is IntNum -> {
            val x = (a.second as IntNum).i
            when {
                x > 0 -> setOf(-1, 0, +1, x)
                x == 0 -> setOf(-1, 0)
                else -> setOf(x, -1)
            }
        }
        a.first is IntNum && a.second is PInf -> {
            val x = (a.first as IntNum).i
            when {
                x < 0 -> setOf(x, -1, 0, +1)
                x == 0 -> setOf(0, +1, x)
                else -> setOf(+1, x)
            }
        }
        a.first is IntNum && a.second is IntNum -> {
            val l = (a.first as IntNum).i
            val h = (a.second as IntNum).i
            setOf(-1, +1, 0, l, h).filter { it in l..h }.toSet()
        }
        else -> throw IllegalArgumentException()
    }

    /**
     * Apples the binary operator `op` on the interval `a` and the int `b`.
     */
    private fun opNum(a: Element, b: Int, op: (Int, Int) -> Int): Element =
        when {
            a.first is PInf -> EmptyInterval
            a == FullInterval -> FullInterval
            a.first is MInf && a.second is IntNum -> {
                val x = (a.second as IntNum).i
                when {
                    b == 0 -> Pair(0.toNum(), 0.toNum())
                    b < 0 -> Pair(op(x, b).toNum(), PInf)
                    else -> Pair(MInf, op(x, b).toNum())
                }
            }
            a.first is IntNum && a.second is PInf -> {
                val x = (a.first as IntNum).i
                when {
                    b == 0 -> Pair(0.toNum(), 0.toNum())
                    b < 0 -> Pair(MInf, op(x, b).toNum())
                    else -> Pair(op(x, b).toNum(), PInf)
                }
            }
            a.first is IntNum && a.second is IntNum -> {
                val x = (a.first as IntNum).i
                val y = (a.second as IntNum).i
                Pair(
                    min(setOf(op(x, b).toNum(), op(y, b).toNum())),
                    max(setOf(op(x, b).toNum(), op(y, b).toNum()))
                )
            }
            else -> throw IllegalArgumentException()
        }

    /**
     * Abstract `*` on intervals;
     */
    override fun times(a: Element, b: Element): Element = when {
        a.first is PInf -> EmptyInterval
        b.first is PInf -> EmptyInterval
        else -> {
            val sa = signs(a)
            val sb = signs(b)
            val mult = { x: Int, y: Int -> x * y }
            val arange = sb.map { opNum(a, it, mult) }
            val brange = sa.map { opNum(b, it, mult) }
            Pair(
                min(arange.map { it.first }.toSet()),
                max(brange.map { it.second }.toSet())
            )
        }
    }

    /**
     * Abstract unary `-` on intervals.
     */
    private fun inv(b: Element): Element = when {
        b == FullInterval -> FullInterval
        b == EmptyInterval -> EmptyInterval
        b.first is IntNum && b.second is PInf -> Pair(MInf, IntNum(-(b.first as IntNum).i))
        b.first is MInf && b.second is IntNum -> Pair(IntNum(-(b.second as IntNum).i), PInf)
        b.first is IntNum && b.second is IntNum -> {
            val h = (b.first as IntNum).i
            val l = (b.second as IntNum).i
            Pair(
                IntNum(kotlin.math.min(-h, -l)),
                IntNum(kotlin.math.max(-h, -l))
            )
        }
        else -> throw IllegalArgumentException()
    }


    /**
     * Abstract `==` on intervals;
     */
    override fun eqq(a: Element, b: Element): Element = when {
        a == FullInterval -> FullInterval
        b == FullInterval -> FullInterval
        a.first is IntNum && a.second is IntNum && b.first is IntNum && b.second is IntNum -> {
            val l1 = (a.first as IntNum).i
            val l2 = (a.second as IntNum).i
            val h1 = (b.first as IntNum).i
            val h2 = (b.second as IntNum).i
            if (l1 == h1 && h1 == l2 && l2 == h2) Pair(IntNum(1), IntNum(1))
            else Pair(IntNum(0), IntNum(1))
        }
        else -> Pair(IntNum(0), IntNum(1))
    }

    /**
     * Abstract `>` on intervals;
     */
    override fun gt(a: Element, b: Element): Element = when {
        a == FullInterval -> FullInterval
        b == FullInterval -> FullInterval
        a.first is IntNum && a.second is IntNum && b.first is IntNum && b.second is IntNum -> {
            val l1 = (a.first as IntNum).i
            val l2 = (a.second as IntNum).i
            val h1 = (b.first as IntNum).i
            val h2 = (b.second as IntNum).i
            if (h1 < l2) Pair(IntNum(1), IntNum(1))
            else if (h2 < l1) Pair(IntNum(0), IntNum(0))
            else Pair(IntNum(0), IntNum(1))
        }
        else -> Pair(IntNum(0), IntNum(1))
    }
}


