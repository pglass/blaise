package compiler;

import java.io.FileNotFoundException;

public class ParserRun {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Need a file");
        } else {
            try {
                Parser parser = new Parser(args[0]);
                Token code = parser.parse();
                parser.printSymbolTable(0);
                parser.printSymbolTable(1);
                System.out.println(code.toExprString());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}
