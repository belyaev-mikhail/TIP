package parser

import com.github.h0tk3y.betterParse.lexer.Token
import com.github.h0tk3y.betterParse.lexer.literalToken

public class TokenBetween(name: String?, ignored: Boolean,
                          val openToken: Token,
                          val closeToken: Token,
                          val allowNested: Boolean) : Token(name, ignored) {
    override fun match(input: CharSequence, fromIndex: Int): Int {
        val tryOpening = openToken.match(input, fromIndex)
        if (tryOpening == 0) return 0
        var index = fromIndex + tryOpening
        while (index < input.length) {
            val tryClosing = closeToken.match(input, index)
            if (tryClosing != 0) return index + tryClosing - fromIndex
            if (allowNested) {
                val tryNested = this.match(input, index)
                if (tryNested != 0) index += tryNested
            }
            ++index
        }
        return index // mismatched token
    }
    override fun toString(): String =
        "${name ?: ""} ($openToken ... $closeToken)" + if (ignored) " [ignorable]" else ""
}

public fun tokenBetween(open: Token, close: Token, allowNested: Boolean = false, ignore: Boolean = false): Token {
    return TokenBetween(null, ignore, open, close, allowNested)
}

public fun tokenBetween(open: String, close: String, allowNested: Boolean = false, ignore: Boolean = false): Token {
    return TokenBetween(null, ignore,
        literalToken(open, ignore = ignore),
        literalToken(close, ignore = ignore),
        allowNested
    )
}