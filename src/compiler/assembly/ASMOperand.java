package compiler.assembly;

import java.util.*;

public class ASMOperand {

    public static Size REAL_SIZE = Size.bits32;
    public static Size INT_SIZE = Size.bits32;

    /* Some registers we refer to frequently */
    public static ASMOperand EAX = new ASMOperand(Register.eax);
    public static ASMOperand EBP = new ASMOperand(Register.ebp);
    public static ASMOperand ESP = new ASMOperand(Register.esp);
    public static ASMOperand ST0 = new ASMOperand(Register.st0);
    public static ASMOperand ST1 = new ASMOperand(Register.st1);

    public static enum Type {
        register,
        memory,
        immediate
    }

    public static enum Size {
        bits8(8),
        bits16(16),
        bits32(32),
        bits64(64);

        private int size;
        Size(int size) {
            this.size = size;
        }

        public int bits() { return this.size; }
        public int bytes() { return this.size / 8; }
    }

    public static enum Constraint {
        noDereference,
        dereference,
    }

    private Type type;
    private Size size;
    private Object text;
    private Set<Constraint> constraints;

    public ASMOperand(int i) {
        this.type = Type.immediate;
        this.size = INT_SIZE;
        this.text = i;
        this.constraints = new HashSet<Constraint>();
    }

    public ASMOperand(Register reg) {
        this.type = Type.register;
        if (reg.size.equals(Register.Type.SIZE32)) {
            this.size = Size.bits32;
        } else if (reg.size.equals(Register.Type.SIZE64)) {
            this.size = Size.bits64;
        } else {
            throw new Error("TODO");
        }
        this.text = reg;
        this.constraints = new HashSet<Constraint>();
    }

    public ASMOperand(Type type, Size size, Object text) {
        this.type = type;
        this.size = size;
        this.text = text;
        this.constraints = new HashSet<Constraint>();
    }

    public ASMOperand(Type type, Size size, Object text, Constraint... constraints) {
        this.type = type;
        this.size = size;
        this.text = text;
        this.constraints = new HashSet<Constraint>();
        for (Constraint c: constraints) {
            this.constraints.add(c);
        }
    }

    public Register getRegister() {
        if (this.text instanceof Register) {
            return (Register) this.text;
        } else {
            return null;
        }
    }

    public void setType(Type t) { this.type = t; }
    public Type type() { return this.type; }
    public Size size() { return this.size; }

    public String toString() {
        if (this.hasConstraint(Constraint.noDereference)) {
            return this.text.toString();
        } else if (this.type.equals(Type.memory)) {
            return toNasmMemStr(this.size) + " [" + this.text + "]";
        } else if (this.type().equals(Type.register) && this.hasConstraint(Constraint.dereference)) {
            return toNasmMemStr(this.size) + " [" + this.text + "]";
        } else {
            return this.text.toString();
        }
    }

    public boolean hasConstraint(Constraint c) { return this.constraints.contains(c); }
    public void addConstraint(Constraint c) { this.constraints.add(c); }
    public void removeConstraint(Constraint c) { this.constraints.remove(c); }

    public boolean isMem() {
        return !this.hasConstraint(Constraint.noDereference)
                && (this.type.equals(Type.memory) || this.hasConstraint(Constraint.dereference));
    }

    public boolean equals(ASMOperand other) {
        for (Constraint c: this.constraints) {
            if (!other.hasConstraint(c))
                return false;
        }
        return this.type.equals(other.type())
                && this.size.equals(other.size)
                && this.text.equals(other.text);
    }

    /* Static helpers */
    public static String toNasmDataStr(Size size) {
        switch (size.bytes()) {
            case 8: return "dq";
            case 4: return "dd";
            case 2: return "dw";
            default: return "db";
        }
    }

    public static String toNasmMemStr(Size size) {
        switch (size.bytes()) {
            case 8: return "qword";
            case 4: return "dword";
            case 2: return "word";
            default: return "byte";
        }
    }
}
