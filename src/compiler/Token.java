package compiler;

import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

public class Token<T> {
    public static final Token NULLTOKEN = new Token<Integer>(TokenType.NULL, 0);

    /* Initialize some static information based on the enum */
    private static int FORMAT_WIDTH = -1;
    /* Map to lookup TokenTypes for operators and keywords */
    public static final Hashtable<String, TokenType> TYPE_MAP = new Hashtable<String, TokenType>();
    /* Precedences for the operator precedence parser */
    public static final Hashtable<TokenType, Integer> PRECEDENCES = new Hashtable<TokenType, Integer>();
    static {
        for (TokenType t: TokenType.values()) {
            if (t.stringRepr != null)
                TYPE_MAP.put(t.stringRepr, t);
            Token.FORMAT_WIDTH = Math.max(t.name().length(), FORMAT_WIDTH);
        }
        Token.FORMAT_WIDTH += 1;
        PRECEDENCES.put(TokenType.ASSIGN,  1);
        PRECEDENCES.put(TokenType.EQ,      2);
        PRECEDENCES.put(TokenType.NE,      2);
        PRECEDENCES.put(TokenType.LT,      2);
        PRECEDENCES.put(TokenType.LE,      2);
        PRECEDENCES.put(TokenType.GT,      2);
        PRECEDENCES.put(TokenType.GE,      2);
        PRECEDENCES.put(TokenType.IN,      2);
        PRECEDENCES.put(TokenType.PLUS,    3);
        PRECEDENCES.put(TokenType.MINUS,   3);
        PRECEDENCES.put(TokenType.OR,      3);
        PRECEDENCES.put(TokenType.TIMES,   4);
        PRECEDENCES.put(TokenType.DIVIDE,  4);
        PRECEDENCES.put(TokenType.AND,     4);
        PRECEDENCES.put(TokenType.DIV,     4);
        PRECEDENCES.put(TokenType.MOD,     4);
        PRECEDENCES.put(TokenType.NOT,     5);
        PRECEDENCES.put(TokenType.DOT,     6);
        PRECEDENCES.put(TokenType.POINTER, 6);
        PRECEDENCES.put(TokenType.FUNCALL, 7);
    }

	public TokenType type;
    public T value;
    public LinkedList<Token> children;
    public Symbol.TypeSymbol datatype;   // the type of the expression
    public Symbol symbolTableEntry;      // for a function or variable

    private void init(TokenType type,
                      T value,
                      LinkedList<Token> children,
                      Symbol.TypeSymbol datatype,
                      Symbol symbolTableEntry) {
        this.type = type;
        this.value = value;
        this.children = children;
        this.datatype = datatype;
        this.symbolTableEntry = symbolTableEntry;
    }

	public Token(TokenType type) {
        this.init(type, null, new LinkedList<Token>(), Symbol.NULLSYMBOL, Symbol.NULLSYMBOL);
	}

    /** Copy constructor */
    public Token(Token<T> tok) {
        this.init(tok.type, tok.value, new LinkedList<Token>(), tok.datatype, tok.symbolTableEntry);
        this.children.addAll(tok.children);
    }

    public Token(TokenType type, T value) {
        if (value instanceof Token) {   // value is not allowed to be type Token
            this.init(type, null, new LinkedList<Token>(), Symbol.NULLSYMBOL, Symbol.NULLSYMBOL);
            this.children.add((Token) value);
        } else {
            this.init(type, value, new LinkedList<Token>(), Symbol.NULLSYMBOL, Symbol.NULLSYMBOL);
        }
    }

    public Token(TokenType type, List<Token> children) {
        this.init(type, null, new LinkedList<Token>(), Symbol.NULLSYMBOL, Symbol.NULLSYMBOL);
        this.children.addAll(children);
    }

    public Token(TokenType type, Token... children) {
        this.init(type, null, new LinkedList<Token>(), Symbol.NULLSYMBOL, Symbol.NULLSYMBOL);
        Collections.addAll(this.children, children);
    }

    /** Get the precedence of this Token
     *
     * @return An integer representing the precedence of this Token,
     * which is null if the token is not an operator
     * @see Token#PRECEDENCES
     */
    public Integer precedence() {
        return PRECEDENCES.get(this.type);
    }

    /** Test the type of this Token
     *
     * @param types Any number of {@link TokenType} instances
     * @return True only if the type of this Token matches any of the given types
     */
    public boolean isType(TokenType... types) {
        for (TokenType t: types)
            if (t.ordinal() == this.type.ordinal())
                return true;
        return false;
    }

    public Token getChild(int index) {
        return this.children.get(index);
    }

    /** Returns true if this token has type {@link TokenType#NULL} */
    public boolean isNull() {
        return this.isType(TokenType.NULL);
    }

	boolean isOperator() {
		return TokenType.MIN_OP.ordinal() < this.type.ordinal() 
			&& this.type.ordinal() < TokenType.MAX_OP.ordinal();
	}
	
	boolean isLiteral() {
		return TokenType.MIN_LITERAL.ordinal() < this.type.ordinal()
				&& this.type.ordinal() < TokenType.MAX_LITERAL.ordinal();
	}
	
	boolean isDelimiter() {
		return TokenType.MIN_DELIM.ordinal() < this.type.ordinal() 
				&& this.type.ordinal() < TokenType.MAX_DELIM.ordinal();
	}

    public static String padString(String s, int width) {
        if (width-1 > 0)
            return String.format("%1$-" + (width-1) + "s", s);
        return "";
    }

    public String toExprString() {
        return this.toExprString(0, false);
    }

    public String toExprString(int indent, boolean singleLine) {
        String s = padString(" ", indent) + "(";
        s += this.type.name();
        if (this.value != null)
            s += " " + this.value.toString();
        if (this.datatype != null)
            s += " type=" + this.datatype.toSimpleString();
        for (Token t: this.children) {
            if (singleLine)
                s += " ";
            else
                s += "\n";
            s += t.toExprString(indent + 4, singleLine);
        }
        s += ")";
        return s;
    }

	public String toString() {
        String s = padString(this.type.name(), FORMAT_WIDTH);
        if (this.value != null) {
            if (this.type == TokenType.STRING)
                s += " : '" + this.value.toString() + "'";
            else
                s += " : " + this.value.toString();
        }
        return s;
	}

    public static TokenType lookupTokenType(String str) {
        return Token.TYPE_MAP.get(str);
    }
}
