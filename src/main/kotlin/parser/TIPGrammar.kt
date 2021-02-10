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
import java.io.File
import java.lang.NullPointerException
import kotlin.math.log

class CursorValue(override val nextPosition: Int) : Parsed<Int>() {
    override val value: Int get() = nextPosition
}

public object Cursor : Parser<Int> {
    override fun tryParse(tokens: TokenMatchesSequence, fromPosition: Int): ParseResult<Int> {
//        println(fromPosition)
        return CursorValue(fromPosition)
    }
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
    val digits by regexToken("-?\\d")
    val leftParen by literalToken("(")
    val rightParen by literalToken(")")
    val leftBrace by literalToken("{")
    val rightBrace by literalToken("}")
    val minus by literalToken("-") asJust Minus
    val plus by literalToken("+") asJust Plus
    val alpha by regexToken("[a-zA-Z]+[a-zA-Z0-9]*")
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
        t2?.let { ABinaryOp(it.t2, t1, it.t3, offset2Loc(it.t1)) } ?: t1
    }

    val expression: Parser<AExpr> by parser { operation } *
            optional((Cursor * greaterThan * parser { operation }) or (Cursor * eqq * parser { operation })) use {
        t2?.let { ABinaryOp(it.t2, t1, it.t3, offset2Loc(it.t1)) } ?: t1
    }

    val parens: Parser<AExpr> by -leftParen * expression * -rightParen

    val number: Parser<AExpr> by Cursor * digits use {
        ANumber(t2.value.text.toInt(), offset2Loc(t1))
    }

    val atom by parser { funApp } or number or parens or parser { identifier } or
            (Cursor * inputToken use { AInput(offset2Loc(t1)) }) or parser { pointersExpression }

    val term: Parser<AExpr> by atom *
            optional((Cursor * times * parser { term }) * (Cursor * div * parser { term })) use {
        t2?.let { ABinaryOp(it.t2, t1, it.t3, offset2Loc(it.t1)) } ?: t1
    }

    val identifier: Parser<AIdentifier> by Cursor * alpha use {
        AIdentifier(t2.text, offset2Loc(t1))
    }

    val identifierDeclaration: Parser<AIdentifierDeclaration> by Cursor * alpha use {
        AIdentifierDeclaration(t2.text, offset2Loc(t1))
    }

    val leftHandUnaryPointerExpression by Cursor * derefOp * parser { atom } use {
        AUnaryOp(t2, t3, offset2Loc(t1))
    }

    val assignableExpression: Parser<Assignable<DerefOp>> by identifier or leftHandUnaryPointerExpression

    val zeraryPointerExpression by (Cursor * allocToken) or (Cursor * nullToken) use {
        if (t2.text == allocToken.name) AAlloc(offset2Loc(t1))
        else ANull(offset2Loc(t1))
    }

    val unaryPointerExpression by (Cursor * refOp * identifier) or (Cursor * derefOp * parser { atom }) use {
        AUnaryOp(t2, t3, offset2Loc(t1))
    }

    val pointersExpression: Parser<AExpr> by zeraryPointerExpression or unaryPointerExpression

    val assignment: Parser<AStmtInNestedBlock> by Cursor * assignableExpression * assgn * expression * semicol use {
        AAssignStmt(t2, t4, offset2Loc(t1))
    }

    val block: Parser<AStmtInNestedBlock> by Cursor * -leftBrace * parser { statements } * -rightBrace use {
        ANestedBlockStmt(t2, offset2Loc(t1))
    }

    val declaration: Parser<AVarStmt> by Cursor * varToken * oneOrMore(identifierDeclaration) * semicol use {
        AVarStmt(t3, offset2Loc(t1))
    }

    val whileParser: Parser<AStmtInNestedBlock> by Cursor * whileToken * -leftParen * expression * -rightParen * parser { statement } use {
        AWhileStmt(t3, t4, offset2Loc(t1))
    }

    val ifParser: Parser<AStmtInNestedBlock> by Cursor * ifToken * leftParen * expression * rightParen * parser { statement } *
            optional(elseToken * parser { statement }) use {
        AIfStmt(t4, t6, t7!!.t2, offset2Loc(t1))
    }

    val outputParser: Parser<AStmtInNestedBlock> by Cursor * outputToken * expression * semicol use {
        AOutputStmt(t3, offset2Loc(t1))
    }

    val returnParser by Cursor * returnToken * expression * semicol use { AReturnStmt(t3, offset2Loc(t1)) }

    val errorParser by Cursor * errorToken * expression * semicol use { AErrorStmt(t3, offset2Loc(t1)) }

    val funActualArgs: Parser<List<AExpr>> by -leftParen and zeroOrMore(expression) and -rightParen

    val funApp: Parser<AExpr> by (Cursor * parens * funActualArgs) or (Cursor * identifier * funActualArgs) map { (cur, id, args) ->
        if (id == parens) ACallFuncExpr(id, args, true, offset2Loc(cur))
        else ACallFuncExpr(id, args, false, offset2Loc(cur))
    }

    val statement: Parser<AStmtInNestedBlock> by outputParser or assignment or block or whileParser or ifParser or errorParser

    val statements: Parser<List<AStmtInNestedBlock>> by zeroOrMore(statement)

    val varStatements by zeroOrMore(declaration)

    val funBlock: Parser<AFunBlockStmt> by Cursor * -leftBrace * varStatements * statements * returnParser * -rightBrace use {
        AFunBlockStmt(t2, t3, t4, offset2Loc(t1))
    }

    val tipFunction: Parser<AFunDeclaration> by Cursor * identifier * leftParen * zeroOrMore(identifierDeclaration) *
            rightParen * funBlock use {
        AFunDeclaration(t2.value, t4, t6, offset2Loc(t1))
    }

    val program: Parser<AProgram> by Cursor * zeroOrMore(tipFunction) use {
        AProgram(t2, offset2Loc(t1))
    }
}

fun main() {
    val grammar = TIPGrammar()
    var success = 0
    var failure = 0
    File("examples/").walk().forEach { file ->
        println("\n${file.name}")
        if (file.isFile) {
            val text = file.readText(Charsets.UTF_8)
            println(grammar.tryParseToEnd(text) is Parsed)
            if (grammar.tryParseToEnd(text) is Parsed) success++ else failure++
        }
    }
    println("\nSuccess = $success, failure = $failure")
}