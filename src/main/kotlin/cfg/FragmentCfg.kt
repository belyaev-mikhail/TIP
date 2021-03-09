package cfg

import ast.*
import utils.DotArrow
import utils.DotDirArrow
import utils.DotGraph
import utils.DotNode

object FragmentCfgObj {

    /**
     * Generates a CFG for each function in the given program.
     */
    fun generateFromProgram(prog: AProgram, nodeBuilder: (CfgNode) -> FragmentCfg): Map<AFunDeclaration, FragmentCfg> =
        prog.funs.map { f -> Pair(f, generateFromFunction(f, nodeBuilder)) }.toMap()

    /**
     * Constructs an empty CFG.
     */
    private fun seqUnit(): FragmentCfg = FragmentCfg(setOf(), setOf())

    /**
     * Converts a CFG node to a one-node CFG.
     */
    fun nodeToGraph(node: CfgNode): FragmentCfg = FragmentCfg(setOf(node), setOf(node))

    /**
     * Generates a CFG from the body of a function.
     */
    fun generateFromFunction(func: AFunDeclaration, nodeBuilder: (CfgNode) -> FragmentCfg): FragmentCfg {

        fun recGen(node: AstNode): FragmentCfg {
            return when (node) {
                is AFunDeclaration -> {
                    val blk = recGen(func.stmts)
                    val entry = nodeBuilder(CfgFunEntryNode(data = node))
                    val exit = nodeBuilder(CfgFunExitNode(data = node))
                    entry.concat(blk).concat(exit)
                }
                is AAssignStmt -> nodeBuilder(CfgStmtNode(data = node))
                is ABlock -> {
                    node.body.fold(seqUnit()) { g, stmt ->
                        g.concat(recGen(stmt))
                    }
                }
                is AIfStmt -> {
                    val ifGuard = nodeBuilder(CfgStmtNode(data = node))
                    val trueBranch = recGen(node.ifBranch)
                    val falseBranch = node.elseBranch?.let { recGen(it) }
                    val guardedTrue = ifGuard.concat(trueBranch)
                    val guardedFalse = listOf(falseBranch?.let { ifGuard.concat(it) })
                    guardedFalse.fold(guardedTrue.union(ifGuard)) { gf, _ -> guardedTrue.union(gf) }
                }
                is AOutputStmt -> nodeBuilder(CfgStmtNode(data = node))
                is AReturnStmt -> nodeBuilder(CfgStmtNode(data = node))
                is AVarStmt -> nodeBuilder(CfgStmtNode(data = node))
                is AWhileStmt -> {
                    val whileG = nodeBuilder(CfgStmtNode(data = node))
                    val bodyG = recGen(node.innerBlock)
                    val loopingBody = whileG.concat(bodyG).concat(whileG)
                    loopingBody.union(whileG)
                }
                is AErrorStmt -> nodeBuilder(CfgStmtNode(data = node))
                else -> throw IllegalArgumentException()
            }
        }
        return recGen(func)
    }
}

open class FragmentCfg(val graphEntries: Set<CfgNode>, val graphExits: Set<CfgNode>) {

    /**
     * Returns true if this is the unit CFG w.r.t. to concatenation.
     */
    fun isUnit() = graphEntries.isEmpty() && graphExits.isEmpty()

    /**
     * Returns the concatenation of this CFG with `after`.
     */
    fun concat(after: FragmentCfg): FragmentCfg =
        when {
            isUnit() -> after
            after.isUnit() -> this
            else -> {
                graphExits.forEach { it.succ += after.graphEntries }
                after.graphEntries.forEach { it.pred += graphExits }
                FragmentCfg(graphEntries, after.graphExits)
            }
        }

    /**
     * Returns the union of this CFG with `other`.
     */
    fun union(other: FragmentCfg): FragmentCfg = FragmentCfg(other.graphEntries.union(graphEntries), other.graphExits.union(graphExits))

    /**
     * Returns the set of nodes in the CFG.
     */
    fun nodes(): Set<CfgNode> = graphEntries.flatMap { entry -> nodesRec(entry) }.toSet()

    protected fun nodesRec(n: CfgNode, visited: MutableSet<CfgNode> = mutableSetOf()): MutableSet<CfgNode> {
        if (!visited.contains(n)) {
            visited += n
            n.succ.forEach { nodesRec(it, visited) }
        }
        return visited
    }

    /**
     * Returns a map associating each node with its rank.
     * The rank is defined such that
     * rank(x) < rank(y) iff y is visited after x in a depth-first
     * visit of the control-flow graph
     */
    fun rank(): Map<CfgNode, Int> {
        fun rankRec(elems: List<CfgNode>, visited: List<List<CfgNode>>, level: Int): Map<CfgNode, Int> {
            val curLevel: Map<CfgNode, Int> = elems.map { Pair(it, level) }.toMap()
            val newNeighbours = elems.flatMap { it.succ }.filterNot { visited.flatten().contains(it) }.distinct()
            return if (newNeighbours.isEmpty()) curLevel
            else {
                val newVisited = listOf(newNeighbours) + visited
                rankRec(newNeighbours, newVisited, level + 1) + curLevel
            }
        }
        return rankRec(graphEntries.toList(), listOf(graphEntries.toList()), 0)
    }

    /**
     * Returns a Graphviz dot representation of the CFG.
     * Each node is labeled using the given function labeler.
     */
    fun toDot(labeler: (CfgNode) -> String, idGen: (CfgNode) -> String): String {
        val dotNodes = mutableMapOf<CfgNode, DotNode>()
        var dotArrows = mutableSetOf<DotArrow>()
        nodes().forEach { n -> dotNodes[n] = DotNode(idGen(n), labeler(n), mapOf()) }
        nodes().forEach { n ->
            n.succ.forEach { dest ->
                dotArrows.add(DotDirArrow(dotNodes[n]!!, dotNodes[dest]!!))
            }
        }
        dotArrows = dotArrows.sortedBy { arr -> arr.fromNode.id + "-" + arr.toNode.id }.toMutableSet()
        val allNodes = dotNodes.values.toList().sortedBy { n -> n.id }
        return DotGraph("CFG", allNodes, dotArrows).toDotString()
    }
}

/**
 * Control-flow graph for an entire program.
 *
 * @param prog AST of the program
 * @param funEntries map from AST function declarations to CFG function entry nodes
 * @param funExits map from AST function declarations to CFG function exit nodes
 */
abstract class ProgramCfg(val prog: AProgram, val funEntries: Map<AFunDeclaration, CfgFunEntryNode>, val funExits: Map<AFunDeclaration, CfgFunExitNode>) :
    FragmentCfg(funEntries.values.toSet(), funEntries.values.toSet())