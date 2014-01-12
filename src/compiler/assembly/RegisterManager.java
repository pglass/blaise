package compiler.assembly;

import java.util.HashSet;

public class RegisterManager {

    public static class RegisterAllocationException extends Exception {
        public RegisterAllocationException(String message) {
            super(message);
        }
    }

    private HashSet<Register> acquiredRegisters;

    public RegisterManager() {
        this.acquiredRegisters = new HashSet<Register>();
    }

    public boolean isAcquired(Register reg) {
        return this.acquiredRegisters.contains(reg);
    }

    public void freeRegisters() {
        this.acquiredRegisters.clear();
    }

    public void freeRegister(Register reg) {
        this.acquiredRegisters.remove(reg);
    }

    public Register acquireRegister(Register.Type datatype) throws RegisterAllocationException {
        for (Register reg: Register.values()) {
            if (reg.datatype == datatype
                    && reg.instructionSet == Register.Type.X86
                    && reg.type != Register.Type.SPECIAL
                    && !this.acquiredRegisters.contains(reg)) {
                this.acquiredRegisters.add(reg);
                return reg;
            }
        }
        throw new RegisterAllocationException("Failed to acquire register of datatype " + datatype.name());
    }

    /** Acquire a particular kind of register
     *
     * @param datatype Either {@link Register.Type#INT} or {@link Register.Type#FLOAT}
     * @param type Either {@link Register.Type#VOLATILE} or {@link Register.Type#NONVOLATILE}
     * @param size Either {@link Register.Type#SIZE32}, {@link Register.Type#SIZE64},
     *             or {@link Register.Type#SIZE128}
     * @return A free {@link Register} with the given type attributes
     * @throws RegisterAllocationException if no free register of the given type exists
     */
    public Register acquireRegister(Register.Type datatype, Register.Type type, Register.Type size)
            throws RegisterAllocationException {
        // not the fastest way, but there are not very many (45) registers to look through
        for (Register reg: Register.values()) {
            if (reg.is(datatype, type, size) && !this.acquiredRegisters.contains(reg)) {
                this.acquiredRegisters.add(reg);
                return reg;
            }
        }
        throw new RegisterAllocationException("Cannot acquire a register of type "
                + type.name() + " and size " + size.name());
    }
}
