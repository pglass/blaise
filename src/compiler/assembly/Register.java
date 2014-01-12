package compiler.assembly;

public enum Register {
    /*****************************
     * Integer registers
     *****************************/
    rbx (Type.INT, Type.NONVOLATILE, Type.SIZE64, Type.X64),
    rcx (Type.INT,    Type.VOLATILE, Type.SIZE64, Type.X64),
    rdx (Type.INT,    Type.VOLATILE, Type.SIZE64, Type.X64),
    rsi (Type.INT, Type.NONVOLATILE, Type.SIZE64, Type.X64),
    rdi (Type.INT, Type.NONVOLATILE, Type.SIZE64, Type.X64),
    rax (Type.INT,    Type.VOLATILE, Type.SIZE64, Type.X64),
    rbp (Type.INT,     Type.SPECIAL, Type.SIZE64, Type.X64),
    rsp (Type.INT,     Type.SPECIAL, Type.SIZE64, Type.X64),
    r8d (Type.INT,    Type.VOLATILE, Type.SIZE64, Type.X64),
    r9d (Type.INT,    Type.VOLATILE, Type.SIZE64, Type.X64),
    r10d(Type.INT,    Type.VOLATILE, Type.SIZE64, Type.X64),
    r11d(Type.INT,    Type.VOLATILE, Type.SIZE64, Type.X64),
    r12d(Type.INT, Type.NONVOLATILE, Type.SIZE64, Type.X64),
    r13d(Type.INT, Type.NONVOLATILE, Type.SIZE64, Type.X64),
    r14d(Type.INT, Type.NONVOLATILE, Type.SIZE64, Type.X64),
    r15d(Type.INT, Type.NONVOLATILE, Type.SIZE64, Type.X64),

    ebx(Type.INT, Type.NONVOLATILE, Type.SIZE32, Type.X86),
    ecx(Type.INT,    Type.VOLATILE, Type.SIZE32, Type.X86),
    edx(Type.INT,    Type.VOLATILE, Type.SIZE32, Type.X86),
    esi(Type.INT, Type.NONVOLATILE, Type.SIZE32, Type.X86),
    edi(Type.INT, Type.NONVOLATILE, Type.SIZE32, Type.X86),
    eax(Type.INT,    Type.VOLATILE, Type.SIZE32, Type.X86),
    ebp(Type.INT,     Type.SPECIAL, Type.SIZE32, Type.X86),
    esp(Type.INT,     Type.SPECIAL, Type.SIZE32, Type.X86),
    r8 (Type.INT,    Type.VOLATILE, Type.SIZE32, Type.X64),
    r9 (Type.INT,    Type.VOLATILE, Type.SIZE32, Type.X64),
    r10(Type.INT,    Type.VOLATILE, Type.SIZE32, Type.X64),
    r11(Type.INT,    Type.VOLATILE, Type.SIZE32, Type.X64),
    r12(Type.INT, Type.NONVOLATILE, Type.SIZE32, Type.X64),
    r13(Type.INT, Type.NONVOLATILE, Type.SIZE32, Type.X64),
    r14(Type.INT, Type.NONVOLATILE, Type.SIZE32, Type.X64),
    r15(Type.INT, Type.NONVOLATILE, Type.SIZE32, Type.X64),

    /*****************************
     * Floating point registers
     *****************************/
    st0(Type.FLOAT, Type.NONVOLATILE, Type.SIZE32, Type.X86),
    st1(Type.FLOAT, Type.NONVOLATILE, Type.SIZE32, Type.X86),
    st2(Type.FLOAT, Type.NONVOLATILE, Type.SIZE32, Type.X86),
    st3(Type.FLOAT, Type.NONVOLATILE, Type.SIZE32, Type.X86),
    st4(Type.FLOAT, Type.NONVOLATILE, Type.SIZE32, Type.X86),
    st5(Type.FLOAT, Type.NONVOLATILE, Type.SIZE32, Type.X86),
    st6(Type.FLOAT, Type.NONVOLATILE, Type.SIZE32, Type.X86),
    st7(Type.FLOAT, Type.NONVOLATILE, Type.SIZE32, Type.X86),

    /*****************************
     * SSE Registers
     *****************************/
    xmm0 (Type.SSE,    Type.VOLATILE, Type.SIZE128, Type.X86),
    xmm1 (Type.SSE,    Type.VOLATILE, Type.SIZE128, Type.X86),
    xmm2 (Type.SSE,    Type.VOLATILE, Type.SIZE128, Type.X86),
    xmm3 (Type.SSE,    Type.VOLATILE, Type.SIZE128, Type.X86),
    xmm4 (Type.SSE,    Type.VOLATILE, Type.SIZE128, Type.X86),
    xmm5 (Type.SSE,    Type.VOLATILE, Type.SIZE128, Type.X86),
    xmm6 (Type.SSE, Type.NONVOLATILE, Type.SIZE128, Type.X86),
    xmm7 (Type.SSE, Type.NONVOLATILE, Type.SIZE128, Type.X86),
    xmm8 (Type.SSE, Type.NONVOLATILE, Type.SIZE128, Type.X64),
    xmm9 (Type.SSE, Type.NONVOLATILE, Type.SIZE128, Type.X64),
    xmm10(Type.SSE, Type.NONVOLATILE, Type.SIZE128, Type.X64),
    xmm11(Type.SSE, Type.NONVOLATILE, Type.SIZE128, Type.X64),
    xmm12(Type.SSE, Type.NONVOLATILE, Type.SIZE128, Type.X64),
    xmm13(Type.SSE, Type.NONVOLATILE, Type.SIZE128, Type.X64),
    xmm14(Type.SSE, Type.NONVOLATILE, Type.SIZE128, Type.X64),
    xmm15(Type.SSE, Type.NONVOLATILE, Type.SIZE128, Type.X64),
    ;

    /** Some properties to categorize the registers */
    public static enum Type {
        SIZE128,
        SIZE64,
        SIZE32,
        VOLATILE,
        NONVOLATILE,
        SPECIAL,
        INT,
        FLOAT,
        SSE,
        X86,
        X64
    }

    /* Register class definition */
    public Type datatype;
    public Type type;
    public Type size;
    public Type instructionSet;

    Register(Type datatype, Type type, Type size, Type instructionSet) {
        this.datatype = datatype;
        this.type = type;
        this.size = size;
        this.instructionSet = instructionSet;
    }

    public String toString() {
        return this.name();
    }

    public boolean is(Type datatype, Type type, Type size) {
        return this.datatype.equals(datatype) && this.type.equals(type) && this.size.equals(size);
    }
}
