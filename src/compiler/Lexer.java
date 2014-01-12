package compiler;

import java.io.*;

public class Lexer {
    private static final boolean DEBUG = false;
    private static final int BUFFER_SIZE = 2;
    private static final char EOF = (char) -1;

    private PushbackReader stream;
    private boolean hasMore;

    public Lexer(String filename) throws FileNotFoundException {
        this.stream = new PushbackReader(new FileReader(filename), BUFFER_SIZE);
        this.hasMore = true;
    }

    public boolean hasMoreInput() {
        return this.hasMore;
    }

    private void logError(String message) {
        System.out.println("LexerError: " + message);
    }

    public Token nextToken() {
        while (this.consumeWhitespace() || this.consumeComment());

        Token t;
        if ((t = this.readIdentifier()) != null) {
            return t;
        } else if ((t = this.readString()) != null) {
            return t;
        } else if ((t = this.readSpecial()) != null) {
            return t;
        } else if ((t = this.readNumber()) != null) {
            return t;
        } else {
            this.nextChar();     // always progress through the stream
        }
        return null;
    }

    private Token readSpecial() {
        // look for a two-character operator/delimiter
        String s = this.peekChar() + "" + this.peek2Char();
        TokenType t = Token.lookupTokenType(s);
        if (t != null) {
            this.nextChar();
            this.nextChar();
            return new Token<String>(t, s);
        }
        // look for a one-character delimiter
        s = "" + this.peekChar();
        t = Token.lookupTokenType(s);
        if (t != null) {
            this.nextChar();
            return new Token<String>(t, s);
        }
        return null;
    }

    /* A Pascal string uses single quotes. Two adjacent single quotes are interpreted
     * as one single quote inside strings. That is, 'Dont''t forget' in Pascal is the
     * string "Don't forget" in Java
     */
    private Token readString() {
        char quoteChar = TokenType.QUOTE.stringRepr.charAt(0);
        if (this.peekChar() == quoteChar) {
            StringBuilder builder = new StringBuilder();
            this.nextChar();
            while (this.peekChar() != Lexer.EOF) {
                if (this.peekChar() == quoteChar && this.peek2Char() == quoteChar) {
                    builder.append(this.nextChar());
                    this.nextChar();
                } else if (this.peekChar() == quoteChar) {
                    break;
                } else {
                    builder.append(this.nextChar());
                }
            }
            if (this.peekChar() == quoteChar) {
                this.nextChar();
                return new Token<String>(TokenType.STRING, builder.toString());
            } else {
                this.logError("Unclosed string '" + builder.toString() + "'");
            }
        }
        return null;
    }

    private Token readIdentifier() {
        if (Character.isLetter(this.peekChar())) {
            StringBuilder builder = new StringBuilder();
            while (Character.isLetter(this.peekChar()) || Character.isDigit(this.peekChar())) {
                builder.append(this.nextChar());
            }
            String s = builder.toString();
            TokenType t = Token.lookupTokenType(s);
            if (t == null)
                return new Token<String>(TokenType.IDENTIFIER, s);
            else
                return new Token<String>(t, s);
        }
        return null;
    }

    private String readDigitString() {
        StringBuilder builder = new StringBuilder();
        while (Character.isDigit(this.peekChar())) {
            builder.append(this.nextChar());
        }
        return builder.toString();
    }

    private String readExponentString() {
        StringBuilder builder = new StringBuilder();
        if (this.peekChar() == 'e' || this.peekChar() == 'E') {
            builder.append(this.nextChar());
            if (this.peekChar() == '-' || this.peekChar() == '+')
                builder.append(this.nextChar());
            builder.append(readDigitString());
        }
        return builder.toString();
    }

    private Token parseDoubleToken(String s) {
        try {
            Double d = Double.parseDouble(s);
            return new Token<Double>(TokenType.REAL, d);
        } catch (NumberFormatException e) {
            this.logError("Failed to parse floating point value from '" + s + "'");
            return null;
        }
    }

    private Token parseIntToken(String s) {
        try {
            int num = Integer.parseInt(s);
            return new Token<Integer>(TokenType.INTEGER, num);
        } catch (NumberFormatException e) {
            this.logError("Failed to parse integer from '" + s + "'");
            return null;
        }
    }

    private Token readNumber() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.readDigitString());
        if (this.peekChar() == '.' && this.peek2Char() != '.') {    // 1..10 parses as '1', '..', '10'
            builder.append(this.nextChar())
                   .append(this.readDigitString())
                   .append(this.readExponentString());
            return this.parseDoubleToken(builder.toString());
        } else if (this.peekChar() == 'e' || this.peekChar() == 'E') {
            builder.append(this.readExponentString());
            return this.parseDoubleToken(builder.toString());
        } else if (builder.length() > 0) {
            return this.parseIntToken(builder.toString());
        }
        return null;
    }

    /* Comments start with either "{" or "(*" */
    private boolean readStartComment() {
        if (this.peekChar() == '{') {
            this.nextChar();
            return true;
        } else if (this.peekChar() == '(' && this.peek2Char() == '*') {
            this.nextChar();
            this.nextChar();
            return true;
        }
        return false;
    }

    /* Comments can end in either "}" or "*)" */
    private boolean readEndComment() {
        if (this.peekChar() == '}') {
            this.nextChar();
            return true;
        } else if (this.peekChar() == '*' && this.peek2Char() == ')') {
            this.nextChar();
            this.nextChar();
            return true;
        }
        return false;
    }

    private boolean consumeWhitespace() {
        boolean flag = Character.isWhitespace(this.peekChar());
        while (Character.isWhitespace(this.peekChar()))
            this.nextChar();
        return flag;
    }

    private boolean consumeComment() {
        if (this.readStartComment()) {
            boolean flag = false;
            while (this.peekChar() != Lexer.EOF && !(flag = this.readEndComment()))
                this.nextChar();
            return flag;
        }
        return false;
    }

    private char nextChar() {
        try {
            char c = (char) this.stream.read();
            if (DEBUG)
                System.out.println("nextChar() = " + c);
            if (c == Lexer.EOF)
                this.hasMore = false;
            return c;
        } catch (IOException e) {
            this.hasMore = false;
            return Lexer.EOF;
        }
    }

    private char peekChar() {
        try {
            char c = (char) this.stream.read();
            this.stream.unread(c);
            return c;
        } catch (IOException e) {
            return Lexer.EOF;
        }
    }

    private char peek2Char() {
        try {
            char c = (char) this.stream.read();
            char cc = (char) this.stream.read();
            this.stream.unread(cc);
            this.stream.unread(c);
            return cc;
        } catch (IOException e) {
            return Lexer.EOF;
        }
    }
}
