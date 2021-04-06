package lattices

import java.lang.IllegalArgumentException

class EdgeLattice<L: Lattice<Any>>(val valuelattice: L) : Lattice<EdgeLattice.Edge<Any>> {

    override val bottom: ConstEdge<Any> = ConstEdge(valuelattice.bottom)

    override fun lub(x: Edge<Any>, y: Edge<Any>): Edge<Any> = x.joinWith(y)

    /**
     * An "edge" represents a function L -> L where L is the value lattice.
     */
    interface Edge<L> {
        /**
         * Applies the function to the given lattice element.
         */
        fun apply(x: Any): Any

        /**
         * Composes this function with the given one.
         * The resulting function first applies `e` then this function.
         */
        fun composeWith(e: Edge<L>): Edge<L>

        /**
         * Finds the least upper bound of this function and the given one.
         */
        fun joinWith(e: Edge<L>): Edge<L>
    }

    /**
     * Edge labeled with identity function.
     */
    inner class IdEdge<L> : Edge<L> {

        override fun apply(x: Any) = x

        override fun composeWith(e: Edge<L>) = e

        override fun joinWith(e: Edge<L>): Edge<L> =
            if (e == this) this
            else e.joinWith(this)

        override fun toString() = "IdEdge()"
    }

    /**
     * Edge labeled with constant function.
     */
     inner class ConstEdge<L>(val c: Any) : Edge<L> {

        override fun apply(x: Any) = c

        override fun composeWith(e: Edge<L>) = this

        override fun joinWith(e: Edge<L>) =
            if (e == this || c == valuelattice.top) this
            else if (c == valuelattice.bottom) e
            else when (e) {
                is EdgeLattice<*>.IdEdge<*> -> throw IllegalArgumentException() // never reached with the currently implemented analyses
                is EdgeLattice<*>.ConstEdge<*> -> ConstEdge(valuelattice.lub(c, e))
                else -> e.joinWith(this)
            }

        override fun toString() = "ConstEdge($c)"
    }
}