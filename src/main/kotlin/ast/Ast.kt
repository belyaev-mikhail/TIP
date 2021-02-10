package ast

object AstNodeObject {
    var lastUid: Int = 0
}

data class Loc(val line: Int, val col: Int) {
    override fun toString(): String = "${line}:${col}"
}

interface Operator
interface BinaryOperator
interface UnaryOperator

object Plus : Operator, BinaryOperator {
    override fun toString(): String = "+"
}

object Minus : Operator, BinaryOperator {
    override fun toString(): String = "-"
}

object Times : Operator, BinaryOperator {
    override fun toString(): String = "*"
}

object Divide : Operator, BinaryOperator {
    override fun toString(): String = "/"
}

object Eqq : Operator, BinaryOperator {
    override fun toString(): String = "=="
}

object GreaterThan : Operator, BinaryOperator {
    override fun toString(): String = ">"
}

object RefOp : Operator, UnaryOperator {
    override fun toString(): String = "&"
}

object DerefOp : Operator, UnaryOperator {
    override fun toString(): String = "*"
}

/**
 * AST node.
 */
interface IAstNode {

    /**
     * Unique ID of the node.
     * Every new node object gets a fresh ID (but the ID is ignored in equals tests).
     */
    val uid: () -> Int

    /**
     * Source code location.
     */
    val loc: Loc

    override fun toString(): String
}

sealed class AstNode : IAstNode {
    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }

//    override fun toString(): String = "${this.print(PartialFunction.empty)}:$loc"
}

//////////////// Expressions //////////////////////////

interface AExprOrIdentifierDeclaration : IAstNode
interface AExpr : AExprOrIdentifierDeclaration
interface Assignable<out T> : AExpr
interface AAtomicExpr : AExpr
interface ADeclaration : IAstNode

data class ACallFuncExpr(val targetFun: AExpr, val args: List<AExpr>, val indirect: Boolean, override val loc: Loc) : AExpr {
    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }
}

data class AIdentifierDeclaration(val value: String, override val loc: Loc) : ADeclaration, AExprOrIdentifierDeclaration {
    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }
}

data class AIdentifier(val value: String, override val loc: Loc) : AExpr, AAtomicExpr, Assignable<Nothing> {
    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }
}

data class ABinaryOp(val operator: BinaryOperator, val left: AExpr, val right: AExpr, override val loc: Loc) : AExpr {
    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }
}

data class AUnaryOp<out T : UnaryOperator>(val operator: T, val target: AExpr, override val loc: Loc) : AExpr, Assignable<T> {
    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }
}

data class ANumber(val value: Int, override val loc: Loc) : AExpr, AAtomicExpr {
    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }
}

data class AInput(override val loc: Loc) : AExpr, AAtomicExpr {
    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }
}

data class AAlloc(override val loc: Loc) : AExpr, AAtomicExpr {
    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }
}

data class ANull(override val loc: Loc) : AExpr, AAtomicExpr {
    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }
}

//////////////// Statements //////////////////////////

interface AStmt : IAstNode

/**
 * A statement in the body of a nested block (cannot be a declaration or a return).
 */
interface AStmtInNestedBlock : AStmt

data class AAssignStmt(val left: Assignable<DerefOp>, val right: AExpr, override val loc: Loc) : AStmtInNestedBlock{
    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }
}


interface ABlock : AStmt {

    /**
     * All the statements in the block, in order.
     */
    val body: List<AStmt>

}

data class ANestedBlockStmt(override val body: List<AStmtInNestedBlock>, override val loc: Loc) : ABlock, AStmtInNestedBlock {
    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }
}

data class AFunBlockStmt(val declarations: List<AVarStmt>, val others: List<AStmtInNestedBlock>, val ret: AReturnStmt, override val loc: Loc) : ABlock {

    /**
     * The contents of the block, not partitioned into declarations, others and return
     */
    override val body: List<AStmt> = declarations + (others + ret)

    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }
}

data class AIfStmt(val guard: AExpr, val ifBranch: AStmtInNestedBlock, val elseBranch: AStmtInNestedBlock?, override val loc: Loc) : AStmtInNestedBlock{
    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }
}

data class AOutputStmt(val value: AExpr, override val loc: Loc) : AStmtInNestedBlock{
    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }
}

data class AReturnStmt(val value: AExpr, override val loc: Loc) : AStmt{
    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }
}

data class AErrorStmt(val value: AExpr, override val loc: Loc) : AStmtInNestedBlock{
    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }
}

data class AVarStmt(val declIds: List<AIdentifierDeclaration>, override val loc: Loc) : AStmt{
    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }
}

data class AWhileStmt(val guard: AExpr, val innerBlock: AStmtInNestedBlock, override val loc: Loc) : AStmtInNestedBlock{
    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }
}

//////////////// Program and function ///////////////

data class AProgram(val funs: List<AFunDeclaration>, override val loc: Loc) : AstNode() {

    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }

    fun mainFunction(): AFunDeclaration {
        val main = findMainFunction()
        if (main != null) return main
        else throw RuntimeException("Missing main function, declared functions are $funs")
    }

    fun hasMainFunction(): Boolean = findMainFunction() == null

    private fun findMainFunction(): AFunDeclaration? {
        return funs.find { it.name == "main" }
    }

}

data class AFunDeclaration(val name: String, val args: List<AIdentifierDeclaration>, val stmts: AFunBlockStmt, override val loc: Loc) : ADeclaration {

    override val uid: () -> Int = { AstNodeObject.lastUid += 1; AstNodeObject.lastUid }

    override fun toString() = "$name (${args.joinToString(",")}){...}"

}