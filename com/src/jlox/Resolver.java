package jlox;

import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.HashMap;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private final Interpreter interpreter;
    private final Stack<Map<String, Variable>> scopes = new Stack<>();
    //    currentFunction is used to recognize return statements out of context.
    private FunctionType currentFunction = FunctionType.NONE;

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    //    Keeps track if the scope is inside a function body or not.
    private enum FunctionType {
        NONE,
        FUNCTION,
        METHOD
    }

    private class Variable {
        final Token name;
        VarState state;

        Variable(Token name, VarState state) {
            this.name = name;
            this.state = state;
        }
    }

    private enum VarState {
        DECLARED,
        USED,
        DEFINED
    }

    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    private void resolveFunction(Stmt.Function function, FunctionType type) {

        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;

        // Since a function declaration has it's own scope
        beginScope();
        for (Token param : function.function.parameters) {
            declare(param);
            define(param);
        }
        resolve(function.function.body);
        endScope();
        currentFunction = enclosingFunction;

    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }

    private void beginScope() {
        //Starts a new local Scope
        scopes.push(new HashMap<String, Variable>());
    }

    private void endScope() {
        Map<String, Variable> scope = scopes.pop();

        for (Map.Entry<String, Variable> entry : scope.entrySet()) {
            if (entry.getValue().state == VarState.DEFINED) {
                Lox.error(entry.getValue().name, "Local variable is not used.");
            }
        }

    }

    //    We have two separate methods for declaration and definition, so that we can tackle assigning a variable with its own name inside a local scope
    private void declare(Token name) {
        if (scopes.isEmpty()) {
            return;
        }

        Map<String, Variable> scope = scopes.peek();
        // Handle redeclaration of an already existing variable
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name, "Already a variable with this name in the scope");
        }

        scope.put(name.lexeme, new Variable(name, VarState.DECLARED));
    }

    private void define(Token name) {
        if (scopes.isEmpty()) return;

        scopes.peek().get(name.lexeme).state = VarState.DEFINED;
    }

    private void resolveLocal(Expr expr, Token name, Boolean isRead) {

        for (int i = scopes.size() - 1; i >= 0; i--) {
            // Checks all the local scopes and finds the definition. Then it's passed on to the Interpreter for its usage.
            if (scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size() - 1 - i);
                if (isRead) {
                    scopes.get(i).get(name.lexeme).state = VarState.USED;
                }
                return;
            }


        }

    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        declare(stmt.name);
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Top level return, really?");
        }
        if (stmt.value != null) {
            resolve(stmt.value);
        }

        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);

        for (Expr argument : expr.arguments) {
            resolve(argument);
        }

        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);

        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
//        Handling the case where the assignment might reference to another variable.
//        So first that gets resolved.
        resolve(expr.value);
//        Then the assignment itself.
        resolveLocal(expr, expr.name, false);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if (!scopes.isEmpty() && scopes.peek().containsKey(expr.name.lexeme) && scopes.peek().get(expr.name.lexeme).state == VarState.DECLARED) {
            Lox.error(expr.name, "Can't read local variable in its own initializer.");
        }

        resolveLocal(expr, expr.name, true);
        return null;
    }

    @Override
    public Void visitFunctionExpr(Expr.Function expr) {
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        return null;
    }
}
