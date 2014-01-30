package compiler;

import java.util.LinkedHashMap;

/**
 * This is used to store compile-time information about types, variables, and functions.
 * It is a map that sends Strings to {@link Symbol}s. It has two conceptual "levels".
 * Level zero contains built-in symbols. These are not allowed to be changed or overwritten
 * by the program being compiled. Level one represents all symbols defined by the program
 * being compiled.
 */
public class SymbolTable {
    public static class SymbolTableException extends Exception {
        public SymbolTableException(String message) {
            super(message);
        }
    }

    private LinkedHashMap<String, Symbol> levelZeroTable;
    private LinkedHashMap<String, Symbol> levelOneTable;
    private int offset;

    public SymbolTable() {
        this.levelZeroTable = new LinkedHashMap<String, Symbol>();
        this.levelOneTable = new LinkedHashMap<String, Symbol>();
        this.initializeLevelZeroTable();
        this.offset = 0;
    }

    public boolean inLevelZero(String name) {
        return levelZeroTable.containsKey(name);
    }

    private String formatOutput(String key, Symbol value) {
        return Token.padString(key, 10) + " : " + value.toString() + "\n";
    }

    public String levelZeroString() {
        StringBuilder sb = new StringBuilder("Symbol Table Level Zero:\n");
        for (String key : this.levelZeroTable.keySet()) {
            sb.append(this.formatOutput(key, this.levelZeroTable.get(key)));
        }
        return sb.toString();
    }

    public String levelOneString() {
        StringBuilder sb = new StringBuilder("Symbol Table Level One:\n");
        for (String key : this.levelOneTable.keySet()) {
            sb.append(this.formatOutput(key, this.levelOneTable.get(key)));
        }
        return sb.toString();
    }

    public Symbol lookup(String name) {
        Symbol sym = this.levelZeroTable.get(name);
        if (sym != null)
            return sym;
        return this.levelOneTable.get(name);
    }

    public Symbol lookupOrInsertType(String name) {
        Symbol symbol = this.lookup(name);
        if (symbol == null) {
            symbol = new Symbol.StubWithTypeSymbol(name, Symbol.NULLSYMBOL);
            this.levelOneTable.put(name, symbol);
        }
        return symbol;
    }

    public void insert(String key, Symbol value) throws SymbolTableException {
        this.throwIfKeyInLevelZero(key);
        this.levelOneTable.put(key, value);
        if (value instanceof Symbol.VarSymbol)
            this.updateOffset((Symbol.VarSymbol) value);
    }

    private void throwIfKeyInLevelZero(String key) throws SymbolTableException {
        Symbol check = this.levelZeroTable.get(key);
        if (check != null)
            throw new SymbolTableException("Cannot redefine built-in symbol '" + key + "'");
    }

    private void insertSymbolAtLevel(int level, Symbol symbol) {
        if (level == 0)
            this.levelZeroTable.put(symbol.name, symbol);
        else if (level == 1)
            this.levelOneTable.put(symbol.name, symbol);
        if (symbol instanceof Symbol.VarSymbol)
            this.updateOffset((Symbol.VarSymbol) symbol);
    }

    private void updateOffset(Symbol.VarSymbol symbol) {
        int alignSize = ParserUtil.alignSize(symbol.typeSymbol);
        symbol.offset = ParserUtil.alignAddress(this.offset, alignSize);
        this.offset += ParserUtil.alignAddress(symbol.size, alignSize);
    }

    public int getOffset() {
        return this.offset;
    }

    private void initializeLevelZeroTable() {
        Symbol.BasicTypeSymbol realType = new Symbol.BasicTypeSymbol("real", Symbol.REAL_SIZE);
        Symbol.BasicTypeSymbol integerType = new Symbol.BasicTypeSymbol("integer", Symbol.INT_SIZE);
        Symbol.BasicTypeSymbol charType = new Symbol.BasicTypeSymbol("char", Symbol.CHAR_SIZE);
        Symbol.BasicTypeSymbol booleanType = new Symbol.BasicTypeSymbol("boolean", Symbol.BOOL_SIZE);
        this.insertSymbolAtLevel(0, realType);
        this.insertSymbolAtLevel(0, integerType);
        this.insertSymbolAtLevel(0, charType);
        this.insertSymbolAtLevel(0, booleanType);
        this.insertSymbolAtLevel(0, new Symbol.FunctionSymbol("exp", realType, realType));
        this.insertSymbolAtLevel(0, new Symbol.FunctionSymbol("sin", realType, realType));
        this.insertSymbolAtLevel(0, new Symbol.FunctionSymbol("cos", realType, realType));
        this.insertSymbolAtLevel(0, new Symbol.FunctionSymbol("sqrt", realType, realType));
        this.insertSymbolAtLevel(0, new Symbol.FunctionSymbol("round", realType, realType));
        this.insertSymbolAtLevel(0, new Symbol.FunctionSymbol("iround", integerType, realType));
        this.insertSymbolAtLevel(0, new Symbol.FunctionSymbol("ord", integerType, realType));
        this.insertSymbolAtLevel(0, new Symbol.FunctionSymbol("new", integerType, integerType));
        this.insertSymbolAtLevel(0, new Symbol.FunctionSymbol("write", Symbol.NULLSYMBOL, charType));
        this.insertSymbolAtLevel(0, new Symbol.FunctionSymbol("writeln", Symbol.NULLSYMBOL, charType));
        this.insertSymbolAtLevel(0, new Symbol.FunctionSymbol("writef", Symbol.NULLSYMBOL, realType));
        this.insertSymbolAtLevel(0, new Symbol.FunctionSymbol("writelnf", Symbol.NULLSYMBOL, realType));
        this.insertSymbolAtLevel(0, new Symbol.FunctionSymbol("writei", Symbol.NULLSYMBOL, integerType));
        this.insertSymbolAtLevel(0, new Symbol.FunctionSymbol("writelni", Symbol.NULLSYMBOL, integerType));
        this.insertSymbolAtLevel(0, new Symbol.FunctionSymbol("read", Symbol.NULLSYMBOL, Symbol.NULLSYMBOL));
        this.insertSymbolAtLevel(0, new Symbol.FunctionSymbol("readln", Symbol.NULLSYMBOL, Symbol.NULLSYMBOL));
        this.insertSymbolAtLevel(0, new Symbol.FunctionSymbol("eof", booleanType, Symbol.NULLSYMBOL));
    }

    public Symbol.BasicTypeSymbol getBasicTypeOf(Token value) throws SymbolTableException {
        if (value.isType(TokenType.INTEGER)) {
            return (Symbol.BasicTypeSymbol) this.lookup("integer");
        } else if (value.isType(TokenType.REAL)) {
            return (Symbol.BasicTypeSymbol) this.lookup("real");
        } else if (value.isType(TokenType.STRING)) {
            return (Symbol.BasicTypeSymbol) this.lookup("char");
        } else if (value.isType(TokenType.BOOLEAN)) {
            return (Symbol.BasicTypeSymbol) this.lookup("boolean");
        }
        throw new SymbolTableException("Token of type '" + value.type.name() + "' is not a basic type");
    }
}
