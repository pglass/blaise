package compiler;

import compiler.assembly.RegisterManager;

import java.io.FileNotFoundException;

public class CodeGenRun {
    public static String FILE = null;
    public static boolean DEBUG = false;

    public static void parseArgs(String[] args) {
        for (String s: args) {
            if (s.startsWith("-")) {
                if (s.equals("-d"))
                    DEBUG = true;
            } else {
                FILE = s;
            }
        }
    }

    public static void main(String[] args) {
        parseArgs(args);
        if (FILE == null) {
            System.err.println("Need a file");
        } else {
            try {
                Parser parser = new Parser(FILE);
                CodeGen gen = new CodeGen(parser, DEBUG);
                gen.write(System.out);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (RegisterManager.RegisterAllocationException e) {
                e.printStackTrace();
            }
        }
    }
}
