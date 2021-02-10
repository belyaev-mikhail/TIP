package parser

import ast.*
import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.grammar.tryParseToEnd
import com.github.h0tk3y.betterParse.lexer.*
import com.github.h0tk3y.betterParse.parser.ParseResult
import com.github.h0tk3y.betterParse.parser.Parsed
import com.github.h0tk3y.betterParse.parser.Parser

class CursorValue(override val nextPosition: Int) : Parsed<Int>() {
    override val value: Int get() = nextPosition
}

public object Cursor : Parser<Int> {
    override fun tryParse(tokens: TokenMatchesSequence, fromPosition: Int): ParseResult<Int> = CursorValue(fromPosition)
}

fun offset2Loc(i: Int): Loc {
    return Loc(i + 1, i + 1)
}

interface Comments : Parser<AstNode> {
    var lastBreaks: MutableList<Int>

    fun offset2Loc(i: Int): Loc {
        val idx = lastBreaks.findLast { it <= i } ?: 0
        return Loc(idx + 1, i - lastBreaks[idx] + 1)
    }

    val newLine: Token

    val nonClosing: Parser<List<Any>>

    val blockComment: Parser<Any>

    val comment: Parser<Any>
}

class TIPGrammar : Grammar<AstNode>(){

    val allocToken by literalToken("alloc")
    val inputToken by literalToken("input")
    val whileToken by literalToken("while")
    val ifToken by literalToken("if")
    val elseToken by literalToken("else")
    val varToken by literalToken("var")
    val returnToken by literalToken("return")
    val nullToken by literalToken("null")
    val outputToken by literalToken("output")
    val errorToken by literalToken("error")
    val digit by regexToken("\\d")
    val leftParen by literalToken("(")
    val rightParen by literalToken(")")
    val leftBrace by literalToken("{")
    val rightBrace by literalToken("}")
    val minus by literalToken("-") asJust Minus
    val plus by literalToken("+") asJust Plus
    val alpha by regexToken("[a-zA-Z]+")
    val refOp by literalToken("&") asJust RefOp
    val derefOp by literalToken("*") asJust DerefOp
    val times by literalToken("*") asJust Times
    val div by literalToken("/") asJust Divide
    val greaterThan by literalToken(">") asJust GreaterThan
    val eqq by literalToken("==") asJust Eqq
    val assgn by literalToken("=")
    val semicol by literalToken(";")
    val blockCommentStart by literalToken("/*")
    val blockCommentEnd by literalToken("*/")
    val doubleSlash by literalToken("//")

    val ws by regexToken("[ \t\n]", ignore = true)

    override val rootParser by parser { program }

//    override var lastBreaks: MutableList<Int> = mutableListOf(0)
//
//    override val newLine by regexToken("[\r\n]")
//
//    override val nonClosing: Parser<List<Any>> by zeroOrMore(times * -div or newLine)
//
//    override val blockComment: Parser<Any> by blockCommentStart * parser { blockComment } or nonClosing * blockCommentEnd
//
//    override val comment by blockComment or doubleSlash * newLine
//
//    val ws by newLine or regexToken(" \t\n") or comment

//    val optSpace by zeroOrMore(ws)

    val keywords by
    allocToken or inputToken or whileToken or ifToken or elseToken or varToken or returnToken or nullToken or outputToken

    val operation: Parser<AExpr> by parser { term } *
            optional((Cursor * plus * parser { expression }) or (Cursor * minus * parser { expression })) use {
        ABinaryOp(t2!!.t2, t1, t2!!.t3, offset2Loc(t2!!.t1))
    }

    val expression: Parser<AExpr> by parser { operation } *
            optional((Cursor * greaterThan * parser { operation }) or (Cursor * eqq * parser { operation })) use {
        ABinaryOp(t2!!.t2, t1, t2!!.t3, offset2Loc(t2!!.t1))
    }

    val parens: Parser<AExpr> by -leftParen * parser { expression } * -rightParen

    val digits by optional(minus) * oneOrMore(digit)

    val number: Parser<AExpr> by Cursor * digits use {
        ANumber((t2.t1.toString() + t2.t2.toString()).toInt(), offset2Loc(t1))
    }

    val atom by parser { funApp } or number or parens or parser { identifier } or
            (Cursor * inputToken use { AInput(offset2Loc(t1)) }) or parser { pointersExpression }

    val term: Parser<AExpr> by atom *
            optional((Cursor * times * parser { term }) * (Cursor * div * parser { term })) use {
        t2?.let { ABinaryOp(it.t2, t1, it.t3, offset2Loc(it.t1)) } ?: t1
    }

    val identifier: Parser<AIdentifier> by Cursor * -keywords * alpha use {
        println(t1)
        println(AIdentifier(t2.text, offset2Loc(t1)))
        AIdentifier(t2.text, offset2Loc(t1))
    }

    val leftHandUnaryPointerExpression by Cursor * derefOp * parser { atom } use {
        AUnaryOp(t2, t3, offset2Loc(t1))
    }

    val assignableExpression: Parser<Assignable<DerefOp>> by identifier or leftHandUnaryPointerExpression

    val identifierDeclaration: Parser<AIdentifierDeclaration> by Cursor * -keywords * alpha use {
        AIdentifierDeclaration(t2.text, offset2Loc(t1))
    }

    val zeraryPointerExpression by (Cursor * allocToken) or (Cursor * nullToken) use {
        if (t2.text == allocToken.name) AAlloc(offset2Loc(t1))
        else ANull(offset2Loc(t1))
    }

    val unaryPointerExpression by (Cursor * refOp * identifier) or (Cursor * derefOp * parser { atom }) use {
        AUnaryOp(t2, t3, offset2Loc(t1))
    }

    val pointersExpression: Parser<AExpr> by zeraryPointerExpression or unaryPointerExpression

    val program: Parser<AProgram> by Cursor * zeroOrMore(parser { tipFunction }) use {
        println(t1)
        println(AProgram(t2, offset2Loc(t1)))
        AProgram(t2, offset2Loc(t1))
    }

    val tipFunction: Parser<AFunDeclaration> by Cursor * identifier * leftParen *
            zeroOrMore(identifierDeclaration) * rightParen * parser { funBlock } use {
        println(AFunDeclaration(t2.value, t4, t6, offset2Loc(t1)))
        AFunDeclaration(t2.value, t4, t6, offset2Loc(t1))
    }

    val statement: Parser<AStmtInNestedBlock> by parser { outputParser } or parser { assignment } or parser { block } or
            parser { whileParser } or parser { ifParser } or parser { error }

    val statements: Parser<List<AStmtInNestedBlock>> by zeroOrMore(statement)

    val varStatements by zeroOrMore(parser { declaration })

    val assignment: Parser<AStmtInNestedBlock> by Cursor * assignableExpression * assgn * expression * semicol use {
        AAssignStmt(t2, t4, offset2Loc(t1))
    }

    val block: Parser<AStmtInNestedBlock> by Cursor * leftBrace * statements * rightBrace use {
        ANestedBlockStmt(t3, offset2Loc(t1))
    }

    val funBlock: Parser<AFunBlockStmt> by Cursor * leftBrace * varStatements * statements * parser { returnParser } * rightBrace use {
        AFunBlockStmt(t3, t4, t5, offset2Loc(t1))
    }

    val declaration: Parser<AVarStmt> by Cursor * varToken * oneOrMore(identifierDeclaration) * semicol use {
        AVarStmt(t3, offset2Loc(t1))
    }

    val whileParser: Parser<AStmtInNestedBlock> by Cursor * whileToken * leftParen * expression * rightParen * parser { statement } use {
        AWhileStmt(t4, t6, offset2Loc(t1))
    }

    val ifParser: Parser<AStmtInNestedBlock> by Cursor * ifToken * leftParen * expression * rightParen * statement * optional(elseToken * statement) use {
        AIfStmt(t4, t6, t7!!.t2, offset2Loc(t1))
    }

    val outputParser: Parser<AStmtInNestedBlock> by Cursor * outputToken * parser { expression } * semicol use {
        AOutputStmt(t3, offset2Loc(t1))
    }

    val returnParser by Cursor * returnToken * expression * semicol use { AReturnStmt(t3, offset2Loc(t1)) }

    val error by Cursor * errorToken * expression * semicol use { AErrorStmt(t3, offset2Loc(t1)) }

    val funActualArgs: Parser<List<AExpr>> by -leftParen and zeroOrMore(expression)

    val funApp: Parser<AExpr> by (Cursor * parens * funActualArgs) or (Cursor * identifier * funActualArgs) map { (cur, id, args) ->
        println(id)
        println(args)
        if (id == parens) ACallFuncExpr(id, args, true, offset2Loc(cur))
        else ACallFuncExpr(id, args, false, offset2Loc(cur))
    }

}

fun main() {
    val g: ParseResult<AstNode> = TIPGrammar().tryParseToEnd("foo(i) { return i+1; }")
    print(g)
}