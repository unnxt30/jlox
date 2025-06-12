package jlox;

import java.util.ArrayList;
import java.util.List;

import static jlox.TokenType.*;

public class Parser {
    private static class ParseError extends RuntimeException{}
    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens){
        this.tokens = tokens;
    }

    List<Stmt> parse(){
       List<Stmt> statements = new ArrayList<>();
       while(!isAtEnd()){
           statements.add(statement());
       }

       return statements;

    }


    private Expr expression(){
        return comma();
    }

    private Stmt statement(){
       if (match(PRINT)) return printStatement();

       return expressionStatement();

    }

    private Stmt printStatement(){
        Expr value = expression();

        consume(SEMICOLON, "Expected a ;");
        return new Stmt.Print(value);
    }

    private Stmt expressionStatement(){
        Expr value = expression();

        consume(SEMICOLON, "Expected a ;");

        return new Stmt.Expression(value);
    }

    private Expr comma(){
        Expr expr = equality();

        while(match(COMMA)){
           Token operator = previous();
           Expr right = equality();
           expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // Grammar for equality is as follows: comparison (("!=" | "==") comparison)*
    private Expr equality(){
        // We store the left side of the expression
        Expr expr = comparison();

        // Here the !=, == symbols are matched, which verifies that it's an equality.
        while(match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous(); // This is the already ingested != or == operator
            Expr right = comparison(); // The right expression can again, recursively be parsed
            expr = new Expr.Binary(expr, operator, right); // Whole can be taken as a binary expression
        }

        return expr;
    }

    private boolean match(TokenType... types){
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private Token consume(TokenType type, String message){
       if (check(type)) return advance();

       throw error(peek(), message);
    }

    private boolean check(TokenType type){
        if (isAtEnd()) return false;
        return peek().type == type;

    }

    private Token advance(){
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek(){
        return tokens.get(current);
    }

    private Token previous(){
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message){
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize(){
        advance();

        while(!isAtEnd()){
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }


    // comparison -> term (( ">" | ">=" | "<" | "<=") term)*
    private Expr comparison(){
        Expr expr = term();
        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term(){
        Expr expr = factor();

        while (match(MINUS, PLUS)){
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }


    private Expr factor(){
        Expr expr = unary();

        while (match(SLASH, STAR)){
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary(){
        while(match(BANG, MINUS)){
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }


        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }
        if (match(BANG_EQUAL, EQUAL_EQUAL)) {
            error(previous(), "Missing left-hand operand.");
            equality();
            return null;
        }

        if (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            error(previous(), "Missing left-hand operand.");
            comparison();
            return null;
        }

        if (match(PLUS)) {
            error(previous(), "Missing left-hand operand.");
            term();
            return null;
        }

        if (match(SLASH, STAR)) {
            error(previous(), "Missing left-hand operand.");
            factor();
            return null;
        }
        throw error(peek(), "Expect expression.");
    }


}
