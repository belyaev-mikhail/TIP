package cfg

import ast.AstNode

object CfgNodeObj {
    var lastUid = 0

    fun uid(): Int {
        lastUid++
        return lastUid
    }
}

/**
 * Node in a control-flow graph.
 */
abstract class CfgNode {

    /**
     * Predecessors of the node.
     */
    abstract val pred: MutableSet<CfgNode>

    /**
     * Successors of the node.
     */
    abstract val succ: MutableSet<CfgNode>

    /**
     * Unique node ID.
     */
    abstract val id: Int

    /**
     * The AST node contained by this node.
     */
    abstract val data: AstNode

    override fun equals(other: Any?): Boolean =
        if (other is CfgNode) other.id == this.id
        else false

    override fun hashCode() = id
}

/**
 * Node in a CFG representing a program statement.
 * The `data` field holds the statement, or in case of if/while instructions, the branch condition.
 */
data class CfgStmtNode(
    override val pred: MutableSet<CfgNode> = mutableSetOf(),
    override val succ: MutableSet<CfgNode> = mutableSetOf(),
    override val id: Int = CfgNodeObj.uid(),
    override val data: AstNode
) : CfgNode() {
    override fun toString() = "[Stmt] $data"
}

/**
 * Node in a CFG representing a function call.
 * The `data` field holds the assignment statement where the right-hand-side is the function call.
 */
data class CfgCallNode(
    override val pred: MutableSet<CfgNode> = mutableSetOf(),
    override val succ: MutableSet<CfgNode> = mutableSetOf(),
    override val id: Int = CfgNodeObj.uid(),
    override val data: AstNode
) : CfgNode() {
    override fun toString() = "[Call] $data"
}

/**
 * Node in a CFG representing having returned from a function call.
 * The `data` field holds the assignment statement where the right-hand-side is the function call.
 */
data class CfgAfterCallNode(
    override val pred: MutableSet<CfgNode> = mutableSetOf(),
    override val succ: MutableSet<CfgNode> = mutableSetOf(),
    override val id: Int = CfgNodeObj.uid(),
    override val data: AstNode
) : CfgNode() {
    override fun toString() = "[AfterCall] $data"
}

/**
 * Node in a CFG representing the entry of a function.
 */
data class CfgFunEntryNode(
    override val pred: MutableSet<CfgNode> = mutableSetOf(),
    override val succ: MutableSet<CfgNode> = mutableSetOf(),
    override val id: Int = CfgNodeObj.uid(),
    override val data: AstNode
) : CfgNode() {
    override fun toString() = "[FunEntry] $data"
}

/**
 * Node in a CFG representing the exit of a function.
 */
data class CfgFunExitNode(
    override val pred: MutableSet<CfgNode> = mutableSetOf(),
    override val succ: MutableSet<CfgNode> = mutableSetOf(),
    override val id: Int = CfgNodeObj.uid(),
    override val data: AstNode
) : CfgNode() {
    override fun toString() = "[FunExit] $data"
}