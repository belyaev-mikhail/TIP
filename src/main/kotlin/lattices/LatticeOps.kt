package lattices

/**
 * Abstract operators.
 */
interface LatticeOps<L> {

    fun plus(a: L, b: L): L

    fun minus(a: L, b: L): L

    fun times(a: L, b: L): L

    fun div(a: L, b: L): L

    fun eqq(a: L, b: L): L

    fun gt(a: L, b: L): L

}