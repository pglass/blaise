package compiler.assembly;


public enum Instruction {
    /*****************************************************
     * Conditional moves and jumps
     *  OF - overflow flag
     *  CF - carry flag
     *  ZF - zero flag
     *  SF - sign flag
     *  PF - parity flag
     *****************************************************/
    cmovo,      jo,     // if overflow              (OF = 1)
    cmovno,     jno,    // if not overflow          (OF = 0)
    cmovb,      jb,     // if below                 (CF = 1)
    cmovc,      jc,     // if carry                 (CF = 1)
    cmovnae,    jnae,   // if not above or equal    (CF = 1)
    cmovnc,     jnc,    // if not carry             (CF = 0)
    cmovnb,     jnb,    // if not below             (CF = 0)
    cmovae,     jae,    // if above or equal        (CF = 0)
    cmovz,      jz,     // if zero                  (ZF = 1)
    cmove,      je,     // if equal                 (ZF = 1)
    cmovnz,     jnz,    // if not zero              (ZF = 0)
    cmovne,     jne,    // if not equal             (ZF = 0)
    cmovbe,     jbe,    // if below or equal        (CF = 1 or ZF = 1)
    cmovna,     jna,    // if not above             (CF = 1 or ZF = 1)
    cmovnbe,    jnbe,   // if not below or equal    (CF = 0 and ZF = 0)
    cmova,      ja,     // if above                 (CF = 0 and ZF = 0)
    cmovs,      js,     // if sign                  (SF = 1)
    cmovns,     jns,    // if not sign              (SF = 0)
    cmovp,      jp,     // if parity                (PF = 1)
    cmovpe,     jpe,    // if parity even           (PF = 1)
    cmovnp,     jnp,    // if not parity            (PF = 0)
    cmovpo,     jpo,    // if parity odd            (PF = 0)
    cmovl,      jl,     // if less                  (SF != OF)
    cmovnge,    jnge,   // if not greater or equal  (SF != OF)
    cmovnl,     jnl,    // if nor less              (SF = OF)
    cmovge,     jge,    // if greater or equal      (SF = OF)
    cmovle,     jle,    // if less or equal         (ZF = 1 or SF != OF)
    cmovng,     jng,    // if not greater           (ZF = 1 or SF != OF)
    cmovnle,    jnle,   // if not less or equal     (ZF = 0 and SF = OF)
    cmovg,      jg,     // if greater               (ZF = 0 and SF = OF)

    /******************************************************
     * Floating point instructions
     * The floating point registers ST0-ST7 are organized
     * as a stack. ST0 is the top of the stack.
     ******************************************************/
    fld,        /* FLD src              Load a floating point number to the top of the float stack */
    fild,       /* FILD src             Convert an int to a float and load to the top of the float stack */
    fld1,       /* FLD1                 Load 1.0 to the top of the float stack */
    fldz,       /* FLDZ                 Load 0.0 to the top of the float stack */
    fst,        /* FST dst              store the top of the float stack (ST0) to a memory location */
    fstp,       /* FSTP dst             sames as FST but pop the value off the stack */
    fist,       /* FIST dst             convert ST0 to an integer and store to a memory location. The conversion
                                            to an integer depends on the coprocessor's control word. */
    fistp,      /* FISTP dst            same as FIST, except the top of the stack is popped */
    fxch,       /* FXCH ST*             exchange ST0 with one of ST1-ST7 */
    ffree,      /* FFREE ST*            frees a register on the stack by marking it as unused/empty */
    fadd,       /* FADD src             same as STO += src
                   FADD dst, ST0        same as dst += ST0 */
    fsub,       /* FSUB src             same as ST0 -= src
                   FSUB dst, ST0        same as dst -= ST0 */
    fsubr,      /* FSUBR src            same as ST0 = src - ST0
                   FSUBR dst, ST0       same as dst = ST0 - dst */
    fmul,       /* FMUL src             same as ST0 *= src
                   FMUL dst, ST0        same as dst *= ST0 */
    fdiv,       /* FDIV src             same as ST0 /= src
                   FDIV dst, ST0        same as dst /= ST0 */
    fdivr,      /* FDIVR src            same as ST0 = src / ST0
                   FDIVR dst, ST0       same as dst = ST0 / dst */
    fiadd,      /* FIADD src            same as ST0 += (float) src */
    fisub,      /* FISUB src            same as ST0 -= (float) src */
    fisubr,     /* FISUBR src           same as ST0 = ((float) src) - ST0 */
    fimul,      /* FIMUL src            same as ST0 *= (float) src */
    fidiv,      /* FIDIV src            same as ST0 /= (float) src */
    fidivr,     /* FIDIVR src           same as ST0 = ((float) src) / ST0 */
    faddp,      /* same as FADD  but pop the float stack as well */
    fsubp,      /* same as FSUB  but pop the float stack as well */
    fsubrp,     /* same as FSUBR but pop the float stack as well */
    fmulp,      /* same as FMUL  but pop the float stack as well */
    fdivp,      /* same as FDIV  but pop the float stack as well */
    fdivrp,     /* same as FDIVR but pop the float stack as well */
    fcom,       /* FCOM src             compare ST0 and src */
    fcomi,      /* FCOMI src            compares ST0 and src and directly modify the FLAGS register */
    ficom,      /* FICOM src            compares ST0 and (float) src */
    ftst,       /* FTST                 compares ST0 and 0.0 */
    fcomp,      /* same as FCOM  but pop the float stack as well */
    fcomip,     /* same as FCOMI but pop the float stack as well */
    fcompp,     /* same as FCOM  but pop the float stack twice */
    ficomp,     /* same as FICOM but pop the float stack as well */
    fchs,       /* FCHS                 same as ST0 = -ST0 */
    fabs,       /* FABS                 same as ST0 = |ST0| */
    fsqrt,      /* FSQRT                same as STO = sqrt(ST0) */
    fscale,     /* FSCALE               same as ST0 = ST0 * (2 ** ST1) -- fast multiplication by a power of two */
    fstsw,      /* FSTSW dst            store coprocessor status word into dst (a memory location or AX) */
    sahf,       /* SAHF                 store AH into the FLAGS register */
    lahf,       /* LAHF                 load the FLAGS register into AH */

    add,
    and,
    call,
    cmp,
    dec,
    div,
    enter,
    idiv,
    imul,
    inc,
    jmp,
    lea,
    leave,
    mov,
    mul,
    neg,
    nop,
    not,
    or,
    pop,
    popa,
    push,
    pusha,
    ret,
    sub,
    test,
    xchg,
    xor,
}