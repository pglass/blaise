package compiler.assembly;

import compiler.StringUtil;
import compiler.Token;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;

public class ASMWriter {

    public static String format(Instruction instruction, Object... args) {
        return "    " + Token.padString(instruction.name(), 8) + StringUtil.join(", ", args);
    }

    private PrintStream stream;
    private String comment;

    public ASMWriter(PrintStream stream) {
        this.stream = stream;
    }

    public void println(String s) {
        if (this.comment != null)
            stream.println(Token.padString(s, 40) + "; " + comment);
        else
            stream.println(s);
        comment = null;
    }

    public void print(Instruction instruction, Object... operands) {
        this.println(format(instruction, operands));
    }

    public void printf(String format, Object... args) {
        this.println(String.format(format, args));
    }

    /* Set a comment to be printed inline with the next instruction */
    public void setComment(String comment) { this.comment = comment; }

    /* Print a comment on its own line */
    public void comment(String s) { this.stream.println("; " + s); }

    public void makeStackRoom(int size) {
        if (size > 0)
            this.print(Instruction.sub, Register.esp, size);
    }
    public void clearStackRoom(int size) {
        if (size > 0)
            this.print(Instruction.add, Register.esp, size);
    }

    public void printFloatLiterals(LiteralManager<Double> floatLiteralManager) {
        for (int i = 0; i < floatLiteralManager.size(); ++i) {
            double literal = floatLiteralManager.get(i);
            this.printf("    %s %s %s", floatLiteralManager.getLabel(literal),
                    ASMOperand.toNasmDataStr(ASMOperand.INT_SIZE), literal);
        }
    }

    public void printStringLiterals(LiteralManager<String> stringLiteralManager) {
        for (int i = 0; i < stringLiteralManager.size(); ++i) {
            String literal = stringLiteralManager.get(i);
            this.printf("    %s db \"%s\", 0", stringLiteralManager.getLabel(literal), literal);
        }
    }

    public void printTempSpace(TempStorageManager tempStorageManager) {
        for (Map.Entry<Integer, List<Boolean>> entry: tempStorageManager.memLocations.entrySet()) {
            int size = entry.getKey();
            for (int i = 0; i < entry.getValue().size(); ++i) {
                this.printf("    %s resb %s", tempStorageManager.formatLabel(size, i), size);
            }
        }
    }


    public void mov(ASMOperand left, ASMOperand right) {
        if (left.type().equals(ASMOperand.Type.memory) && right.type().equals(ASMOperand.Type.memory)) {
            throw new Error("cannot mov from memory to memory");
        } else if (!left.size().equals(right.size())) {
            throw new Error("cannot mov from size " + right.size() + " to size " + left.size());
        }

        this.print(Instruction.mov, left, right);
    }

    public void label(String label) { this.println(label + ":"); }
    public void push(ASMOperand operand) { this.print(Instruction.push, operand); }
    public void pop(ASMOperand operand) { this.print(Instruction.pop, operand); }
}
