package tool;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Arrays;
import java.io.PrintWriter;

public class GenerateAst {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: generate_ast <output directory>");
            System.exit(64);
        }
        String outputDir = args[0];
        defineAst(outputDir, "Expr", Arrays.asList(
                "Assign: Token name, Expr value",
                "Binary: Expr left, Token operator, Expr right",
                "Grouping: Expr expression",
                "Literal: Object value",
                "Logical: Expr left, Token operator, Expr right",
                "Unary: Token operator, Expr right",
                "Variable: Token name"
        ));

        defineAst(outputDir, "Stmt", Arrays.asList(
                "Block: List<Stmt> statements",
                "Break: ",
                "Expression: Expr expression",
                "If: Expr condition, Stmt thenBranch," +
                        " Stmt elseBranch",
                "Print: Expr expression",
                "Var: Token name, Expr initializer",
                "While: Expr condition, Stmt body"
        ));
    }

    private static void defineAst(String outputDir, String baseName, List<String> types)
            throws FileNotFoundException, UnsupportedEncodingException {
        String path = outputDir + "/" + baseName + ".java";
        PrintWriter writer = new PrintWriter(path, "UTF-8");

        writer.println("package jlox;");
        writer.println();
        writer.println("import java.util.List;");
        writer.println();
        writer.println("abstract class " + baseName + " {");

        defineVisitor(writer, baseName, types);
        writer.println();

        // Generate each subclass
        for (String type : types) {
            String className = type.split(":")[0].trim();
            String fields = type.split(":")[1].trim();
            defineType(writer, baseName, className, fields);
        }

        // Add abstract accept method
        writer.println("    abstract <R> R accept(Visitor<R> visitor);");
        writer.println("}");
        writer.close();
    }

    private static void defineType(PrintWriter writer, String baseName, String className, String fieldList) {
        writer.println("    static class " + className + " extends " + baseName + " {");

        // Constructor
        writer.println("        " + className + "(" + fieldList + ") {");
        String[] fields;
        if (fieldList.isEmpty()) {
            fields = new String[0];
        } else {
            fields = fieldList.split(", ");
        }

        for (String field : fields) {
            String name = field.split(" ")[1];
            writer.println("            this." + name + " = " + name + ";");
        }
        writer.println("        }");
        writer.println();

        // Accept method implementation
        writer.println("        @Override");
        writer.println("        <R> R accept(Visitor<R> visitor) {");
        writer.println("            return visitor.visit" + className + baseName + "(this);");
        writer.println("        }");
        writer.println();

        // Field declarations
        for (String field : fields) {
            writer.println("        final " + field + ";");
        }

        writer.println("    }");
        writer.println();
    }

    private static void defineVisitor(PrintWriter writer, String baseName, List<String> types) {
        writer.println("    interface Visitor<R> {");
        for (String type : types) {
            String typeName = type.split(":")[0].trim();
            writer.println("        R visit" + typeName + baseName + "(" +
                    typeName + " " + baseName.toLowerCase() + ");");
        }
        writer.println("    }");
    }
}