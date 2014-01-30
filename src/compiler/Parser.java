package compiler;

import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.List;

public class Parser {

    private static final boolean DEBUG = false;

    public ParserUtil.LabelList labelList;
    public SymbolTable symbolTable;
    private Lexer lexer;
    private Token savedToken;

    /**
     * Construct a new Parser
     *
     * @param filename A (Pascal) source file
     * @throws FileNotFoundException
     */
    public Parser(String filename) throws FileNotFoundException {
        this.lexer = new Lexer(filename);
        this.labelList = new ParserUtil.LabelList();
        this.symbolTable = new SymbolTable();
        this.savedToken = Token.NULLTOKEN;
    }

    public void printSymbolTable(int level) {
        if (level == 0)
            System.out.println(this.symbolTable.levelZeroString());
        else if (level == 1)
            System.out.println(this.symbolTable.levelOneString());
    }

    private void logMessage(String message) {
        System.out.println(message);
    }

    private void logInternalError(String message) {
        this.logMessage("INTERNAL ERROR: " + message);
    }

    private void logError(String message) {
        this.logMessage("ParseError: " + message);
    }

    private void logWarning(String message) {
//        this.logMessage("Warning: " + message);
    }

    /**
     * Parse the source file.
     *
     * @return A Token that represents the root of an abstract syntax tree
     * which can be traversed by a code generator.
     */
    public Token parse() {
        return this.parseProgram();
    }

    /**
     * Parse the beginning of the source file: PROGRAM ID(ID); BLOCK.
     *
     * @return A Token that represents the root of an abstract syntax tree.
     */
    private Token parseProgram() {
        Token program = this.expectNextToken(TokenType.PROGRAM);
        Token name = this.expectNextToken(TokenType.IDENTIFIER);
        this.expectNextToken(TokenType.LPAREN);
        Token stream = this.expectNextToken(TokenType.IDENTIFIER);
        this.expectNextToken(TokenType.RPAREN);
        this.expectNextToken(TokenType.SEMICOLON);
        Token block = this.parseBlock();
        this.expectNextToken(TokenType.DOT);
        program.children.add(name);
        program.children.add(stream);
        program.children.add(block);
        return program;
    }

    /**
     * Parse a block: LABELS; CONSTS; TYPES; VARS; begin...end;
     * This installs all definitions (labels, consts, types, vars) into
     * an internal data structure (the label list or symbol table).
     *
     * @return A Token representing the abstract syntax of the BEGIN...END block
     */
    private Token parseBlock() {
        this.readLabelSection();
        this.readConstSection();
        this.readTypeSection();
        this.readVarSection();
        // this.readFunctions();
        return this.parseBegin();
    }

    /**
     * Parse all user-defined labels and add them to the internal label list:
     * {@code label NUM1, NUM2, ..., NUMN; }
     */
    private void readLabelSection() {
        if (this.peekToken().isType(TokenType.LABEL)) {
            this.nextToken();
            this.readLabelNumber();
            while (this.peekToken().isType(TokenType.COMMA)) {
                this.nextToken();
                this.readLabelNumber();
            }
            this.expectNextToken(TokenType.SEMICOLON);
        }
    }

    /**
     * Consume an integer token and install it in the internal label list
     */
    private void readLabelNumber() {
        Token label = this.expectNextToken(TokenType.INTEGER);
        this.labelList.addUserLabel(label);
    }

    /**
     * Parse all const definitions: {@code const DEF1; DEF2; ...; DEFN;}
     */
    private void readConstSection() {
        if (this.peekToken().isType(TokenType.CONST)) {
            this.nextToken();
            do {
                this.readConstDefinition();
            } while (this.peekToken().isType(TokenType.IDENTIFIER));
        }
    }

    /**
     * Parse a single const definition: {@code ID = VALUE;} and install in the symbol table.
     */
    private void readConstDefinition() {
        Token id = this.expectNextToken(TokenType.IDENTIFIER);
        Token eq = this.expectNextToken(TokenType.EQ);
        Token value = this.expectNextToken(TokenType.INTEGER, TokenType.REAL, TokenType.STRING, TokenType.BOOLEAN);
        this.installConstValue(id, value);
        this.expectNextToken(TokenType.SEMICOLON);
    }

    /**
     * Install a const variable in the symbol table. The const variables are defined
     * before composite types are defined, so we know the value must be a basic type
     * (integer, real, char, or boolean)
     *
     * @param id    A Token containing a String with the const variable name
     * @param value The value of the const variable (integer, real, char, or boolean)
     */
    private void installConstValue(Token<String> id, Token value) {
        try {
            Symbol.BasicTypeSymbol typeSymbol = this.symbolTable.getBasicTypeOf(value);
            Symbol.ConstSymbol constSymbol = new Symbol.ConstSymbol(id.value, value.value, typeSymbol);
            this.symbolTable.insert(id.value, constSymbol);
        } catch (SymbolTable.SymbolTableException e) {
            this.logError(e.getMessage());
        }
    }

    /**
     * Parse the section of type definitions: {@code type DEF1; DEF2; ...; DEFN; }
     * and install them in the symbol table.
     *
     * @see Parser#installType(Token, compiler.Symbol.TypeSymbol)
     */
    private void readTypeSection() {
        if (this.peekToken().isType(TokenType.TYPE)) {
            this.expectNextToken(TokenType.TYPE);
            do {
                this.readTypeDefinition();
            } while (this.peekToken().isType(TokenType.IDENTIFIER));
        }
    }

    /**
     * Parse a single type definition {@code ID = TYPE;}
     */
    private void readTypeDefinition() {
        Token<String> id = this.expectNextToken(TokenType.IDENTIFIER);
        this.expectNextToken(TokenType.EQ);
        Symbol.TypeSymbol type = this.readType();
        this.expectNextToken(TokenType.SEMICOLON);
        this.installType(id, type);
    }

    /**
     * Read the right hand side of the equal sign in a type definition, or
     * after a colon in a variable definition.
     *
     * @return A {@link Symbol.TypeSymbol} instance. This may represent an array,
     * record, pointer, subrange, enum, or another type identifier.
     * @see Parser#readTypeDefinition()
     */
    private Symbol.TypeSymbol readType() {
        Symbol.TypeSymbol result = null;
        Token peek = this.peekToken();
        if (peek.isType(TokenType.ARRAY)) {
            result = this.readArrayType();
        } else if (peek.isType(TokenType.RECORD)) {
            result = this.readRecordType();
        } else if (peek.isType(TokenType.POINTER)) {
            result = this.readPointerType();
        } else {
            result = this.readSimpleType();
        }
        return result;
    }


    /**
     * Install a type in the symbol table. Forward declarations are a complication
     * here. Consider the following:
     * <div style="margin-left: 2em" >
     * {@code pp = ^person;}<br>
     * {@code person = record ...; }
     * </div>
     * When we see type pp, person is not yet in the symbol table. We use the
     * method {@link compiler.SymbolTable#lookupOrInsertType(String)} to create
     * a "stub" {@link compiler.Symbol.StubWithTypeSymbol} with its type symbol set to null.
     * Therefore, we must check the symbol table before
     *
     * @param idToken    A Token containing a String with the name of the type
     * @param typeSymbol A Symbol.TypeSymbol that defines the type. This may represent a
     *                   record, array, pointer, enum, subrange, or another type id.
     */
    private void installType(Token<String> idToken, Symbol.TypeSymbol typeSymbol) {
        Symbol symbolTableEntry = this.symbolTable.lookupOrInsertType(idToken.value);
        if (symbolTableEntry instanceof Symbol.BasicTypeSymbol) {
            this.logError("Cannot define type named '" + symbolTableEntry.name + "' which is a basic type");
        } else if (symbolTableEntry instanceof Symbol.StubWithTypeSymbol) {
            Symbol.StubWithTypeSymbol stubSymbol = (Symbol.StubWithTypeSymbol) symbolTableEntry;
            if (stubSymbol.typeSymbol instanceof Symbol.NullTypeSymbol) {
                stubSymbol.setTypeSymbol(typeSymbol);
            } else {
                this.logError("Cannot redefine type " + stubSymbol.name + " which was " +
                        stubSymbol.typeSymbol.name + " to be " + typeSymbol.name);
            }
        } else {
            this.logInternalError("Typename '" + idToken.value + "' is mapped to" +
                    "non TypeSymbol type " + symbolTableEntry.getClass().getName());
        }
    }

    /**
     * Read a pointer in a type definition: {@code ^ TYPEID}.
     *
     * @return A {@link Symbol.PointerSymbol} instance which contains a
     * {@link Symbol.TypeSymbol} which is the type pointed to.
     */
    private Symbol.PointerSymbol readPointerType() {
        this.expectNextToken(TokenType.POINTER);
        Symbol.TypeSymbol typeSymbol = this.readTypeIdentifier();
        return new Symbol.PointerSymbol(typeSymbol);
    }

    /**
     * Read an identifier token, assuming it is the name of a type on the
     * right-hand side of the equals sign in a type definition.
     * We use {@link compiler.SymbolTable#lookupOrInsertType(String)}
     * to find the type pointed to in the symbol table in case the type pointed
     * to is a forward declaration.
     *
     * @return The {@link Symbol.TypeSymbol} associated with the
     * identifier in the symbol table
     */
    private Symbol.TypeSymbol readTypeIdentifier() {
        Token<String> id = this.expectNextToken(TokenType.IDENTIFIER);
        Symbol symbol = this.symbolTable.lookupOrInsertType(id.value);
        if (symbol instanceof Symbol.TypeSymbol) {
            return (Symbol.TypeSymbol) symbol;
        } else {
            this.logError("Cannot redefine id '" + id.value + "' which is has type "
                    + symbol.toSimpleString() + " as a type");
            return Symbol.NULLSYMBOL;
        }
    }

    /**
     * Read an array in a type definition:
     * {@code array[SIMPLETYPE, SIMPLETYPE, ...] of TYPE} where each simple type
     * is something read by {@link compiler.Parser#readSimpleType()}.
     *
     * @return A {@link Symbol.ArraySymbol} instance
     */
    private Symbol.ArraySymbol readArrayType() {
        this.expectNextToken(TokenType.ARRAY);
        this.expectNextToken(TokenType.LBRACKET);
        LinkedList<Symbol.TypeSymbol> ranges = new LinkedList<Symbol.TypeSymbol>();
        Symbol.TypeSymbol range = this.readSimpleType();
        ranges.add(range);
        while (this.peekToken().isType(TokenType.COMMA)) {
            this.nextToken();
            range = this.readSimpleType();
            ranges.add(range);
        }
        this.expectNextToken(TokenType.RBRACKET);
        this.expectNextToken(TokenType.OF);
        Symbol.TypeSymbol containedType = this.readType();
        try {
            return ParserUtil.makeArraySymbol(ranges, containedType);
        } catch (ParserUtil.TypeMismatchException e) {
            this.logError(e.getMessage());
        }
        return null;
    }

    /**
     * Read a type identifier, an enum, or a subrange found on the right-hand
     * side of the equals sign in a type definition.
     *
     * @return A {@link Symbol.TypeSymbol} representing a subrange, or the symbol
     * associated with the identifier from the symbol table.
     * @see compiler.Parser#readEnum()
     * @see compiler.Parser#readSubrange()
     */
    private Symbol.TypeSymbol readSimpleType() {
        Token peek = this.peekToken();
        if (peek.isType(TokenType.IDENTIFIER)) {             // another type
            return this.readTypeIdentifier();
        } else if (peek.isType(TokenType.LPAREN)) {          // an enum
            return this.readEnum(); // returns a SubrangeSymbol
        } else if (peek.isType(TokenType.INTEGER)) {  // a subrange
            return this.readSubrange();
        } else {
            this.logError("Bad type declaration. Found no parseable type value.");
        }
        return null;
    }

    /**
     * Read a subrange: {@code INTEGER .. INTEGER}
     *
     * @return A {@link Symbol.SubrangeSymbol}
     */
    private Symbol.SubrangeSymbol readSubrange() {
        Token<Integer> lowToken = this.expectNextToken(TokenType.INTEGER);
        this.expectNextToken(TokenType.DOTDOT);
        Token<Integer> highToken = this.expectNextToken(TokenType.INTEGER);
        return new Symbol.SubrangeSymbol(lowToken.value, highToken.value);
    }

    /**
     * Read an enum: {@code (ID1, ID2, ..., IDN)}. This defines each identifier
     * as an integer starting from zero: That is, ID1 = 0, ID2 = 1, ..., IDN = N-1.
     * Otherwise, the expression is equivalent to a subrange {@code 0..N-1}.
     *
     * @return A {@link Symbol.SubrangeSymbol}
     */
    private Symbol.SubrangeSymbol readEnum() {
        int count = 0;
        this.expectNextToken(TokenType.LPAREN);
        Token id = this.expectNextToken(TokenType.IDENTIFIER);
        Token value = this.makeIntegerToken(count++);
        this.installConstValue(id, value);
        while (this.peekToken().isType(TokenType.COMMA)) {
            this.nextToken();
            id = this.expectNextToken(TokenType.IDENTIFIER);
            value = new Token<Integer>(TokenType.INTEGER, count++);
            this.installConstValue(id, value);
        }
        this.expectNextToken(TokenType.RPAREN);
        return new Symbol.SubrangeSymbol(0, count - 1);
    }

    /**
     * Read a record: {@code record FIELDLIST end;}.
     *
     * @return A {@link Symbol.RecordSymbol} instance
     * @see compiler.Parser#readFieldList()
     */
    private Symbol.RecordSymbol readRecordType() {
        this.expectNextToken(TokenType.RECORD);
        Symbol.RecordSymbol recordSymbol = new Symbol.RecordSymbol(this.readFieldList());
        this.expectNextToken(TokenType.END);
        return recordSymbol;
    }

    /**
     * Read the fields of a record: {@code FIELD1; FIELD2; ...; FIELDN end;}
     * Notice the location of the final semicolon, and be aware that records
     * are allowed to have no fields.
     *
     * @return An array of {@link Symbol.FieldSymbol} instances
     * (which may have length zero)
     * @see compiler.Parser#readRecordType()
     * @see compiler.Parser#readRecordField()
     */
    private Symbol.FieldSymbol[] readFieldList() {
        if (this.peekToken().isType(TokenType.IDENTIFIER)) {
            List<Symbol.FieldSymbol> result = new LinkedList<Symbol.FieldSymbol>();
            List<Symbol.FieldSymbol> fields = this.readRecordField();
            result.addAll(fields);
            while (this.peekToken().isType(TokenType.SEMICOLON)) {
                this.nextToken();
                fields = this.readRecordField();
                if (fields.size() != 0) {
                    result.addAll(fields);
                } else {
                    this.logError("Expected more fields after semicolon in record");
                }
            }
            return ParserUtil.toFieldSymbolArrayWithOffsets(result);
        }
        return new Symbol.FieldSymbol[0];
    }

    /**
     * Read a definition of record fields of a particular type:
     * {@code IDLIST : TYPEID}.
     *
     * @return A {@link List} of {@link Symbol.FieldSymbol}.
     * @see compiler.Parser#readFieldList()
     */
    private List<Symbol.FieldSymbol> readRecordField() {
        if (this.peekToken().isType(TokenType.IDENTIFIER)) {
            List<Token<String>> idTokens = this.readIdList();
            this.expectNextToken(TokenType.COLON);
            Symbol.TypeSymbol type = this.readType();
            LinkedList<Symbol.FieldSymbol> fieldSymbols = new LinkedList<Symbol.FieldSymbol>();
            for (Token<String> id : idTokens) {
                fieldSymbols.add(new Symbol.FieldSymbol(id.value, type, 0)); // compute the offsets later
            }
            return fieldSymbols;
        }
        return new LinkedList<Symbol.FieldSymbol>();
    }

    /**
     * Read a comma separated list of identifiers: {@code ID, ID, ..., ID}
     *
     * @return A {@link List} of {@link Token} instances containing the identifier strings
     */
    private List<Token<String>> readIdList() {
        LinkedList<Token<String>> idTokens = new LinkedList<Token<String>>();
        idTokens.add(this.nextToken());
        while (this.peekToken().isType(TokenType.COMMA)) {
            this.nextToken();
            idTokens.add(this.expectNextToken(TokenType.IDENTIFIER));
        }
        return idTokens;
    }

    /**
     * Read a section of variable definitions: {@code var DEF1; DEF2; ...; DEFN;}
     */
    private void readVarSection() {
        if (this.peekToken().isType(TokenType.VAR)) {
            this.nextToken();
            do {
                this.readVarDefinition();
                this.expectNextToken(TokenType.SEMICOLON);
            } while (this.peekToken().isType(TokenType.IDENTIFIER));
        }
    }

    /**
     * Read a variable definition: {@code IDLIST : TYPE}. At this point, the
     * type section has ended, so all type names must be in the symbol table.
     */
    private void readVarDefinition() {
        List<Token<String>> idList = this.readIdList();
        this.expectNextToken(TokenType.COLON);
        Symbol.TypeSymbol typeSymbol = this.readType();
        for (Token<String> id : idList) {
            this.installVariable(id, typeSymbol);
        }
    }

    /**
     * Install a variable name in the symbol table. This prints an error
     * if the variable's name is already installed in the symbol table. That is,
     * we do not allow variables to have the same name as a type, and we do not
     * allow redeclarations of variables.
     *
     * @param id         A {@link Token} containing the variable identifier
     * @param typeSymbol A {@link Symbol.TypeSymbol} that represents the type
     */
    private void installVariable(Token<String> id, Symbol.TypeSymbol typeSymbol) {
        Symbol symbolTableEntry = this.symbolTable.lookup(id.value);
        if (symbolTableEntry == null) {
            try {
                this.symbolTable.insert(id.value, new Symbol.VarSymbol(id.value, typeSymbol));
            } catch (SymbolTable.SymbolTableException e) {
                this.logError(e.getMessage());
            }
        } else if (symbolTableEntry instanceof Symbol.TypeSymbol) {
            this.logError("Cannot declare variable name '" + id.value + "' which " +
                    "is declared as a type");
        } else if (symbolTableEntry instanceof Symbol.VarSymbol) {
            this.logError("Cannot redefine variable '" + id.value + "'");
        } else {
            this.logInternalError("Unhandled case in Parser while installing a variable");
        }
    }

    /**
     * Parse a begin...end block: {@code begin STATEMENT; STATEMENT; ...; STATEMENT; end}.
     *
     * @return A {@link Token} with type {@link TokenType#PROGN}. Each statement is parsed
     * to a Token as abstract syntax and placed in the {@code children} list of this returned Token.
     */
    private Token parseBegin() {
        List<Token> tokenList = new LinkedList<Token>();
        this.expectNextToken(TokenType.BEGIN);
        Token token = this.parseStatement();
        tokenList.add(token);
        while (this.peekToken().isType(TokenType.SEMICOLON)) {
            this.nextToken();
            token = this.parseStatement();
            tokenList.add(token);
        }
        this.expectNextToken(TokenType.END);
        return new Token(TokenType.PROGN, tokenList);
    }

    /**
     * Parse a statment: {@code [LABEL:] ASSIGNMENT | BEGIN | IF_THEN_ELSE |
     * CASE | WHILE | REPEAT | FOR | WITH | GOTO}. That is, an optional label followed
     * by one of the kinds of statements.
     *
     * @return A Token representing the abstract syntax of the parsed code.
     */
    private Token parseStatement() {
        Token peek = this.peekToken();
        if (peek.isType(TokenType.INTEGER)) {    // check for a label number
            int labelNumber = this.labelList.lookupUserLabel(this.nextToken());
            Token<Integer> labelToken = this.makeLabelToken(labelNumber);
            this.expectNextToken(TokenType.COLON);
            Token result = new Token(TokenType.PROGN);
            result.children.add(labelToken);
            result.children.add(this.parseStatement());
            return result;
        }
        if (peek.isType(TokenType.IDENTIFIER)) {
            return this.parseExpr();
        } else if (peek.isType(TokenType.BEGIN)) {
            return this.parseBegin();
        } else if (peek.isType(TokenType.IF)) {
            return this.parseIfStatement();
        } else if (peek.isType(TokenType.WHILE)) {
            return this.parseWhileStatement();
        } else if (peek.isType(TokenType.REPEAT)) {
            return this.parseRepeatStatement();
        } else if (peek.isType(TokenType.FOR)) {
            return this.parseForStatement();
        } else if (peek.isType(TokenType.GOTO)) {
            return this.parseGotoStatement();
        } else {
            return Token.NULLTOKEN;
        }
    }

    private Token<Integer> makeLabelToken(int labelNumber) {
        return new Token<Integer>(TokenType.LABEL, labelNumber);
    }

    private Token<Integer> makeGotoToken(int labelNumber) {
        return new Token<Integer>(TokenType.GOTO, labelNumber);
    }

    /**
     * Parse a goto statement: {@code goto LABELNUMBER}
     *
     * @return A Token of type {@link TokenType#GOTO} with the label number as a child
     */
    private Token parseGotoStatement() {
        this.expectNextToken(TokenType.GOTO);
        Token<Integer> numberToken = this.expectNextToken(TokenType.INTEGER);
        return this.makeGotoToken(this.labelList.lookupUserLabel(numberToken));
    }

    /**
     * Parse an if statement {@code if EXPR then STATEMENT [else STATEMENT]},
     * where the else clause is optional.
     *
     * @return A Token of type {@link TokenType#IF}.
     * @see Parser#makeIfStatement(Token, Token, Token)
     */
    private Token parseIfStatement() {
        this.expectNextToken(TokenType.IF);
        Token condition = this.parseExpr();
        this.expectNextToken(TokenType.THEN);
        Token thenStatement = this.parseStatement();
        if (this.peekToken().isType(TokenType.ELSE)) {
            Token elseStatement = this.parseStatement();
            return this.makeIfStatement(condition, thenStatement, elseStatement);
        } else {
            return this.makeIfStatement(condition, thenStatement, null);
        }
    }

    /**
     * Construct abstract syntax representing an if statement.
     *
     * @param condition
     * @param thenStatement
     * @param elseStatement Set this to null to handle if statements without else clauses
     * @return A Token of type {@link TokenType#IF} with a list of children
     * that looks like: {@code {condition, thenStatement}} (if no else clause) or
     * {@code {condition, thenStatement, elseStatement}}.
     */
    private Token makeIfStatement(Token condition, Token thenStatement, Token elseStatement) {
        Token result = new Token(TokenType.IF);
        result.children.add(condition);
        result.children.add(thenStatement);
        if (elseStatement != null)
            result.children.add(elseStatement);
        return result;
    }

    /**
     * Parse a repeat..until style loop: {@code repeat BODY until ENDEXPR}
     *
     * @return A Token of type {@link TokenType#PROGN} which contains
     * a list of statements in abstract syntax
     * @see Parser#makeRepeat(Token, Token)
     */
    private Token parseRepeatStatement() {
        this.expectNextToken(TokenType.REPEAT);
        LinkedList<Token> statementList = new LinkedList<Token>();
        Token statement = this.parseStatement();
        statementList.add(statement);
        while (this.peekToken().isType(TokenType.SEMICOLON)) {
            this.nextToken();
            statementList.add(this.parseStatement());
        }
        Token statementsToken = new Token(TokenType.PROGN, statementList);
        this.expectNextToken(TokenType.UNTIL);
        Token endExpr = this.parseExpr();
        return this.makeRepeat(statementsToken, endExpr);
    }

    /**
     * Construct a abstract syntax representing an repeat loop
     *
     * @return A Token of type {@link TokenType#PROGN} which contains
     * a list of statements in abstract syntax
     * @see compiler.Parser#parseIfStatement()
     */
    private Token makeRepeat(Token body, Token endExpr) {
        int labelNumber = this.labelList.getNextLabelNumber();
        Token labelToken = this.makeLabelToken(labelNumber);
        Token gotoToken = this.makeGotoToken(labelNumber);
        Token ifToken = this.makeIfStatement(endExpr, new Token(TokenType.PROGN), gotoToken);
        return new Token(TokenType.PROGN, labelToken, body, ifToken);
    }

    /**
     * Parse a while loop: {@code while EXPR do BODY}
     *
     * @return A Token of type {@link TokenType#PROGN}
     * @see Parser#makeWhileStatement(Token, Token)
     */
    private Token parseWhileStatement() {
        this.expectNextToken(TokenType.WHILE);
        Token condition = this.parseExpr();
        this.expectNextToken(TokenType.DO);
        Token body = this.parseStatement();
        return this.makeWhileStatement(condition, body);
    }

    /**
     * Construct abstract syntax representing a while loop.
     *
     * @return A Token of type {@link TokenType#PROGN} which contains
     * the list of abstract syntax statements representing the loop
     * @see compiler.Parser#parseWhileStatement()
     */
    private Token makeWhileStatement(Token condition, Token body) {
        int labelNumber = this.labelList.getNextLabelNumber();
        Token<Integer> labelToken = this.makeLabelToken(labelNumber);
        Token gotoToken = this.makeGotoToken(labelNumber);
        body.children.add(gotoToken);
        Token ifTok = this.makeIfStatement(condition, body, null);
        return new Token(TokenType.PROGN, labelToken, ifTok);
    }

    /**
     * Parse a for loop: {@code for VAR := STARTEXPR (to|downto) ENDEXPR do BODY}.
     *
     * @return A Token of type {@link TokenType#PROGN}
     */
    private Token parseForStatement() {
        this.expectNextToken(TokenType.FOR);
        Token var = this.expectNextToken(TokenType.IDENTIFIER);
        this.setIdentifierSymbols(var);
        this.expectNextToken(TokenType.ASSIGN);
        Token initExpr = this.parseExpr();
        boolean downTo = false;
        if (this.peekToken().isType(TokenType.DOWNTO)) {
            this.nextToken();
            downTo = true;
        } else if (this.peekToken().isType(TokenType.TO)) {
            this.nextToken();
            downTo = false;
        } else {
            this.logError("Expected either TO or DOWNTO in for loop, not \"" + this.peekToken().toString() + "\"");
        }
        Token endExpr = this.parseExpr();
        this.expectNextToken(TokenType.DO);
        Token body = this.parseStatement();
        return this.makeForStatement(var, initExpr, endExpr, body, downTo);
    }

    private Token<Integer> makeIntegerToken(int value) {
        Token<Integer> result = new Token<Integer>(TokenType.INTEGER, value);
        result.datatype = (Symbol.TypeSymbol) this.symbolTable.lookup("integer");
        return result;
    }

    /**
     * Construct the abstract syntax representing a for loop.
     *
     * @param var       The variable of iteration
     * @param initValue The initial value of the variable of iteration
     * @param endValue  The value of the variable of iteration which determines when the loop ends.
     *                  In Pascal, the conditional is inclusive of this value. That is, the condition
     *                  tested is {@code i <= endValue} or {@code i >= endValue} depending on an
     *                  increasing or decreasing iteration variable.
     * @param body      The body of the loop
     * @param isDownTo  True if the variable of iteration decreases each iteration
     * @return A Token of type {@link TokenType#PROGN} whose children are the statements
     * representing the loop.
     */
    private Token makeForStatement(Token var, Token initValue, Token endValue, Token body, boolean isDownTo) {
        Token initExpr = this.makeBinaryExpression(new Token(TokenType.ASSIGN), new Token(var), initValue);
        int labelNumber = this.labelList.getNextLabelNumber();
        Token labelToken = this.makeLabelToken(labelNumber);
        Token gotoToken = this.makeGotoToken(labelNumber);
        // construct abstract syntax based on whether the loop counts up or counts down
        Token endExpr, updateValue;
        if (isDownTo) {
            endExpr = this.makeBinaryExpression(new Token(TokenType.GE), new Token(var), endValue);
            updateValue = this.makeBinaryExpression(new Token(TokenType.MINUS), new Token(var), this.makeIntegerToken(1));
        } else {
            endExpr = this.makeBinaryExpression(new Token(TokenType.LE), new Token(var), endValue);
            updateValue = this.makeBinaryExpression(new Token(TokenType.PLUS), new Token(var), this.makeIntegerToken(1));
        }
        // e.g. (:= i (+ i 1))
        Token updateExpr = this.makeBinaryExpression(new Token(TokenType.ASSIGN), new Token(var), updateValue);
        // (progn (:= i 0)
        //        (label 1)
        //        (if (<= i limit)
        //            (<body>)
        //            (:= i (+ i 1))
        //            (goto 1)))
        Token thenClause = new Token(TokenType.PROGN, body, updateExpr, gotoToken);
        Token ifToken = this.makeIfStatement(endExpr, thenClause, null);
        return new Token(TokenType.PROGN, initExpr, labelToken, ifToken);
    }

    /**
     * This finds the {@link Symbol.BasicTypeSymbol} from the symbol table based
     * on the {@link Token#type} of the given token. This symbol is stored in the
     * {@link Token#datatype} attribute of the token.
     * <p/>
     * This works only for the basic types integer, real, string, or boolean.
     */
    private void setBasicDatatype(Token token) {
        if (token.isType(TokenType.INTEGER)) {
            token.datatype = (Symbol.TypeSymbol) this.symbolTable.lookup("integer");
        } else if (token.isType(TokenType.REAL)) {
            token.datatype = (Symbol.TypeSymbol) this.symbolTable.lookup("real");
        } else if (token.isType(TokenType.STRING)) {
            token.datatype = (Symbol.TypeSymbol) this.symbolTable.lookup("char");
        } else if (token.isType(TokenType.BOOLEAN)) {
            token.datatype = (Symbol.TypeSymbol) this.symbolTable.lookup("boolean");
        }
    }

    /**
     * This looks up the identifier in the symbol table and sets the {@link Token#datatype} and
     * {@link Token#symbolTableEntry} of the token. We want to be able to throw away the symbol
     * table at the end of parsing, so we need every Token in the abstract syntax tree to have
     * these two attributes set for the code generator. Furthermore, we never assign {@code null}
     * to either of these attributes but use {@link Symbol#NULLSYMBOL} instead (this avoids
     * having to check for null in various places).
     * <p/>
     * Forward declarations complicate this a bit. We use the function
     * {@link ParserUtil#resolveExprTypeSymbol(compiler.Symbol.TypeSymbol)}
     * to ensure that the {@link Token#datatype} field is never set to be an instance of
     * {@link Symbol.StubWithTypeSymbol} (instantiated when we see a type which is not
     * yet defined in the Pascal source) but instead set to the actual contained type.
     *
     * @param idToken A token containing the identifier for a type, variable, or function
     * @see ParserUtil#resolveExprTypeSymbol(compiler.Symbol.TypeSymbol)
     */
    private void setIdentifierSymbols(Token<String> idToken) {
        Symbol symbol = this.symbolTable.lookup(idToken.value);
        if (symbol == null) {
            idToken.datatype = Symbol.NULLSYMBOL;
            idToken.symbolTableEntry = Symbol.NULLSYMBOL;
        } else if (symbol instanceof Symbol.FunctionSymbol) {
            idToken.type = TokenType.FUNCALL;
            idToken.datatype = ParserUtil.resolveExprTypeSymbol(((Symbol.FunctionSymbol) symbol).resultType);
            idToken.symbolTableEntry = symbol;
        } else if (symbol instanceof Symbol.VarSymbol) {
            idToken.datatype = ParserUtil.resolveExprTypeSymbol(((Symbol.VarSymbol) symbol).typeSymbol);
            idToken.symbolTableEntry = symbol;
        } else if (symbol instanceof Symbol.StubWithTypeSymbol) {
            idToken.datatype = ParserUtil.resolveExprTypeSymbol(((Symbol.StubWithTypeSymbol) symbol).typeSymbol);
            idToken.symbolTableEntry = symbol;
        } else if (symbol instanceof Symbol.ConstSymbol) {
            idToken.datatype = ((Symbol.ConstSymbol) symbol).typeSymbol;
            idToken.symbolTableEntry = symbol;
        } else if (symbol instanceof Symbol.TypeSymbol) {
            idToken.datatype = ParserUtil.resolveExprTypeSymbol((Symbol.TypeSymbol) symbol);
            idToken.symbolTableEntry = Symbol.NULLSYMBOL;
        } else {
            this.logInternalError("Unhandled type assignment for " + symbol.getClass().getName());
        }
    }

    /**
     * Use this to convert a constant variable Token to a constant value Token.
     * This must be called after the symbol fields of the token are set by
     * {@link Parser#setIdentifierSymbols(Token)}.
     *
     * @param token A Token instance, with its datatype field set by e.g.
     *              {@link Parser#setIdentifierSymbols(Token)}
     * @return Either a Token instance containing the value of the constant
     * or the given Token instance if not an constant
     */
    private Token foldConstant(Token token) {
        Token result = token;
        if (token.symbolTableEntry instanceof Symbol.ConstSymbol) {
            Symbol.ConstSymbol constSymbol = (Symbol.ConstSymbol) token.symbolTableEntry;
            if (constSymbol.typeSymbol.name.equals("integer")) {
                result = new Token<Integer>(TokenType.INTEGER, ((Symbol.ConstSymbol<Integer>) constSymbol).value);
                result.datatype = constSymbol.typeSymbol;
            } else if (constSymbol.typeSymbol.name.equals("real")) {
                result = new Token<Double>(TokenType.REAL, ((Symbol.ConstSymbol<Double>) constSymbol).value);
                result.datatype = constSymbol.typeSymbol;
            } else if (constSymbol.typeSymbol.name.equals("char")) {
                result = new Token<String>(TokenType.STRING, ((Symbol.ConstSymbol<String>) constSymbol).value);
                result.datatype = constSymbol.typeSymbol;
            } else if (constSymbol.typeSymbol.name.equals("boolean")) {
                return new Token<Integer>(TokenType.BOOLEAN, ((Symbol.ConstSymbol<Integer>) constSymbol).value);
            }
        }
        return result;
    }

    /**
     * Parse an expression, which can be any code involving operators. This includes assignment,
     * accessing fields of a record, array references, and function calls.
     * <p/>
     * This is implemented as a shift-reduce parser to handle operator precedences. There are a few
     * aspects to be careful about here.
     * 1. Negation (e.g. {@code -3}) must be supported, so we have to be able to identify where
     * a {@link TokenType#MINUS} is actually a negation and not a subtraction.
     * I think this can be done reliably by looking at the tokens immediately to the left
     * and right of the minus sign. Currently, I have a hack in place that should work
     * in many cases (TODO)
     * 2. We need to identify function identifiers as function calls. This is easy, since any
     * identifier token that is a function will have its {@link Token#type} attribute set
     * to be {@link TokenType#FUNCALL}
     * 3. We need to handle array indexing. This is also easy, since brackets always indicate
     * an array subscript in Pascal. We call {@link compiler.Parser#parseArrayIndex()} for this.
     *
     * @return A Token representing the expression in abstract syntax.
     * @see Parser#setIdentifierSymbols(Token)
     * @see Parser#reduce(java.util.LinkedList, java.util.LinkedList)
     */
    private Token parseExpr() {
        Symbol symbol;
        Token peek, token;
        LinkedList<Token> operators = new LinkedList<Token>();
        LinkedList<Token> operands = new LinkedList<Token>();
        boolean done = false;
        while (!done) {
            peek = this.peekToken();
            if (peek.isLiteral()) {    // number, string, boolean
                token = this.nextToken();
                this.setBasicDatatype(token);
                operands.push(token);
            } else if (peek.isType(TokenType.NIL)) {  // nil is an alias for a pointer to zero
                this.nextToken();
                token = new Token<Integer>(TokenType.INTEGER, 0);
                token.datatype = Symbol.POINTERTYPE;
                operands.push(token);
            } else if (peek.isType(TokenType.IDENTIFIER)) {   // either a variable, function, or field id
                token = this.nextToken();
                // Lookup and set the datatype from the symbol table of all identifiers here
                this.setIdentifierSymbols(token);   // record fields may still have NullTypeSymbol datatypes
                token = this.foldConstant(token);
                //if (token.symbolTableEntry instanceof Symbol.FunctionSymbol) {
                if (token.isType(TokenType.FUNCALL)) {
                    operators.push(token);
                } else {
                    operands.push(token);
                }
            } else if (peek.isType(TokenType.LPAREN)) {
                operators.push(this.nextToken());
            } else if (peek.isType(TokenType.RPAREN)) {
                this.nextToken();
                while (!operators.isEmpty() && !operators.peek().isType(TokenType.LPAREN))
                    this.reduce(operators, operands);
                if (operators.isEmpty())
                    this.logError("Found dangling right parentheses");
                else
                    operators.pop();  // discard the left parentheses
            } else if (peek.isType(TokenType.LBRACKET)) {     // handle array indexing
                List<Token> indices = this.parseArrayIndex();
                Token array = operands.pop();
                token = this.reduceArrayReferenceList(array, indices);
                operands.push(token);
            } else if (peek.isOperator()) {
                token = this.nextToken();
                while (!operators.isEmpty() && !operators.peek().isDelimiter()
                        && operators.peek().precedence() >= token.precedence()) {
                    reduce(operators, operands);
                }
                operators.push(token);
            } else {
                done = true;
            }
        }
        while (!operators.isEmpty())
            reduce(operators, operands);
        if (operands.size() != 1)
            this.logError("Incorrect number of arguments to some expression");
        return operands.pop();
    }

    /**
     * This pops an operator from operators and pops some number of arguments from operands
     * (one or two depending on the operator, or possibly more for a function call).
     * It constructs a tree from the popped items and pushes the tree back onto the operands stack.
     *
     * @see compiler.Parser#parseExpr()
     */
    private void reduce(LinkedList<Token> operators, LinkedList<Token> operands) {
        Token lhs, rhs;
        Token op = operators.pop();
        if (op.isType(TokenType.FUNCALL)) {   // TODO: handle functions with more than one argument
            lhs = operands.pop();
            String name = ((Token<String>) op).value;
            if (name.equals("new")) {
                op = this.reduceFunctionNew(op, lhs);
            } else if (name.equals("write") || name.equals("writeln")) {
                op = this.reduceFunctionWrite(op, lhs);
            } else {
                op = this.reduceFuncall(op, lhs);
            }
        } else if (op.isType(TokenType.POINTER)) {
            lhs = operands.pop();
            op = this.reducePointer(op, lhs);
        } else if (op.isType(TokenType.MINUS) &&      // check for negation
                (!operators.isEmpty() && operators.peek().isDelimiter()
                        || !operators.isEmpty() && operators.peek().isOperator()
                        && !operators.peek().isType(TokenType.ASSIGN))) {
            lhs = operands.pop();
            op.children.add(lhs);
            op.datatype = lhs.datatype;
        } else if (op.isType(TokenType.DOT)) {
            rhs = operands.pop();
            lhs = operands.pop();
            op = reduceDot(op, lhs, rhs);
        } else {
            rhs = operands.pop();
            lhs = operands.pop();
            op = this.makeBinaryExpression(op, lhs, rhs);
        }
        operands.push(op);
    }

    /**
     * Cast an expression to an integer
     *
     * @param expr Some expression in abstract syntax.
     * @return A Token with type {@link TokenType#CASTINT} and expr as a child
     */
    private Token coerceToInt(Token expr) {
        Token result = new Token<Integer>(TokenType.CASTINT, expr);
        result.datatype = (Symbol.BasicTypeSymbol) this.symbolTable.lookup("integer");
        return result;
    }

    /**
     * Cast an expression to a real
     *
     * @param expr Some expression in abstract syntax.
     * @return A Token with type {@link TokenType#CASTINT} and expr as a child
     */
    private Token coerceToReal(Token expr) {
        Token result = new Token<Double>(TokenType.CASTREAL, expr);
        result.datatype = (Symbol.BasicTypeSymbol) this.symbolTable.lookup("real");
        return result;
    }

    /**
     * Construct a binary tree
     *
     * @param opToken The root of the tree, which should be an operator
     * @param lhs     The left hand child
     * @param rhs     The right hand child
     * @return opToken with lhs and rhs as children
     */
    private Token makeBinaryExpression(Token opToken, Token lhs, Token rhs) {
        if (opToken.isType(TokenType.ASSIGN)) {   // for "a := b", we cast b as necessary
            if (lhs.datatype.name.equals("integer") && rhs.datatype.name.equals("real")) {
                opToken.children.add(lhs);
                opToken.children.add(this.coerceToInt(rhs));
                this.logWarning("Coercing real to integer in expression: \n" + opToken.toExprString());
            } else if (lhs.datatype.name.equals("real") && rhs.datatype.name.equals("integer")) {
                opToken.children.add(lhs);
                opToken.children.add(this.coerceToReal(rhs));
            } else {
                opToken.children.add(lhs);
                opToken.children.add(rhs);
            }
            //op.datatype = ParserUtil.resolveExprTypeSymbol(lhs.datatype);
        } else {   // otherwise, for e.g. "a + b" we cast an integer as a real where needed
            if (lhs.datatype.name.equals("integer") && rhs.datatype.name.equals("real")) {
                opToken.children.add(this.coerceToReal(lhs));
                opToken.children.add(rhs);
                //this.logWarning("Coercing real to integer in expression: \n" + op.toExprString());
                opToken.datatype = ParserUtil.resolveExprTypeSymbol(rhs.datatype);   // real
            } else if (lhs.datatype.name.equals("real") && rhs.datatype.name.equals("integer")) {
                opToken.children.add(lhs);
                opToken.children.add(this.coerceToReal(rhs));
                opToken.datatype = ParserUtil.resolveExprTypeSymbol(lhs.datatype);   // real
            } else {
                opToken.children.add(lhs);
                opToken.children.add(rhs);
                opToken.datatype = ParserUtil.resolveExprTypeSymbol(lhs.datatype);
            }
            if (opToken.isType(TokenType.EQ, TokenType.NE, TokenType.LE,
                    TokenType.LT, TokenType.GE, TokenType.GT)) {
                opToken.datatype = (Symbol.BasicTypeSymbol) this.symbolTable.lookup("boolean");
            }
        }
        return opToken;
    }

    /**
     * Construct abstract syntax representing a function call. This will check the argument
     * type and either print an error, a warning, or cast the argument as appropriate.
     *
     * @param func The function, which should have {@link Token#symbolTableEntry}
     *             set to a {@link Symbol.FunctionSymbol}
     * @param arg  The argument to the function
     * @return func, with arg as child
     */
    private Token reduceFuncall(Token func, Token arg) {
        if (func.symbolTableEntry instanceof Symbol.FunctionSymbol) {
            Symbol.FunctionSymbol functionSymbol = (Symbol.FunctionSymbol) func.symbolTableEntry;
            if (arg.datatype.name.equals(functionSymbol.argTypes[0].name)) {
                func.children.add(arg);
            } else if (arg.datatype.name.equals("integer") && functionSymbol.argTypes[0].name.equals("real")) {
                this.logWarning("Casting integer argument '" + arg.toExprString(0, true) + "' to function '" + functionSymbol.name +
                        "' to real.");
                func.children.add(this.coerceToReal(arg));
            } else {
                this.logError("Invalid argument '" + arg.toExprString(0, true) + "' to function '"
                        + functionSymbol.name + "': Expected type " + functionSymbol.argTypes[0].name
                        + " but got " + arg.datatype.name);
            }
        } else {
            this.logInternalError("Cannot reduce function using token (" + func.toString() +
                    ") which has non-function symbolTableEntry (" + func.symbolTableEntry.toString() + ")");
        }
        return func;
    }

    /**
     * Parse a list of array references: {@code [EXPRa1, ..., EXPRaN][EXPRb1, ..., EXPRbN]... }
     *
     * @return A List of Tokens representing each index expr, stored same order as in the source code.
     */
    private List<Token> parseArrayIndex() {
        this.expectNextToken(TokenType.LBRACKET);
        LinkedList<Token> result = new LinkedList<Token>();
        Token expr = this.parseExpr();
        result.add(expr);
        while (this.peekToken().isType(TokenType.COMMA)) {
            this.nextToken();
            expr = this.parseExpr();
            result.add(expr);
        }
        this.expectNextToken(TokenType.RBRACKET);
        if (this.peekToken().isType(TokenType.LBRACKET)) {
            result.addAll(this.parseArrayIndex());
        }
        return result;
    }

    /**
     * Reduce an array subscript expression
     *
     * @param array   An expression which resolves to an array
     * @param indices A list of expressions that index into the array
     *                as returned by {@link compiler.Parser#parseArrayIndex()}
     * @return A Token of type {@link TokenType#AREF} which
     * represents the array reference as abstract syntax: {@code (AREF <base> <offset>)}
     * @see compiler.Parser#parseExpr()
     */
    private Token reduceArrayReferenceList(Token array, List<Token> indices) {
        if (indices == null || indices.isEmpty()) {
            this.logError("Found empty array subscript after '" + array.value + "'");
            return Token.NULLTOKEN;
        }
        Token arefToken = this.reduceArrayReference(array, indices.remove(0));
        while (!indices.isEmpty())
            arefToken = this.reduceArrayReference(arefToken, indices.remove(0));
        return arefToken;
    }

    /**
     * Construct abstract syntax representing {@code high - low}
     */
    private Token makeHighMinusLow(Token high, Token low) {
        return this.makeBinaryExpression(new Token(TokenType.MINUS), high, low);
    }

    /**
     * Construct abstract syntax representing {@code *high - low) * size}
     */
    private Token makeHighMinusLowTimesSize(Token high, Token low, Token size) {
        return this.makeBinaryExpression(new Token(TokenType.TIMES), this.makeHighMinusLow(high, low), size);
    }

    /**
     * Create an array reference. In abstract syntax this is: {@code (AREF ARRAY OFFSET)}
     * In Pascal, an interval of indices is included in the type of an array. For example,
     * {@code array[3..7]} is a different type than {@code array[2..9]}. Therefore, the
     * offset into the array is computed as {@code (index - low) * size}
     *
     * @param base        The base expression (assume to be an array here)
     * @param index       The index expression into the base expression
     * @param arraySymbol A {@link Symbol.ArraySymbol} which has the contained type and
     *                    the subrange we need to construct the array reference
     * @return A Token of type {@link TokenType#AREF}
     * @see Parser#reduceArrayReference(Token, Token)
     * @see Parser#reduceArrayReferenceList(Token, java.util.List)
     */
    private Token makeArrayReference(Token base, Token index, Symbol.ArraySymbol arraySymbol) {
        Token lowToken = this.makeIntegerToken(arraySymbol.subrangeSymbol.low);
        Token sizeToken = this.makeIntegerToken(arraySymbol.containedTypeSymbol.size);
        Token address = this.makeHighMinusLowTimesSize(index, lowToken, sizeToken);
        Token result = new Token(TokenType.AREF, base, address);
        result.datatype = ParserUtil.resolveExprTypeSymbol(arraySymbol.containedTypeSymbol);
        return result;
    }

    /**
     * Construct an array reference from base and index. This corresponds to
     * source code {@code base[index]}.
     *
     * @param base  A Token with its datatype a {@link Symbol.ArraySymbol} instance
     * @param index An expression that indexes into the array
     * @return A Token of type {@link TokenType#AREF}, or {@link Token#NULLTOKEN}
     * if base is not an array type
     * @see Parser#makeArrayReference(Token, Token, compiler.Symbol.ArraySymbol)
     * @see Parser#reduceArrayReferenceList(Token, java.util.List)
     */
    private Token reduceArrayReference(Token base, Token index) {
        if (base.datatype instanceof Symbol.ArraySymbol) {
            Symbol.ArraySymbol arraySymbol = (Symbol.ArraySymbol) base.datatype;
            return this.makeArrayReference(base, index, arraySymbol);
        } else {
            this.logError("Cannot index into non-array expression (" + base.toString()
                    + ") which has type " + base.datatype.toSimpleString());
            return Token.NULLTOKEN;
        }
    }

    /**
     * Reduce an expression {@code LHS . RHS}. LHS is a record, and RHS is a field name.
     * This is a record access which is just an offset from the base address of the record.
     *
     * @param dot A {@link Token} of type {@link TokenType#DOT}. This is ignored.
     * @param lhs An expression which should resolve to a record
     * @param rhs A Token containing the name of the field
     * @return A Token with type {@link TokenType#AREF}
     */
    private Token reduceDot(Token dot, Token lhs, Token<String> rhs) {
        Symbol.RecordSymbol recordSymbol = null;
        if (lhs.datatype instanceof Symbol.RecordSymbol) {
            recordSymbol = (Symbol.RecordSymbol) lhs.datatype;
        } else {
            this.logError("Cannot use dot operator '.' with non-record token '" + lhs.toExprString(4, false) + "'");
            return Token.NULLTOKEN;
        }

        Symbol.FieldSymbol fieldSymbol = recordSymbol.getField(rhs.value);
        if (fieldSymbol == null) {
            this.logError("Field '" + rhs.value + "' not found in record " + recordSymbol.toString() + "\n " + lhs.toExprString(4, false));
            return Token.NULLTOKEN;
        }
        Token result = new Token(TokenType.AREF, lhs, this.makeIntegerToken(fieldSymbol.offset));
        result.datatype = ParserUtil.resolveExprTypeSymbol(fieldSymbol.typeSymbol);
        return result;
    }

    private Token reducePointer(Token point, Token arg) {
        Symbol.PointerSymbol pointerSymbol = null;
        if (arg.datatype instanceof Symbol.PointerSymbol) {
            pointerSymbol = (Symbol.PointerSymbol) arg.datatype;
        } else {
            this.logError("Cannot dereference value of type '" + arg.datatype.name + "' in " +
                    "expression\n" + arg.toExprString(4, false));
            return Token.NULLTOKEN;
        }
        point.children.add(arg);
        point.datatype = ParserUtil.resolveExprTypeSymbol(pointerSymbol.typeSymbol);
        return point;
    }

    /**
     * A call to new {@code new(id)} allocates space at an address and assigns the address
     * to {@code id}. That is, this corresponds to abstract syntax {@code (:= id (funcall new <size>))}.
     * This constructs the assigment tree, gets the size we need to allocate based on id.datatype,
     * and adds the arg to the children of function token.
     *
     * @param func A Token containing the function name new
     * @param arg  A Token containing the argument to the function
     * @return A Token representing the abstract syntax {@code (:= id (funcall new size))}
     */
    private Token reduceFunctionNew(Token<String> func, Token<String> arg) {
        Symbol.PointerSymbol pointerSymbol = null;
        if (arg.datatype instanceof Symbol.PointerSymbol) {
            pointerSymbol = (Symbol.PointerSymbol) arg.datatype;
        } else {
            this.logError("Cannot call new on non-pointer id '" + arg.value +
                    "' : " + arg.datatype.toSimpleString());
            return Token.NULLTOKEN;
        }
        int size = ParserUtil.resolveExprTypeSymbol(pointerSymbol.typeSymbol).size;
        func.children.add(this.makeIntegerToken(size));
        func.datatype = Symbol.POINTERTYPE;
        return new Token(TokenType.ASSIGN, arg, func);
    }

    /**
     * We call different functions based on the type of the argument to write or writeln.
     * For example, {@code write(3.0)} becomes {@code writef(3.0)}.
     *
     * @param func A {@link Token} of type {@link TokenType#FUNCALL}
     *             named either "write" or "writeln"
     * @param arg  An argument to either the write or writeln function
     * @return The argument func, but with the argument
     */
    private Token reduceFunctionWrite(Token<String> func, Token arg) {
        if (arg.datatype.name.equals("integer")) {
            func.value += "i";
            func.symbolTableEntry = this.symbolTable.lookup(func.value);
        } else if (arg.datatype.name.equals("real")) {
            func.value += "f";
            func.symbolTableEntry = this.symbolTable.lookup(func.value);
        }
        func.children.add(arg);
        return func;
    }

    /**
     * This is the same as {@link compiler.Parser#nextToken()}, except this will print
     * an error if the token return is not any of the given types
     *
     * @param expectedTypes Any number of token types
     * @return The next Token
     * @see compiler.Parser#nextToken()
     * @see compiler.Parser#peekToken()
     */
    private Token expectNextToken(TokenType... expectedTypes) {
        Token nextTok = this.nextToken();
        boolean printError = false;
        if (nextTok.isNull()) {
            printError = true;
        } else {
            for (TokenType type : expectedTypes)
                if (nextTok.isType(type))
                    printError = true;
        }
        if (!printError) {
            String errorString = "Expected token with type in {" + expectedTypes[0].name();
            for (int i = 1; i < expectedTypes.length; ++i)
                errorString += ", " + expectedTypes[i].name();
            errorString += "}";
            if (nextTok.isNull())
                this.logError(errorString + " but reached EOF");
            else
                this.logError(errorString + " but found token (" + nextTok.toString() + ")");
        }
        return nextTok;
    }

    /**
     * Consume the next Token. This is implemented to work with {@link compiler.Parser#peekToken()}
     *
     * @return The next Token from {@link Parser#lexer}
     * @see compiler.Parser#expectNextToken(compiler.TokenType...)
     * @see compiler.Parser#peekToken()
     */
    private Token nextToken() {
        Token result = null;
        if (!this.savedToken.isNull()) {
            result = this.savedToken;
            this.savedToken = Token.NULLTOKEN;
        } else {
            result = this.lexer.nextToken();
        }
        if (Parser.DEBUG)
            System.out.println("Parser.getToken() = " + result);
        return result;
    }

    /**
     * Get but do not consume the next token. That is, this will return the same token instance
     * every call until {@link compiler.Parser#nextToken()} is called again.
     * <p/>
     * (This actually always consumes the next Token in {@link Parser#lexer} and stores it in
     * the "one-token buffer" {@link Parser#savedToken}. This buffer is always checked before
     * peeking at or consuming the next token)
     *
     * @return The next Token from {@link Parser#lexer}
     * @see compiler.Parser#nextToken()
     * @see Parser#expectNextToken(compiler.TokenType...)
     */
    private Token peekToken() {
        if (this.savedToken.isNull())
            this.savedToken = this.lexer.nextToken();
        return this.savedToken;
    }
}
