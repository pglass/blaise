package compiler;

/* Symbol represents a compile-time symbol found by the parser (a variable, function, ...).
 * Subclasses are defined for each possible symbol type. The usage is something like:
 *      Symbol sym = symbolTable.lookup(key);
 *      if (sym instanceof Symbol.FunctionSymbol)
 *          handleFunctionSymbol((Symbol.FunctionSymbol) sym);
 *      else if (sym instance of Symbol.VariableSymbol)
 *          ...
 * Aside: Inheritance here is a crufty solution, but the casting it requires ensures
 * we'll be immediately aware if the compiler assign types incorrectly (since Java
 * will produce a ClassCastException when encounter and cast on unexpected Symbols).
 */
public abstract class Symbol {
    public static final int REAL_SIZE = 4;
    public static final int INT_SIZE = 4;
    public static final int POINTER_SIZE = 4;
    public static final int BOOL_SIZE = 4;
    public static final int CHAR_SIZE = 1;

    public static final NullTypeSymbol NULLSYMBOL = new NullTypeSymbol();
    public static final PointerSymbol POINTERTYPE = new PointerSymbol(NULLSYMBOL);
    public String name;

    public Symbol(String name) {
        this.name = name;
    }

    public String toString() {
        return Token.padString(this.getClass().getSimpleName(), 15) + " " + Token.padString(this.name, 10);
    }

    public String toSimpleString() {
        return this.getClass().getSimpleName() + " " + this.name;
    }

    public static abstract class TypeSymbol extends Symbol {
        int size;

        public TypeSymbol(String name, int size) {
            super(name);
            this.size = size;
        }

        public String toString() {
            return super.toString() + " size=" + this.size;
        }
    }

    /* A function or variable */
    public static abstract class DefinedSymbol extends Symbol {
        public DefinedSymbol(String name) {
            super(name);
        }
    }

    /* integer, real, string, boolean */
    public static class BasicTypeSymbol extends TypeSymbol {
        public BasicTypeSymbol(String name, int size) {
            super(name, size);
        }
    }

    /* Used for forward declarations */
    public static class StubWithTypeSymbol extends TypeSymbol {
        public TypeSymbol typeSymbol;

        public StubWithTypeSymbol(String name, TypeSymbol typeSymbol) {
            super(name, typeSymbol.size);
            this.typeSymbol = typeSymbol;
        }

        public void setTypeSymbol(TypeSymbol symbol) {
            this.typeSymbol = symbol;
            this.size = symbol.size;
        }

        public String toString() {
            return super.toString() + "\n\t typeSymbol=" + this.typeSymbol.toString();
        }

        public String toSimpleString() {
            return super.toSimpleString() + " " + this.typeSymbol.name;
        }
    }

    public static class NullTypeSymbol extends TypeSymbol {
        public NullTypeSymbol() {
            super("NullSymbol", 0);
        }
    }

    public static class FunctionSymbol extends DefinedSymbol {
        public TypeSymbol resultType;
        public TypeSymbol[] argTypes;

        public FunctionSymbol(String name, TypeSymbol resultType, TypeSymbol... argTypes) {
            super(name);
            this.resultType = resultType;
            this.argTypes = argTypes;
        }

        public String toString() {
            String s = super.toString() + " resultType=" + this.resultType.name + " argTypes=[";
            if (this.argTypes.length > 0) {
                s += this.argTypes[0].name;
                for (int i = 1; i < this.argTypes.length; ++i)
                    s += ", " + this.argTypes[i].name;
            }
            s += "]";
            return s;
        }
    }

    public static class VarSymbol extends DefinedSymbol {
        public int size;
        public int offset;
        public TypeSymbol typeSymbol;

        public VarSymbol(String name, TypeSymbol typeSymbol) {
            super(name);
            this.size = typeSymbol.size;
            this.offset = 0;  // this is set after construction
            this.typeSymbol = typeSymbol;
        }

        public String toString() {
            return super.toString() + " size=" + this.size + " offset=" + this.offset
                    + " typeSymbol=" + this.typeSymbol.name;
        }

        public String toSimpleString() {
            return super.toSimpleString() + " " + this.typeSymbol.name;
        }
    }

    public static class ConstSymbol<T> extends DefinedSymbol {
        public int size;
        public T value;
        public TypeSymbol typeSymbol;

        public ConstSymbol(String name, T value, TypeSymbol typeSymbol) {
            super(name);
            this.size = typeSymbol.size;
            this.value = value;
            this.typeSymbol = typeSymbol;
        }

        public String toString() {
            return super.toString() + " value=" + this.value + " " + this.typeSymbol.name;
        }
    }

    /* typeSymbol is the type pointed to. That is, for the following:
     *      person_ptr = ^person;
     * The TypeSymbol for person either already exists, or a stub is created.
     * Then a PointerSymbol will be created using the TypeSymbol for person.
     * Then a TypeSymbol will be created using "person_ptr" and the PointerSymbol.
     */
    public static class PointerSymbol extends TypeSymbol {
        public TypeSymbol typeSymbol;
        private static int numPointers = 0;

        public PointerSymbol(TypeSymbol typeSymbol) {
            super("{POINTER " + (++numPointers) + "}", POINTER_SIZE);
            numPointers += 1;
            this.typeSymbol = typeSymbol;
        }

        public String toString() {
            return super.toString() + " typeSymbol=" + this.typeSymbol;
        }
    }

    /* Note: A record does not have a name. A _type_ defined to be a record can have a name.
     * That is, a record is always found as:
     *      type complex = record re, im: real end;
     * This produces a TypeSymbol named complex which contains a RecordSymbol.
     */
    public static class RecordSymbol extends TypeSymbol {
        public FieldSymbol[] fieldTypes;
        private static int numRecords = 0;  // for (unnecessary) display purposes

        public RecordSymbol(FieldSymbol[] fieldTypes) {
            super("{RECORD " + (++numRecords) + "}", 0);
            this.fieldTypes = fieldTypes;
            for (FieldSymbol field : fieldTypes)
                this.size += field.size;
            this.size = ParserUtil.alignAddress(this.size, ParserUtil.alignSize(this));
        }

        public String toString() {
            String s = super.toString() + " fieldTypes=[";
            if (this.fieldTypes.length > 0) {
                s += this.fieldTypes[0].name;
                for (int i = 1; i < this.fieldTypes.length; ++i)
                    s += ", " + this.fieldTypes[i].name;
            }
            s += "]";
            return s;
        }

        public FieldSymbol getField(String fieldName) {
            for (Symbol.FieldSymbol fieldSymbol : this.fieldTypes) {
                if (fieldSymbol.name.equals(fieldName))
                    return fieldSymbol;
            }
            return null;
        }

        public FieldSymbol getFieldByOffset(int offset) throws SymbolTable.SymbolTableException {
            for (FieldSymbol fieldSymbol : this.fieldTypes) {
                if (fieldSymbol.offset == offset)
                    return fieldSymbol;
            }
            throw new SymbolTable.SymbolTableException(this.name + " has no data at offset " + offset);
        }

    }

    /* Represents a field in a record */
    public static class FieldSymbol extends TypeSymbol {
        public int offset;
        public TypeSymbol typeSymbol;

        public FieldSymbol(String name, TypeSymbol typeSymbol, int offset) {
            super(name, ParserUtil.alignSize(typeSymbol));
            this.typeSymbol = typeSymbol;
            this.offset = offset;
        }

        public String toString() {
            return super.toString() + " offset=" + this.offset + " typeSymbol=" + typeSymbol.name;
        }
    }

    public static class SubrangeSymbol extends TypeSymbol {
        public int low;
        public int high;
        private static int numSubranges = 0; // for display purposes

        public SubrangeSymbol(int low, int high) {
            super("{SUBRANGE " + (++numSubranges) + "}", INT_SIZE);
            this.low = low;
            this.high = high;
        }

        public int intervalSize() {
            return this.high - this.low + 1;
        }

        public String toString() {
            return super.toString() + " low=" + this.low + " high=" + this.high;
        }
    }

    public static class ArraySymbol extends TypeSymbol {
        public SubrangeSymbol subrangeSymbol;
        public TypeSymbol containedTypeSymbol;
        private static int numArrays = 0;   // for display purposes

        public ArraySymbol(SubrangeSymbol subrangeSymbol, TypeSymbol containedTypeSymbol) {
            super("{ARRAY " + (++numArrays) + "}", subrangeSymbol.intervalSize() * containedTypeSymbol.size);
            this.subrangeSymbol = subrangeSymbol;
            this.containedTypeSymbol = containedTypeSymbol;
        }
    }
}
