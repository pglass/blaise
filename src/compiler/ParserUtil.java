package compiler;

import java.util.ArrayList;
import java.util.List;

public class ParserUtil {

    public static class TypeMismatchException extends Exception {
        public TypeMismatchException(String message) {
            super(message);
        }
    }

    /* This resolves conflicts between user-defined label numbers
     * and compiler-generated label numbers. In Pascal, all user-defined
     * labels come before any code that needs a generated label (loop/goto).
     * Therefore, we can store the labels in a list and use the indices as
     * label numbers:
     *      -- user-defined labels are stored at indices [0, N]
     *      -- generated labels are stored at indices [N+1, ...).
     */
    public static class LabelList {
        private ArrayList<Integer> labels;
        private int currentLabel;

        public LabelList() {
            this.labels = new ArrayList<Integer>();
            this.currentLabel = 0;
        }

        public LabelList(LabelList other) {
            this.labels = new ArrayList<Integer>();
            this.labels.addAll(other.labels);
            this.currentLabel = other.currentLabel;
        }

        public void addUserLabel(Token<Integer> label) {
            this.labels.add(label.value);
            this.currentLabel++;
        }

        public int lookupUserLabel(Token<Integer> label) {
            return this.lookupUserLabel(label.value);
        }

        public int lookupUserLabel(int labelNumber) {
            return labels.indexOf(labelNumber);
        }

        public int getNextLabelNumber() {
            return this.currentLabel++;
        }
    }

    public static int alignAddress(int n, int size) {
        return ((n + size - 1) / size) * size;
    }

    public static int alignSize(Symbol.TypeSymbol symbol) {
        if (symbol instanceof Symbol.StubWithTypeSymbol || symbol instanceof Symbol.FieldSymbol)
            return alignSize(resolveExprTypeSymbol(symbol));
        else if (symbol instanceof Symbol.ArraySymbol || symbol instanceof Symbol.RecordSymbol)
            return 8;
        else if (symbol instanceof Symbol.BasicTypeSymbol)
            return ((Symbol.BasicTypeSymbol) symbol).size;
        else if (symbol instanceof Symbol.PointerSymbol)
            return 4;
        return 4;
    }

    public static Symbol.TypeSymbol resolveExprTypeSymbol(Symbol.TypeSymbol typeSymbol) {
        if (typeSymbol instanceof Symbol.StubWithTypeSymbol) {
            return resolveExprTypeSymbol(((Symbol.StubWithTypeSymbol) typeSymbol).typeSymbol);
        } else if (typeSymbol instanceof Symbol.FieldSymbol) {
            return resolveExprTypeSymbol(((Symbol.FieldSymbol) typeSymbol).typeSymbol);
        }
        return typeSymbol;
    }

    public static Symbol.FieldSymbol[]
    toFieldSymbolArrayWithOffsets(List<Symbol.FieldSymbol> fields) {
        Symbol.FieldSymbol[] result = new Symbol.FieldSymbol[fields.size()];
        int offset = 0;
        int prevSize = 0;
        for (int i = 0; i < fields.size(); ++i) {
            result[i] = fields.get(i);
            offset = alignAddress(offset + prevSize, alignSize(result[i]));
            result[i].offset = offset;
            prevSize = result[i].size;
        }
        return result;
    }

    /**
     * Construct an ArraySymbol from the given list of subranges and the contained type.
     * These come from an array type declaration in the source that looks like, say,
     * {@code array[1..10, 2..5, 3..7][1..8] of real}. In this case, the containedType
     * represents type real, and the list represents the four subranges.
     * <p/>
     * Note: If we encounter syntax like {@code array[1..10] of array[1..4]} then
     * containedType is an ArraySymbol representing {@code array[1..4]} (already constructed).
     * The subrange list will contain a subrange representing {@code 1..10}.
     *
     * @param subrangeTypes A list of {@link Symbol.TypeSymbol} which are
     *                      or contain {@link Symbol.SubrangeSymbol}s
     * @param containedType A {@link Symbol.TypeSymbol} that represents the
     *                      type contained in the array
     * @return A {@link Symbol.ArraySymbol} that represents the array type
     * @throws TypeMismatchException
     */
    public static Symbol.ArraySymbol
    makeArraySymbol(List<Symbol.TypeSymbol> subrangeTypes,
                    Symbol.TypeSymbol containedType) throws TypeMismatchException {
        if (subrangeTypes.size() > 1) {
            return new Symbol.ArraySymbol(
                    toSubrangeSymbol(subrangeTypes.remove(0)),
                    makeArraySymbol(subrangeTypes, containedType));
        } else {
            return new Symbol.ArraySymbol(toSubrangeSymbol(subrangeTypes.get(0)), containedType);
        }
    }

    /* Either typeSymbol is a SubrangeSymbol or typeSymbol is a stub containing a SubrangeSymbol.
     * Otherwise, throw an exception.
     */
    private static Symbol.SubrangeSymbol
    toSubrangeSymbol(Symbol.TypeSymbol typeSymbol) throws TypeMismatchException {
        if (typeSymbol instanceof Symbol.SubrangeSymbol) {
            return (Symbol.SubrangeSymbol) typeSymbol;
        } else if (typeSymbol instanceof Symbol.StubWithTypeSymbol) {
            Symbol.StubWithTypeSymbol stubSymbol = (Symbol.StubWithTypeSymbol) typeSymbol;
            if (stubSymbol.typeSymbol instanceof Symbol.SubrangeSymbol)
                return (Symbol.SubrangeSymbol) stubSymbol.typeSymbol;
        } else {
            throw new ParserUtil.TypeMismatchException(
                    "Cannot interpret TypeSymbol (" + typeSymbol.toString() + ") as a subrange for an array");
        }
        return null;
    }
}
