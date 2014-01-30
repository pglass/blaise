package compiler;

public enum TokenType {
    NULL,
    IDENTIFIER,

    MIN_LITERAL,
    INTEGER,
    REAL,
    STRING,
    BOOLEAN,
    MAX_LITERAL,

    MIN_DELIM,
    COMMA(","),
    SEMICOLON(";"),
    COLON(":"),
    LPAREN("("),
    RPAREN(")"),
    LBRACKET("["),
    RBRACKET("]"),
    DOTDOT(".."),
    QUOTE("'"),
    MAX_DELIM,

    MIN_OP,
    PLUS("+"),
    MINUS("-"),
    TIMES("*"),
    DIVIDE("/"),
    ASSIGN(":="),
    EQ("="),
    NE("<>"),
    LT("<"),
    LE("<="),
    GT(">"),
    GE(">="),
    POINTER("^"),
    DOT("."),
    AND("and"),
    OR("or"),
    NOT("not"),
    DIV("div"),
    MOD("mod"),
    IN("in"),
    MAX_OP,

    MIN_KW,
    ARRAY("array"),
    BEGIN("begin"),
    CASE("case"),
    CONST("const"),
    DO("do"),
    DOWNTO("downto"),
    ELSE("else"),
    END("end"),
    FILE("file"),
    FOR("for"),
    FUNCTION("function"),
    GOTO("goto"),
    IF("if"),
    LABEL("label"),
    NIL("nil"),
    OF("of"),
    PACKED("packed"),
    PROCEDURE("procedure"),
    PROGRAM("program"),
    RECORD("record"),
    REPEAT("repeat"),
    SET("set"),
    THEN("then"),
    TO("to"),
    TYPE("type"),
    UNTIL("until"),
    VAR("var"),
    WHILE("while"),
    WITH("with"),
    MAX_KW,

    MIN_PARSE,
    FUNCALL,
    PROGN,
    AREF,
    CASTREAL,
    CASTINT,
    MAX_PARSE;

    public String stringRepr;

    TokenType() {
        this.stringRepr = null;
    }

    TokenType(String str) {
        this.stringRepr = str;
    }
}
