package utils

/**
 * Generator for fresh node IDs.
 */
object IDGenerator {
    private var current: Int = 0

    fun getNewId(): Int {
        current += 1
        return current
    }
}

/**
 * Super-class for elements of a Graphviz dot file.
 */
abstract class DotElement {

    /**
     * Produces a dot string representation of this element.
     */
    abstract fun toDotString(): String
}

/**
 * Represents a node in a Graphviz dot file.
 */
class DotNode(val id: String, val label: String, val additionalParams: Map<String, String>) : DotElement() {

    constructor(label: String, additionalParams: Map<String, String> = mapOf()) :
        this("n" + IDGenerator.getNewId(), label, additionalParams)

    constructor(): this("")

    fun equals(other: DotNode) = toDotString() == other.toDotString()

    override fun toString(): String = toDotString()

    override fun toDotString(): String =
    id + "[label=\"" + label + "\"" +
            additionalParams.map { "${it.key} = ${it.value}"}.joinToString(",") + "]"
}

/**
 * Represents an edge between two nodes in a Graphviz dot file.
 */
open class DotArrow(val fromNode: DotNode, val arrow: String, val toNode: DotNode, val label: String) : DotElement() {

    fun equals(other: DotArrow) = toDotString() == other.toDotString()

    override fun toDotString(): String = fromNode.id + " " + arrow + " " + toNode.id + "[label=\"" + label + "\"]"
}

/**
 * Represents a directed edge between two nodes in a Graphviz dot file.
 */
class DotDirArrow(fromNode: DotNode, toNode: DotNode, label: String) : DotArrow(fromNode, "->", toNode, label) {
    constructor(fromNode: DotNode, toNode: DotNode) : this(fromNode, toNode, "")
}

/**
 * Represents a Graphviz dot graph.
 */
class DotGraph(val title: String, val nodes: Iterable<DotNode>, val edges: Iterable<DotArrow>) : DotElement() {

    constructor(nodes: List<DotNode>, edges: List<DotArrow>) : this("", nodes, edges)

    constructor(title: String) : this(title, listOf(), listOf())

    constructor() : this(listOf(), listOf())

    fun addGraph(g: DotGraph): DotGraph {
        val ng = g.nodes.fold(this) { gr, n -> gr.addNode(n)}
        g.edges.fold(ng){ gr, e -> gr.addEdge(e)}
        return g
    }

    fun addNode(n: DotNode): DotGraph =
    if (nodes.any{ n.equals(it) }) this
    else DotGraph(title, nodes + n, edges)


    fun addEdge(e: DotArrow): DotGraph =
        if (edges.any { e.equals(it) }) this
        else DotGraph(title, nodes, edges + e)

    override fun toString(): String = toDotString()

    override fun toDotString() = "digraph " + title + "{" + (nodes + edges).fold(""){str, elm -> str + elm.toDotString() + "\n"} + "}"
}