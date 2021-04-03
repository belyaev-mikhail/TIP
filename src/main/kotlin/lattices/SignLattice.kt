package lattices

import ast.*
import java.util.*

/**
 * An element of the sign lattice.
 */
enum class SignElement : FlatLattice.FlatElement {
    Pos, Neg, Zero
}

/**
 * The sign lattice.
 */
object SignLattice : FlatLattice<SignElement>(), LatticeOps<FlatLattice.FlatElement> {

    private val signValues: Map<FlatElement, Int> = mapOf(
        Pair(Bot, 0),
        Pair(FlatEl(SignElement.Zero), 1),
        Pair(FlatEl(SignElement.Neg), 2),
        Pair(FlatEl(SignElement.Pos), 3),
        Pair(Top, 4)
    )

    private fun abs(op: List<List<FlatElement>>, x: FlatElement, y: FlatElement): FlatElement =
        op[signValues[x]!!][signValues[y]!!]

    private val absPlus: List<List<FlatElement>> = listOf(
        listOf(Bot, Bot, Bot, Bot, Bot),
        listOf(Bot, SignElement.Zero, SignElement.Neg, SignElement.Pos, Top),
        listOf(Bot, SignElement.Neg, SignElement.Neg, Top, Top),
        listOf(Bot, SignElement.Pos, Top, SignElement.Pos, Top),
        listOf(Bot, Top, Top, Top, Top)
    )

    private val absMinus: List<List<FlatElement>> = listOf(
        listOf(Bot, Bot, Bot, Bot, Bot),
        listOf(Bot, SignElement.Zero, SignElement.Pos, SignElement.Neg, Top),
        listOf(Bot, SignElement.Neg, Top, SignElement.Neg, Top),
        listOf(Bot, SignElement.Pos, SignElement.Pos, Top, Top),
        listOf(Bot, Top, Top, Top, Top)
    )

    private val absTimes: List<List<FlatElement>> = listOf(
        listOf(Bot, Bot, Bot, Bot, Bot),
        listOf(Bot, SignElement.Zero, SignElement.Zero, SignElement.Zero, SignElement.Zero),
        listOf(Bot, SignElement.Zero, SignElement.Pos, SignElement.Neg, Top),
        listOf(Bot, SignElement.Zero, SignElement.Neg, SignElement.Pos, Top),
        listOf(Bot, SignElement.Zero, Top, Top, Top)
    )

    private val absDivide: List<List<FlatElement>> = listOf(
        listOf(Bot, Bot, Bot, Bot, Bot),
        listOf(Bot, Bot, SignElement.Zero, SignElement.Zero, Top),
        listOf(Bot, Bot, Top, Top, Top),
        listOf(Bot, Bot, Top, Top, Top),
        listOf(Bot, Bot, Top, Top, Top)
    )

    private val absGt: List<List<FlatElement>> = listOf(
        listOf(Bot, Bot, Bot, Bot, Bot),
        listOf(Bot, SignElement.Zero, SignElement.Pos, SignElement.Zero, Top),
        listOf(Bot, SignElement.Zero, Top, SignElement.Zero, Top),
        listOf(Bot, SignElement.Pos, SignElement.Pos, Top, Top),
        listOf(Bot, Top, Top, Top, Top)
    )

    private val absEq: List<List<FlatElement>> = listOf(
        listOf(Bot, Bot, Bot, Bot, Bot),
        listOf(Bot, SignElement.Pos, SignElement.Zero, SignElement.Zero, Top),
        listOf(Bot, SignElement.Zero, Top, SignElement.Zero, Top),
        listOf(Bot, SignElement.Zero, SignElement.Zero, Top, Top),
        listOf(Bot, Top, Top, Top, Top)
    )

    override fun plus(a: FlatElement, b: FlatElement): FlatElement = abs(absPlus, a, b)

    override fun minus(a: FlatElement, b: FlatElement) = abs(absMinus, a, b)

    override fun times(a: FlatElement, b: FlatElement) = abs(absTimes, a, b)

    override fun div(a: FlatElement, b: FlatElement) = abs(absDivide, a, b)

    override fun eqq(a: FlatElement, b: FlatElement) = abs(absEq, a, b)

    override fun gt(a: FlatElement, b: FlatElement) = abs(absGt, a, b)

    /**
     * Returns the sign of `i`.
     */
    private fun sign(i: Int): FlatElement = when {
        i == 0 -> SignElement.Zero
        i > 0 -> SignElement.Pos
        else -> SignElement.Neg
    }

    /**
     * Evaluates the expression `exp` in the abstract domain of signs, using `env` as the current environment.
     */
    fun eval(exp: AExpr, env: Map<ADeclaration, FlatElement>): FlatElement = when (exp) {
        is AIdentifier -> env[exp]!!
        is ANumber -> sign(exp.value)
        is ABinaryOp -> when (exp.operator) {
            Plus -> plus(eval(exp.left, env), eval(exp.right, env))
            Minus -> minus(eval(exp.left, env), eval(exp.right, env))
            Times -> times(eval(exp.left, env), eval(exp.right, env))
            Divide -> div(eval(exp.left, env), eval(exp.right, env))
            GreaterThan -> gt(eval(exp.left, env), eval(exp.right, env))
            Eqq -> eqq(eval(exp.left, env), eval(exp.right, env))
            else -> throw UnexpectedUnsupportedExpressionException("Unexpected expression $exp in eval")
        }
        is AInput -> Top
        is AUnaryOp<*> -> NoPointers.LanguageRestrictionViolation("No pointers allowed in eval $exp")
        is ACallFuncExpr -> NoCalls.LanguageRestrictionViolation("No calls allowed in eval $exp")
        else -> throw UnexpectedUnsupportedExpressionException("Unexpected expression $exp in eval")
    }

    data class UnexpectedUnsupportedExpressionException(val msg: String) : RuntimeException(msg)

}