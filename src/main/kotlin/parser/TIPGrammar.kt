package parser

import ast.*
import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.grammar.tryParseToEnd
import com.github.h0tk3y.betterParse.lexer.*
import com.github.h0tk3y.betterParse.parser.Parsed
import com.github.h0tk3y.betterParse.parser.Parser
import java.io.File

interface Comments : Parser<AstNode> {
    var lastBreaks: MutableList<Int>

    fun offset2Loc(i: Int): Loc {
        val idx = lastBreaks.indexOfLast { it <= i }
        return if (idx == -1) Loc(1, i - lastBreaks[0] + 1)
        else Loc(idx + 1, i - lastBreaks[idx] + 1)
    }

    val newLine: Parser<TokenMatch>

    val blockComment: Token

    val comment: Parser<Any>
}

class TIPGrammar : Grammar<AstNode>(), Comments {

    val commentToken by regexToken("//[^\n\r]*")
    val blockCommentToken by tokenBetween("/*", "*/", allowNested = true)
    val newLineToken1 by regexToken("\r\n")
    val newLineToken2 by regexToken("\n\r")
    val newLineToken3 by regexToken("\r")
    val newLineToken4 by regexToken("\n")
    val space by regexToken("[ \t]")

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
    val digit by regexToken("\\d+")
    val leftParen by literalToken("(")
    val rightParen by literalToken(")")
    val leftBrace by literalToken("{")
    val rightBrace by literalToken("}")
    val alpha by regexToken("[a-zA-Z]+[a-zA-Z0-9]*")
    val minusToken by literalToken("-")
    val plusToken by literalToken("+")
    val refOpToken by literalToken("&")
    val asterisc by literalToken("*")
    val divToken by literalToken("/")
    val greaterThanToken by literalToken(">")
    val eqqToken by regexToken("==")
    val assgn by literalToken("=")
    val semicol by literalToken(";")
    val comma by literalToken(",")

    override val rootParser by parser { program }

    override var lastBreaks: MutableList<Int> = mutableListOf(0)

    override val newLine by Cursor * (newLineToken1 or newLineToken2 or newLineToken3 or newLineToken4) map { (cur, token) ->
        lastBreaks.add(cur)
        token
    }

    override val blockComment by blockCommentToken

    override val comment by blockComment or commentToken * newLine

    val WS by newLine or space or comment

    val optSpace by zeroOrMore(WS)

    fun wspStr(s: Parser<TokenMatch>) = -optSpace * s * -optSpace

    val minus by wspStr(minusToken) asJust Minus
    val plus by wspStr(plusToken) asJust Plus
    val refOp by wspStr(refOpToken) asJust RefOp
    val times: Parser<Times> by wspStr(asterisc) asJust Times
    val derefOp by wspStr(asterisc) asJust DerefOp
    val divide by wspStr(divToken) asJust Divide
    val greaterThan by wspStr(greaterThanToken) asJust GreaterThan
    val eqq by wspStr(eqqToken) asJust Eqq

    val operation: Parser<AExpr> by parser { term } * optional((Cursor * plus * parser { expression }) or (Cursor * minus * parser { expression })) map { (left, op) ->
        op?.let { (cur, binOp, right) ->
            ABinaryOp(binOp, left, right, offset2Loc(cur))
        } ?: left
    }

    val expression: Parser<AExpr> by parser { operation } * optional((Cursor * greaterThan * parser { operation }) or (Cursor * eqq * parser { operation })) map { (left, op) ->
        op?.let { (cur, binOp, right) ->
            ABinaryOp(binOp, left, right, offset2Loc(cur))
        } ?: left
    }

    val parens: Parser<AExpr> by -wspStr(leftParen) * expression * -wspStr(rightParen)

    val digits: Parser<Int> by optional(minusToken) * digit map { (min, num) ->
        min?.run { num.value.text.toInt() * -1 } ?: num.value.text.toInt()
    }

    val number: Parser<AExpr> by Cursor * digits map { (cur, num) ->
        ANumber(num, offset2Loc(cur))
    }

    val atom by parser { funApp } or number or parens or parser { identifier } or
            (Cursor * wspStr(inputToken) use { AInput(offset2Loc(t1)) }) or parser { pointersExpression }

    val term: Parser<AExpr> by atom * optional((Cursor * times * parser { term }) or (Cursor * divide * parser { term })) map { (left, op) ->
        op?.let { (cur, binOp, right) ->
            ABinaryOp(binOp, left, right, offset2Loc(cur))
        } ?: left
    }

    val identifier: Parser<AIdentifier> by Cursor * -optSpace * alpha map { (cur, id) ->
        AIdentifier(id.text, offset2Loc(cur))
    }

    val identifierDeclaration: Parser<AIdentifierDeclaration> by Cursor * -optSpace * alpha map { (cur, id) ->
        AIdentifierDeclaration(id.text, offset2Loc(cur))
    }

    val leftHandUnaryPointerExpression by Cursor * derefOp * parser { atom } map { (cur, op, expr) ->
        AUnaryOp(op, expr, offset2Loc(cur))
    }

    val assignableExpression: Parser<Assignable<DerefOp>> by identifier or leftHandUnaryPointerExpression

    val zeraryPointerExpression by (Cursor * wspStr(allocToken)) or (Cursor * wspStr(nullToken)) map { (cur, token) ->
        if (token.text == allocToken.name) AAlloc(offset2Loc(cur))
        else ANull(offset2Loc(cur))
    }

    val unaryPointerExpression by (Cursor * refOp * identifier) or (Cursor * derefOp * parser { atom }) map { (cur, op, expr) ->
        AUnaryOp(op, expr, offset2Loc(cur))
    }

    val pointersExpression: Parser<AExpr> by zeraryPointerExpression or unaryPointerExpression

    val assignment: Parser<AStmtInNestedBlock> by Cursor * assignableExpression * -wspStr(assgn) * expression * -wspStr(semicol) map { (cur, left, right) ->
        AAssignStmt(left, right, offset2Loc(cur))
    }

    val block: Parser<AStmtInNestedBlock> by Cursor * -wspStr(leftBrace) * parser { statements } * -wspStr(rightBrace) map { (cur, stmts) ->
        ANestedBlockStmt(stmts, offset2Loc(cur))
    }

    val declaration: Parser<AVarStmt> by Cursor * -wspStr(varToken) * separatedTerms(identifierDeclaration, comma) * -wspStr(semicol) map { (cur, ids) ->
        AVarStmt(ids, offset2Loc(cur))
    }

    val whileParser: Parser<AStmtInNestedBlock> by Cursor * -wspStr(whileToken) * -wspStr(leftParen) * expression * -wspStr(rightParen) *
            parser { statement } map { (cur, expr, stmts) ->
        AWhileStmt(expr, stmts, offset2Loc(cur))
    }

    val ifParser: Parser<AStmtInNestedBlock> by Cursor * -wspStr(ifToken) * -wspStr(leftParen) * expression *
            -wspStr(rightParen) * parser { statement } * optional(-elseToken * parser { statement }) map { (cur, expr, stmts, els) ->
        AIfStmt(expr, stmts, els, offset2Loc(cur))
    }

    val outputParser: Parser<AStmtInNestedBlock> by Cursor * -wspStr(outputToken) * expression * -wspStr(semicol) map { (cur, expr) ->
        AOutputStmt(expr, offset2Loc(cur))
    }

    val returnParser by Cursor * -wspStr(returnToken) * expression * -wspStr(semicol) map { (cur, expr) ->
        AReturnStmt(expr, offset2Loc(cur))
    }

    val errorParser by Cursor * -wspStr(errorToken) * expression * -wspStr(semicol) map { (cur, expr) ->
        AErrorStmt(expr, offset2Loc(cur))
    }

    val funActualArgs by -wspStr(leftParen) and separatedTerms(expression, comma, acceptZero = true) and -wspStr(rightParen)

    val funApp: Parser<AExpr> by (Cursor * parens * funActualArgs) or (Cursor * identifier * funActualArgs) map { (cur, id, args) ->
        if (id == parens) ACallFuncExpr(id, args, true, offset2Loc(cur))
        else ACallFuncExpr(id, args, false, offset2Loc(cur))
    }

    val statement: Parser<AStmtInNestedBlock> by outputParser or assignment or block or whileParser or ifParser or errorParser

    val statements: Parser<List<AStmtInNestedBlock>> by zeroOrMore(statement)

    val varStatements by zeroOrMore(declaration)

    val funBlock: Parser<AFunBlockStmt> by Cursor * -wspStr(leftBrace) * varStatements * statements * returnParser * -wspStr(rightBrace) map { (cur, varStmt, stmts, retrn) ->
        AFunBlockStmt(varStmt, stmts, retrn, offset2Loc(cur))
    }

    val tipFunction: Parser<AFunDeclaration> by Cursor * identifier * -wspStr(leftParen) *
            separatedTerms(identifierDeclaration, comma, true) * -wspStr(rightParen) * funBlock map { (cur, id, args, block) ->
        AFunDeclaration(id.value, args, block, offset2Loc(cur))
    }

    val program: Parser<AProgram> by Cursor * zeroOrMore(tipFunction) map { (cur, funs) ->
        AProgram(funs, offset2Loc(cur))
    }
}

fun main() {
    val grammar = TIPGrammar()
    var success = 0
    var failure = 0
    File("examples/").walk().forEach { file ->
        if (file.isFile && file.name != "code.tip") {
            val text = file.readText()
            grammar.lastBreaks = mutableListOf(0)
            val res = grammar.tryParseToEnd(text)
            println("${file.name} – $res")
            if (res is Parsed) success++
            else {
                failure++
                println(file.name)
            }
        }
    }
    println("\nSuccess = $success, failure = $failure")
}