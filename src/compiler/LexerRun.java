package compiler;

import java.io.FileNotFoundException;

public class LexerRun {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Require at least one file");
        } else {
            try {
                System.out.println("Lexing file '" + args[0] + "'");
                Lexer lexer = new Lexer(args[0]);
                Token tok = lexer.nextToken();
                while (lexer.hasMoreInput()) {
                    if (tok != null)
                        System.out.println(tok);
                        tok = lexer.nextToken();
                }
            } catch (FileNotFoundException e) {
                System.out.println(e.getMessage());
            }
        }
    }
}
