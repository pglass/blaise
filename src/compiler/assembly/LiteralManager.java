package compiler.assembly;

import java.util.ArrayList;
import java.util.List;

/* Useful for ensuring unique labels for float and string literals. */
public class LiteralManager<T> {
    private List<T> literals;
    private String labelPrefix;
    public LiteralManager(String labelPrefix) {
        this.literals = new ArrayList<T>();
        this.labelPrefix = labelPrefix;
    }

    public String getLabel(T literal) {
        for (int i = 0; i < this.literals.size(); ++i) {
            if (this.literals.get(i).equals(literal))
                return this.literalLabel(i);
        }
        this.literals.add(literal);
        return this.literalLabel(this.literals.size() - 1);
    }

    public T get(int i) { return this.literals.get(i); }
    public int size() { return this.literals.size(); }

    private String literalLabel(int index) {
        return this.labelPrefix + index;
    }
}
