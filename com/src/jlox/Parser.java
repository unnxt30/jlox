package jlox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jlox.TokenType.*;

public class Parser {
    private boolean foundExpression = false;
    private boolean allowedExpression;

    private int loopDepth = 0;

    private static class ParseError extends RuntimeException {
    }

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // This is quite interesting, it sets the flag that an expression is found, when one expression has been found. so that any further expression are not evaluated in the same pass.
    Object parseREPL() {
        allowedExpression = true;
        List<Stmt> statements = new ArrayList<>();

        while (!isAtEnd()) {
            statements.add(declaration());

            if (foundExpression) {
                Stmt last = statements.get(statements.size() - 1);
                return ((Stmt.Expression) last).expression;
            }

            allowedExpression = false;
        }

        return statements;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;

    }


    private Expr expression() {
        return comma();
    }

    private Stmt declaration() {
        try {
            if (match(VAR)) return varDeclaration();

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt statement() {
        if (match(FOR)) return forStatement();
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(WHILE)) return whileStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        if (match(BREAK)) return breakStatement();


        return expressionStatement();

    }

    private Stmt breakStatement() {
        if (loopDepth == 0) {
            error(previous(), "No superceding loop found");
        }
        consume(SEMICOLON, "Expect a ; after 'break'.");
        return new Stmt.Break();
    }

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }

        consume(SEMICOLON, "EXPECT ';' after loop condition.");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }

        consume(RIGHT_PAREN, "Expect ')' after for clauses.");
        try {
            Stmt body = statement();
            if (increment != null) {
                body = new Stmt.Block(
                        Arrays.asList(
                                body,
                                new Stmt.Expression(increment)
                        )
                );
            }

            if (condition == null) condition = new Expr.Literal(true);
            body = new Stmt.While(condition, body);

            if (initializer != null) {
                body = new Stmt.Block(Arrays.asList(initializer, body));
            }
            return body;
        } finally {
            loopDepth -= 1;
        }

    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;

        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt printStatement() {
        Expr value = expression();

        consume(SEMICOLON, "Expected a ;");
        return new Stmt.Print(value);
    }


    // Logic taken from the grammar
    // varDecl → "var" IDENTIFIER ( "=" expression )? ";" ;

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expected an Identifier");
        Expr intializer = null;

        if (match(EQUAL)) {
            intializer = expression();
        }

        consume(SEMICOLON, "Expected a ;");
        return new Stmt.Var(name, intializer);
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition");
        try {
            loopDepth += 1;
            Stmt body = statement();

            return new Stmt.While(condition, body);
        } finally {
            loopDepth -= 1;
        }

    }

    private Stmt expressionStatement() {
        Expr value = expression();

        if (allowedExpression && isAtEnd()) {
            foundExpression = false;

        } else {
            consume(SEMICOLON, "Expected a ;");
        }
        return new Stmt.Expression(value);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Expr assignment() {
        Expr expr = or();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr comma() {
        Expr expr = assignment();

        while (match(COMMA)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // Grammar for equality is as follows: comparison (("!=" | "==") comparison)*
    private Expr equality() {
        // We store the left side of the expression
        Expr expr = comparison();

        // Here the !=, == symbols are matched, which verifies that it's an equality.
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous(); // This is the already ingested != or == operator
            Expr right = comparison(); // The right expression can again, recursively be parsed
            expr = new Expr.Binary(expr, operator, right); // Whole can be taken as a binary expression
        }

        return expr;
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;

    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
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
    private Expr comparison() {
        Expr expr = term();
        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }


    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        while (match(BANG, MINUS)) {
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

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_BRACE)) {

        }
        throw error(peek(), "Expect expression.");
    }


}
