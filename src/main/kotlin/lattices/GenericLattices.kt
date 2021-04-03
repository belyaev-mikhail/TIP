package lattices

/**
 * A (semi-)lattice.
 */
interface Lattice<T> {

    /**
     * The characteristic function of the set of lattice elements.
     * Default implementation: returns true for all elements of the right type.
     */
    fun ch(e: T) = true

    /**
     * The bottom element of this lattice.
     */
    val bottom: T

    /**
     * The top element of this lattice.
     * Default: not implemented.
     */
    val top: T?
        get() = TODO()


    /**
     * The least upper bound of `x` and `y`.
     */
    fun lub(x: T, y: T): T

    /**
     * Returns true whenever `x` <= `y`.
     */
    fun leq(x: T, y: T): Boolean = lub(x, y) == y // rarely used, but easy to implement :-)
}

/**
 * The `n`-th product lattice made of `sublattice` lattices.
 */
class UniformProductLattice<L : Lattice<Any>>(val sublattice: L, n: Int) : Lattice<List<Any>> {

    override val bottom = (1..n).map { sublattice.bottom }

    override fun lub(x: List<Any>, y: List<Any>): List<Any> {
        if (x.size != y.size)
            error()
        return (x zip y).map { (xc, yc) -> sublattice.lub(xc, yc) }
    }

    private fun error() {
        throw IllegalArgumentException("products not of same length")
    }
}

/**
 * The flat lattice made of element of `X`.
 * Top is greater than every other element, and Bottom is less than every other element.
 * No additional ordering is defined.
 */
open class FlatLattice<X> : Lattice<FlatLattice.FlatElement> {

    interface FlatElement

    data class FlatEl<X>(val el: X) : FlatElement {
        override fun toString(): String = el.toString()
    }

    object Top : FlatElement {
        override fun toString() = "Top"
    }

    object Bot : FlatElement {
        override fun toString() = "Bot"
    }

    /**
     * Lift an element of `X` into an element of the flat lattice.
     */
    fun lift(a: X): FlatEl<X> = FlatEl(a)

    /**
     * Un-lift an element of the lattice to an element of `X`.
     * If the element is Top or Bot then IllegalArgumentException is thrown.
     * Note that this method is declared as implicit, so the conversion can be done automatically.
     */
    fun unlift(a: FlatEl<X>): X = a.el

    override val top: FlatElement = Top

    override val bottom: FlatElement = Bot

    override fun lub(x: FlatElement, y: FlatElement): FlatElement =
        if (x == Bot || y == Top || x == y)
            y
        else if (y == Bot || x == Top)
            x
        else
            Top
}

/**
 * The product lattice made by `l1` and `l2`.
 */
class PairLattice<L1 : Lattice<Any>, L2 : Lattice<Any>>(val sublattice1: L1, val sublattice2: L2) : Lattice<Pair<Any, Any>> {

    override val bottom: Pair<Any, Any> = Pair(sublattice1.bottom, sublattice2.bottom)

    override fun lub(x: Pair<Any, Any>, y: Pair<Any, Any>) =
        Pair(sublattice1.lub(x.first, y.first), sublattice2.lub(x.second, y.second))

}

/**
 * A lattice of maps from the set `X` to the lattice `sublattice`.
 * The set `X` is a subset of `A` and it is defined by the characteristic function `ch`, i.e. `a` is in `X` if and only if `ch(a)` returns true.
 * Bottom is the default value.
 */
class MapLattice<A, out L : Lattice<Any>>(ch: (A) -> Boolean, val sublattice: L) : Lattice<Map<A, Any>> {
    // note: 'ch' isn't used in the class, but having it as a class parameter avoids a lot of type annotations

    override val bottom: Map<A, Any> = mutableMapOf<A, Any>().withDefault { sublattice.bottom }

    override fun lub(x: Map<A, Any>, y: Map<A, Any>): Map<A, Any> =
        x.keys.fold(y) { m, a -> m + Pair(a, sublattice.lub(x[a]!!, y[a]!!)) }.withDefault { sublattice.bottom }
}

/**
 * The powerset lattice of `X`, where `X` is the subset of `A` defined by the characteristic function `ch`, with subset ordering.
 */
class PowersetLattice<A>(ch: (A) -> Boolean) : Lattice<Set<A>> {
    // note: 'ch' isn't used in the class, but having it as a class parameter avoids a lot of type annotations

    override val bottom: Set<A> = TODO() //<--- Complete here

    override fun lub(x: Set<A>, y: Set<A>) = TODO() //<--- Complete here
}

/**
 * The powerset lattice of `X`, where `X` is the subset of `A` defined by the characteristic function `ch`, with superset ordering.
 */
class ReversePowersetLattice<A>(s: Set<A>) : Lattice<Set<A>> {

    override val bottom: Set<A> = s

    override fun lub(x: Set<A>, y: Set<A>) = x intersect y
}

/**
 * The lift lattice for `sublattice`.
 * Supports implicit lifting and unlifting.
 */
class LiftLattice<out L : Lattice<Any>>(val sublattice: L) : Lattice<LiftLattice.Lifted<Any>> {

    interface Lifted<L>

    object Bottom : Lifted<Any> {
        override fun toString() = "LiftBot"
    }

    data class Lift<L>(val n: L) : Lifted<L>

    override val bottom: Lifted<Any> = Bottom

    override fun lub(x: Lifted<Any>, y: Lifted<Any>): Lifted<Any> =
        when {
            x is Bottom -> y
            y is Bottom -> x
            x is Lift && y is Lift -> Lift(sublattice.lub(x.n, y.n))
            else -> throw IllegalArgumentException()
        }

    /**
     * Lift elements of the sublattice to this lattice.
     * Note that this method is declared as implicit, so the conversion can be done automatically.
     */
    fun lift(x: Lattice<Any>): Lifted<Any> = Lift(x)

    /**
     * Un-lift elements of this lattice to the sublattice.
     * Throws an IllegalArgumentException if trying to unlift the bottom element
     * Note that this method is declared as implicit, so the conversion can be done automatically.
     */
    fun unlift(x: Lifted<Any>): Any = when (x) {
        is Lift -> x.n
        is Bottom -> throw IllegalArgumentException("Cannot unlift bottom")
        else -> throw IllegalArgumentException("Cannot unlift $x")
    }
}

