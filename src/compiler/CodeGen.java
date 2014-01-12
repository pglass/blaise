package compiler;

import compiler.assembly.*;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class CodeGen {

    /* A map to lookup the appropriate jump instruction for a given comparison */
    private static final HashMap<TokenType, Instruction> jumpMap = new HashMap<TokenType, Instruction>();
    static {
        jumpMap.put(TokenType.EQ, Instruction.je);
        jumpMap.put(TokenType.LE, Instruction.jle);
        jumpMap.put(TokenType.LT, Instruction.jl);
        jumpMap.put(TokenType.GE, Instruction.jge);
        jumpMap.put(TokenType.GT, Instruction.jg);
        jumpMap.put(TokenType.NE, Instruction.jne);
    }

    /* If we're using 32-bit floats, then we'll run into problems
     * when we push a 4-byte float onto the stack for a function call
     * that expects a 64-bit double (this occurs with C library math functions).
     * To handle this, we'll define some external forwarding functions like `sin32`
     * and `sqrt32` that take a 32-bit float, call the original function with an
     * appropriate conversion to 64-bit double, and return a 32-bit float.
     *
     * These are the built-in functions that take a double as an argument in the C library implementation.
     */
    private static final HashSet<String> doubleMathFunctions = new HashSet<String>();
    static {
        doubleMathFunctions.add("exp");
        doubleMathFunctions.add("sin");
        doubleMathFunctions.add("cos");
        doubleMathFunctions.add("sqrt");
        doubleMathFunctions.add("round");
        doubleMathFunctions.add("iround");
        doubleMathFunctions.add("writef");
        doubleMathFunctions.add("writelnf");
    }

    private boolean debug;
    private Parser parser;
    private ASMWriter asmWriter;
    private Token code;

    private LiteralManager<String> stringLiteralManager;
    private LiteralManager<Double> floatLiteralManager;
    private RegisterManager registerManager;
    private TempStorageManager tempStorageManager;
    private ParserUtil.LabelList labelList;

    public CodeGen(Parser parser, boolean debug) {
        this.parser = parser;
        this.debug = debug;
        this.code = this.parser.parse();
        this.stringLiteralManager = new LiteralManager<String>("STRING");
        this.floatLiteralManager = new LiteralManager<Double>("FLOAT");
        this.tempStorageManager = new TempStorageManager();
        this.registerManager = new RegisterManager();
        this.labelList = new ParserUtil.LabelList(parser.labelList);
    }

    public CodeGen(Parser parser) {
        this(parser, false);
    }

    /** This only works correctly on the first call */
    public void write(PrintStream stream) throws RegisterManager.RegisterAllocationException {
        this.asmWriter = new ASMWriter(stream);

        if (this.debug) {
            this.asmWriter.println(this.code.toExprString());
            this.asmWriter.println(this.parser.symbolTable.levelZeroString());
            this.asmWriter.println(this.parser.symbolTable.levelOneString());
        }
        this.printPrologue();
        this.genCode(this.code);
        this.printEpilogue();
        this.printDataSection();
        this.printBssSection();
    }

    private void printPrologue() {
        this.asmWriter.println("%include \"pascal.inc\"");
        this.asmWriter.println("segment .text");
        this.asmWriter.println("    global _asm_main");
        this.asmWriter.println("_asm_main:");
        /* Setup the stack. We store variables at the beginning of the stack,
         * so we make room by moving esp. */
        this.asmWriter.push(ASMOperand.EBP);
        this.asmWriter.mov(ASMOperand.EBP, ASMOperand.ESP);
        int offset = parser.symbolTable.getOffset();
        if (offset > 0)
            this.asmWriter.makeStackRoom(offset);
    }

    private void printDataSection() {
        this.asmWriter.println("\nsegment .data");
        this.asmWriter.printStringLiterals(this.stringLiteralManager);
        this.asmWriter.printFloatLiterals(this.floatLiteralManager);
    }

    private void printBssSection() {
        this.asmWriter.println("\nsegment .bss");
        this.asmWriter.printTempSpace(this.tempStorageManager);
    }

    private void printEpilogue() {
        this.asmWriter.setComment("set exit value");
        this.asmWriter.mov(ASMOperand.EAX, new ASMOperand(0));
        this.asmWriter.mov(ASMOperand.ESP, ASMOperand.EBP);
        this.asmWriter.print(Instruction.pop, ASMOperand.EBP);
        this.asmWriter.print(Instruction.ret);
    }


    private void genCode(Token tok) throws RegisterManager.RegisterAllocationException {
        if (this.debug) this.asmWriter.comment("genCode() for " + tok);
        if (tok.isType(TokenType.PROGRAM)) {
            // ignore the program identifier and input/output: `program graph1(output)`
            genCode(tok.getChild(2));
        } else if (tok.isType(TokenType.PROGN)) {
            for (Token t: (List<Token>) tok.children) {
                genCode(t);
            }
        } else if (tok.isType(TokenType.ASSIGN)) {
            handleAssign(tok);
        } else if (tok.isType(TokenType.IF)) {
            handleIf(tok);
        } else if (tok.isType(TokenType.LABEL)) {
            handleLabel(tok);
        } else if (tok.isType(TokenType.FUNCALL)) {
            handleFuncall(tok);
        } else if (tok.isType(TokenType.GOTO)) {
            this.asmWriter.print(Instruction.jmp, "L" + tok.value);
        } else if (tok.isType(TokenType.EQ, TokenType.NE, TokenType.LT, TokenType.LE, TokenType.GT, TokenType.GE)) {
            this.handleBoolean(tok);
        } else {
            throw new Error("TODO");
        }
    }

    private ASMOperand genExpr(Token tok) throws RegisterManager.RegisterAllocationException {
        if (this.debug) this.asmWriter.comment("genExpr() for " + tok);
        if (tok.isType(TokenType.IDENTIFIER)) {
            return this.handleIdentifier(tok);
        } else if (tok.isType(TokenType.PLUS, TokenType.MINUS, TokenType.TIMES, TokenType.DIVIDE)) {
            return this.handleArithmetic(tok);
        } else if (tok.isType(TokenType.CASTINT, TokenType.CASTREAL)) {
            return this.handleCast(tok);
        } else if (tok.isType(TokenType.FUNCALL)) {
            return this.handleFuncall(tok);
        } else if (tok.isType(TokenType.INTEGER, TokenType.REAL, TokenType.STRING)) {
            return this.handleLiteral(tok);
        } else if (tok.isType(TokenType.AREF)) {
            return this.handleAref(tok);
        } else if (tok.isType(TokenType.POINTER)) {
            return this.handlePointer(tok);
        } else {
            throw new Error("TODO");
        }
    }

    private ASMOperand handleAref(Token tok) throws RegisterManager.RegisterAllocationException {
        ASMOperand left = genExpr(tok.getChild(0));
        ASMOperand right = genExpr(tok.getChild(1));
        assert(tok.getChild(1).datatype.name.equals("integer"));

        left.addConstraint(ASMOperand.Constraint.noDereference);
        this.asmWriter.print(Instruction.add, left, right);
        this.registerManager.freeRegister(right.getRegister());
        left.removeConstraint(ASMOperand.Constraint.noDereference);

        /* An aref is a base + offset address computation which is then automatically dereferenced */
        left.addConstraint(ASMOperand.Constraint.dereference);
        return left;
    }

    private ASMOperand handlePointer(Token tok) throws RegisterManager.RegisterAllocationException {
        if (tok.getChild(0).isType(TokenType.IDENTIFIER, TokenType.AREF, TokenType.POINTER)) {
            ASMOperand address = new ASMOperand(this.registerManager.acquireRegister(Register.Type.INT));
            ASMOperand var = this.genExpr(tok.getChild(0));
            var.addConstraint(ASMOperand.Constraint.dereference);
            if (tok.getChild(0).isType(TokenType.IDENTIFIER))
                this.asmWriter.setComment(address + " = " + tok.getChild(0).value);
            this.asmWriter.mov(address, var);
            this.registerManager.freeRegister(var.getRegister());
            return address;
        } else {
            throw new Error("TODO");
        }
    }

    private ASMOperand handleIdentifier(Token tok) throws RegisterManager.RegisterAllocationException {
        // variables are stored at the beginning of the stack
        assert(tok.symbolTableEntry instanceof Symbol.VarSymbol);
        Symbol.VarSymbol varSymbol = (Symbol.VarSymbol) tok.symbolTableEntry;
        ASMOperand address = new ASMOperand(this.registerManager.acquireRegister(Register.Type.INT));
        this.asmWriter.mov(address, ASMOperand.EBP);
        this.asmWriter.setComment(address + " = &" + tok.value);
        this.asmWriter.print(Instruction.sub, address, (varSymbol.offset + varSymbol.size));
        address.addConstraint(ASMOperand.Constraint.dereference);
        return address;
    }

    private void handleAssign(Token tok) throws RegisterManager.RegisterAllocationException {
        ASMOperand left = this.genExpr(tok.getChild(0));
        ASMOperand right = this.genExpr(tok.getChild(1));
        if (left.type().equals(ASMOperand.Type.immediate)) {
            throw new Error("cannot assign to immediate location");
        }

        if (right.getRegister() != null && right.getRegister().equals(Register.st0)) {
            if (tok.getChild(0).isType(TokenType.IDENTIFIER))
                this.asmWriter.setComment(tok.getChild(0).value + " = " + right);
            this.asmWriter.print(Instruction.fstp, left);
        } else if (left.isMem() && right.isMem()) { /* Handle memory to memory assignment */
            if (!left.equals(right)){  // don't bother moving a memory location to itself
                ASMOperand tmp = new ASMOperand(this.registerManager.acquireRegister(Register.Type.INT));
                this.asmWriter.mov(tmp, right);
                if (tok.getChild(0).isType(TokenType.IDENTIFIER))
                    this.asmWriter.setComment(tok.getChild(0).value + " = " + right);
                this.asmWriter.mov(left, tmp);
                this.registerManager.freeRegister(tmp.getRegister());
            }
        } else {
            if (tok.getChild(0).isType(TokenType.IDENTIFIER))
                this.asmWriter.setComment(tok.getChild(0).value + " = " + right);
            this.asmWriter.mov(left, right);
        }
        this.registerManager.freeRegisters();
    }

    private void handleIf(Token tok) throws RegisterManager.RegisterAllocationException {
        this.genCode(tok.getChild(0));  // generate a cmp instruction
        Instruction jump = jumpMap.get(tok.getChild(0).type);
        int branchNum = this.labelList.getNextLabelNumber();
        String thenLabel = "THEN_CLAUSE" + branchNum;
        String endIfLabel = "ENDIF" + branchNum;
        this.asmWriter.print(jump, thenLabel);
        // generate else branch
        if (tok.children.size() == 3) {
            genCode(tok.getChild(2));
        } else {
            this.asmWriter.comment("no else branch");
        }
        this.asmWriter.print(Instruction.jmp, endIfLabel);
        this.asmWriter.label(thenLabel);
        genCode(tok.getChild(1));
        this.asmWriter.label(endIfLabel);
    }

    private void handleLabel(Token tok) throws RegisterManager.RegisterAllocationException {
        this.asmWriter.label("L" + tok.value.toString());
    }

    private ASMOperand handleLiteral(Token tok) throws RegisterManager.RegisterAllocationException {
        if (tok.isType(TokenType.INTEGER)) {
            return new ASMOperand((Integer) tok.value);
        } else if (tok.isType(TokenType.REAL)) {
            return new ASMOperand(ASMOperand.Type.memory, ASMOperand.Size.bits32,
                    this.floatLiteralManager.getLabel((Double) tok.value));
        } else if (tok.isType(TokenType.STRING)) {
            return new ASMOperand(ASMOperand.Type.immediate, ASMOperand.Size.bits32,
                    this.stringLiteralManager.getLabel((String) tok.value));
        } else {
            throw new Error("TODO");
        }
    }

    private ASMOperand handleCast(Token tok) throws RegisterManager.RegisterAllocationException {
        if (tok.isType(TokenType.CASTREAL)) {
            ASMOperand arg = this.genExpr(tok.getChild(0));
            ensureIntOnFloatStack(arg);
            return ASMOperand.ST0;
        } else if (tok.isType(TokenType.CASTINT)) {
            ASMOperand arg = this.genExpr(tok.getChild(0));
            assert(arg.getRegister().equals(Register.st0));
            ASMOperand tmpMem = new ASMOperand(ASMOperand.Type.memory, ASMOperand.INT_SIZE,
                    this.tempStorageManager.acquireTempStorage(ASMOperand.INT_SIZE.bytes()));
            this.asmWriter.print(Instruction.fistp, tmpMem);
            return tmpMem;
        } else {
            throw new Error("Unknown cast type " + tok.type);
        }
    }

    private void handleBoolean(Token tok) throws RegisterManager.RegisterAllocationException {
        ASMOperand left = this.genExpr(tok.getChild(0));
        ASMOperand right = this.genExpr(tok.getChild(1));

        // cannot compare memory to memory
        if (left.isMem() && right.isMem()) {
            ASMOperand tmp = new ASMOperand(this.registerManager.acquireRegister(Register.Type.INT));
            this.asmWriter.mov(tmp, right);
            this.tempStorageManager.freeTempStorage(right.size().bytes());
            right = tmp;
        }

        if (tok.getChild(0).isType(TokenType.IDENTIFIER))
            this.asmWriter.setComment(tok.getChild(0).value + " " + tok.type.stringRepr + " " + right + "?");
        else
            this.asmWriter.setComment(left + " " + tok.type.stringRepr + " " + right + "?");
        this.asmWriter.print(Instruction.cmp, left, right);
        this.registerManager.freeRegister(left.getRegister());
        this.registerManager.freeRegister(right.getRegister());
    }

    private ASMOperand handleFuncall(Token tok) throws RegisterManager.RegisterAllocationException {
        assert(tok.symbolTableEntry instanceof Symbol.FunctionSymbol);
        Symbol.FunctionSymbol functionSymbol = (Symbol.FunctionSymbol) tok.symbolTableEntry;

        /* save EAX if in use (don't mark it as free so we can restore it later) */
        if (this.registerManager.isAcquired(Register.eax)) {
            this.asmWriter.setComment("save eax for function call");
            this.asmWriter.push(ASMOperand.EAX);
        }

        int offset = this.pushFunctionArgs(tok);
        this.asmWriter.print(Instruction.call, toCompiledFunctionName(functionSymbol.name));
        this.asmWriter.clearStackRoom(offset);
        if (functionSymbol.resultType.name.equals("real")) {
            return ASMOperand.ST0;
        } else if (!functionSymbol.resultType.equals(Symbol.NULLSYMBOL)) {
            /* if EAX was in use before the function call, restore it.
             * Notice that we move the function result to a different register, since some other
             * code expects the value in eax not to change. */
            if (this.registerManager.isAcquired(Register.eax)) {
                ASMOperand result = new ASMOperand(this.registerManager.acquireRegister(Register.Type.INT));
                this.asmWriter.mov(result, ASMOperand.EAX);
                this.asmWriter.pop(ASMOperand.EAX);
                return result;
            } else {
                return ASMOperand.EAX;
            }
        } else {
            return null;
        }
    }

    private String toCompiledFunctionName(String functionName) {
        if (parser.symbolTable.inLevelZero(functionName)) {
            // C library functions normally take doubles. We'll call special functions for floats for now.
            if (Symbol.REAL_SIZE == 4 && doubleMathFunctions.contains(functionName)) {
                return "_" + functionName + "32";
            }
            /* On Windows, function names compiled from C are prefixed with an underscore.
             * We make sure our built-in functions have the correct identifiers. */
            return "_" + functionName;
        }
        return functionName;
    }

    private int pushFunctionArgs(Token tok) throws RegisterManager.RegisterAllocationException {
        assert(tok.isType(TokenType.FUNCALL));
        int offsetTotal = 0;
        // todo: push args in reverse order
        for (Token t: (List<Token>) tok.children) {
            ASMOperand op = genExpr(t);
            if (op.type().equals(ASMOperand.Type.register) && op.getRegister().equals(Register.st0)) {
                this.asmWriter.makeStackRoom(ASMOperand.REAL_SIZE.bytes());
                this.asmWriter.print(Instruction.fstp,
                        new ASMOperand(ASMOperand.Type.memory, ASMOperand.REAL_SIZE, Register.esp));
                offsetTotal += ASMOperand.REAL_SIZE.bytes();
            } else {
                this.asmWriter.push(op);
                offsetTotal += ASMOperand.INT_SIZE.bytes();
            }
        }
        return offsetTotal;
    }

    private ASMOperand handleArithmetic(Token tok) throws RegisterManager.RegisterAllocationException {
        if (tok.datatype.name.equals("real")) {
            return this.genFloatArith(tok);
        } else {
            return this.genIntArith(tok);
        }
    }

    private ASMOperand genIntArith(Token tok) throws RegisterManager.RegisterAllocationException {
        ASMOperand left = this.genExpr(tok.getChild(0));
        ASMOperand right = null;
        if (tok.children.size() > 1) {     // negation has only one argument
            right = this.genExpr(tok.getChild(1));
        }

        // be careful not to assign directly to `left` if it's a memory location
        ASMOperand dest = new ASMOperand(this.registerManager.acquireRegister(Register.Type.INT));
        this.asmWriter.mov(dest, left);
        this.registerManager.freeRegister(left.getRegister());

        if (tok.isType(TokenType.PLUS)) {
            this.asmWriter.print(Instruction.add, dest, right);
        } else if (tok.isType(TokenType.MINUS)) {
            if (tok.children.size() > 1) {
                this.asmWriter.print(Instruction.sub, dest, right);
            } else {  // handle negation
                this.asmWriter.print(Instruction.neg, dest);
            }
        } else if (tok.isType(TokenType.TIMES)) {
            this.asmWriter.print(Instruction.imul, dest, right);
        } else {
            throw new Error("TODO: unimplemented integer operation " + tok.type);
        }
        if (right != null)
            this.registerManager.freeRegister(right.getRegister());
        return dest;
    }

    private ASMOperand genFloatArith(Token tok) throws RegisterManager.RegisterAllocationException {
        // ensure left operand is on the float stack
        ASMOperand left = this.genExpr(tok.getChild(0));
        ensureFloatOnFloatStack(left);

        ASMOperand right = null;
        if (tok.children.size() > 1) {     // negation has only one argument
            right = this.genExpr(tok.getChild(1));
            ensureFloatOnFloatStack(right);
        }

        if (tok.isType(TokenType.PLUS)) {
            this.asmWriter.print(Instruction.faddp, ASMOperand.ST1);
        } else if (tok.isType(TokenType.MINUS)) {
            if (tok.children.size() > 1) {
                this.asmWriter.print(Instruction.fsubp, ASMOperand.ST1);
            } else {  // handle negation
                this.asmWriter.print(Instruction.fchs);
            }
        } else if (tok.isType(TokenType.TIMES)) {
            this.asmWriter.print(Instruction.fmulp, ASMOperand.ST1);
        } else if (tok.isType(TokenType.DIVIDE)) {
            this.asmWriter.print(Instruction.fdivp, ASMOperand.ST1);
        }

        this.registerManager.freeRegister(left.getRegister());
        if (right != null)
            this.registerManager.freeRegister(right.getRegister());
        return ASMOperand.ST0;
    }

    private void ensureIntOnFloatStack(ASMOperand op) throws RegisterManager.RegisterAllocationException {
        if (op.isMem()) {
            this.asmWriter.print(Instruction.fild, op);
        } else if (op.type().equals(ASMOperand.Type.immediate)) {
            // we can only FILD with a memory location
            String tmpMemId = this.tempStorageManager.acquireTempStorage(ASMOperand.INT_SIZE.bytes());
            ASMOperand tmp = new ASMOperand(ASMOperand.Type.memory, ASMOperand.INT_SIZE, tmpMemId);
            this.asmWriter.mov(tmp, op);
            this.asmWriter.print(Instruction.fild, tmp);
            this.tempStorageManager.freeTempStorage(tmp.size().bytes());
        } else if (op.type().equals(ASMOperand.Type.register)) {
            if (!op.getRegister().equals(Register.st0)) {
                this.asmWriter.print(Instruction.fild, op);
                this.registerManager.freeRegister(op.getRegister());
            }
        }
    }

    private void ensureFloatOnFloatStack(ASMOperand op) throws RegisterManager.RegisterAllocationException {
        if (op.isMem()) {
            this.asmWriter.print(Instruction.fld, op);
        } else if (op.type().equals(ASMOperand.Type.immediate)) {
            ASMOperand tmp = new ASMOperand(this.registerManager.acquireRegister(Register.Type.INT));
            this.asmWriter.mov(tmp, op);
            this.asmWriter.print(Instruction.fld, tmp);
            this.registerManager.freeRegister(tmp.getRegister());
        } else if (op.type().equals(ASMOperand.Type.register)) {
            if (!(op.getRegister().equals(Register.st0))) {
                throw new Error("TODO: " + op + ", " + op.type());
            }
        }
    }
}
