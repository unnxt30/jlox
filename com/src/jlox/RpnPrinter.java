package jlox;

class RpnPrinter implements Expr.Visitor<String>{
    String print(Expr expr) {
        return expr.accept(this);
    }
    @Override
    public String visitBinaryExpr(Expr.Binary expr){
        return expr.left.accept(this) + " " + expr.right.accept(this) + " " + expr.operator.lexeme;
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return expr.expression.accept(this);
    }
    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        return expr.value.toString();
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        String operator = expr.operator.lexeme;
        if (expr.operator.type == TokenType.MINUS) {
            // Can't use same symbol for unary and binary.
            operator = "~";
        }

        return expr.right.accept(this) + " " + operator;
    }

    public static void main(String[] args) {
        Expr expression = new Expr.Binary(new Expr.Literal(1), new Token(TokenType.PLUS, "+", null, 1),new Expr.Literal(2));

        System.out.println(new RpnPrinter().print(expression));
    }
}

