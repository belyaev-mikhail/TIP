package parser

import com.github.h0tk3y.betterParse.lexer.TokenMatchesSequence
import com.github.h0tk3y.betterParse.parser.ParseResult
import com.github.h0tk3y.betterParse.parser.Parsed
import com.github.h0tk3y.betterParse.parser.Parser

class CursorValue(override val nextPosition: Int) : Parsed<Int>() {
    override val value: Int get() = nextPosition
}

public object Cursor : Parser<Int> {
    override fun tryParse(tokens: TokenMatchesSequence, fromPosition: Int): ParseResult<Int> {
        return CursorValue(fromPosition)
    }

}