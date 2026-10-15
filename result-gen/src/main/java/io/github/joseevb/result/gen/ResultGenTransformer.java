package io.github.joseevb.result.gen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * Minimal AST-based source-to-source transformer for: Result.gen($ -> { var x = $.bind(result);
 * return value; }).
 *
 * <p>Prototype limitations: one generator invocation per compilation unit; each bind must be a
 * simple var declaration with one fragment; bound Result types must be resolvable and must not use
 * wildcard/captured type arguments.
 */
public final class ResultGenTransformer {
  private ResultGenTransformer() {}

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      throw new IllegalArgumentException(
          "Usage: ResultGenTransformer <input.java> <output.java> [classpath entries...]");
    }

    Path input = Path.of(args[0]);
    Path output = Path.of(args[1]);
    String[] classpath = Arrays.copyOfRange(args, 2, args.length);
    String source = Files.readString(input, StandardCharsets.UTF_8);
    String transformed = transform(source, input.getFileName().toString(), classpath);
    Files.createDirectories(output.toAbsolutePath().getParent());
    Files.writeString(output, transformed, StandardCharsets.UTF_8);
  }

  public static String transform(String source, String unitName, String[] classpath) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(unitName, "unitName");

    ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setSource(source.toCharArray());
    parser.setUnitName(unitName);
    parser.setResolveBindings(true);
    parser.setBindingsRecovery(false);
    parser.setEnvironment(classpath, null, null, true);
    Map<String, String> options = JavaCore.getOptions();
    JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
    parser.setCompilerOptions(options);

    CompilationUnit unit = (CompilationUnit) parser.createAST(null);
    List<GenCall> calls = new ArrayList<>();
    unit.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(MethodInvocation node) {
            if (node.getName().getIdentifier().equals("gen")
                && node.getExpression() != null
                && node.getExpression().toString().endsWith("Result")) {
              if (node.arguments().size() != 1
                  || !(node.arguments().get(0) instanceof LambdaExpression lambda)
                  || !(lambda.getBody() instanceof Block body)) {
                throw new IllegalArgumentException("Result.gen requires a block lambda: " + node);
              }
              calls.add(new GenCall(node, body));
            }
            return true;
          }
        });

    if (calls.isEmpty()) {
      throw new IllegalArgumentException("No Result.gen($ -> { ... }) call found");
    }
    if (calls.size() != 1) {
      throw new IllegalArgumentException("Prototype currently supports one gen call per file");
    }

    GenCall call = calls.getFirst();
    String unionName = "ResultGenErrors_" + digest(unitName + source).substring(0, 10);
    List<Bind> binds = discoverBinds(call.body(), source);
    if (binds.isEmpty()) {
      throw new IllegalArgumentException("Generator block must contain at least one $.bind(...)");
    }

    List<Edit> edits = new ArrayList<>();
    for (int i = 0; i < binds.size(); i++) {
      Bind bind = binds.get(i);
      edits.add(
          new Edit(
              bind.statement().getStartPosition(),
              bind.statement().getLength(),
              renderBind(bind, unionName, i)));
    }
    edits.addAll(rewriteErrorSwitches(unit, source, binds, unionName));

    StringBuilder union =
        new StringBuilder("\n\nsealed interface ").append(unionName).append(" permits ");
    for (int i = 0; i < binds.size(); i++) {
      if (i > 0) union.append(", ");
      union.append(unionName).append(".Bind").append(i);
    }
    union.append(" {\n");
    for (int i = 0; i < binds.size(); i++) {
      Bind bind = binds.get(i);
      union
          .append("  record Bind")
          .append(i)
          .append("(")
          .append(bind.errorType())
          .append(" value) implements ")
          .append(unionName)
          .append(" {}\n");
    }
    union.append("}\n");
    edits.add(new Edit(source.length(), 0, union.toString()));

    edits.sort(Comparator.comparingInt(Edit::start).reversed());
    StringBuilder result = new StringBuilder(source);
    for (Edit edit : edits) {
      result.replace(edit.start(), edit.start() + edit.length(), edit.replacement());
    }
    return result.toString();
  }

  /**
   * Rewrites source-level patterns over original error types into patterns over the generated
   * sealed union. The source switch is checked here for exhaustiveness because javac only sees the
   * transformed source.
   */
  private static List<Edit> rewriteErrorSwitches(
      CompilationUnit unit, String source, List<Bind> binds, String unionName) {
    Map<String, Integer> variants = new LinkedHashMap<>();
    for (int i = 0; i < binds.size(); i++) {
      String simpleName = simpleTypeName(binds.get(i).errorType());
      Integer previous = variants.putIfAbsent(simpleName, i);
      if (previous != null) {
        throw new IllegalArgumentException(
            "Switch rewriting currently requires distinct error types; repeated type: "
                + simpleName);
      }
    }

    List<Edit> edits = new ArrayList<>();
    unit.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SwitchStatement node) {
            rewriteSwitch(
                node.getExpression(), node.statements(), source, variants, unionName, edits);
            return true;
          }

          @Override
          public boolean visit(SwitchExpression node) {
            rewriteSwitch(
                node.getExpression(), node.statements(), source, variants, unionName, edits);
            return true;
          }
        });
    return edits;
  }

  private static void rewriteSwitch(
      Expression selector,
      List<?> statements,
      String source,
      Map<String, Integer> variants,
      String unionName,
      List<Edit> edits) {
    Pattern labelPattern =
        Pattern.compile("^\\s*case\\s+([\\w.$]+)\\s+([A-Za-z_$][\\w$]*)\\s*->\\s*$");
    Map<String, Integer> matched = new LinkedHashMap<>();
    List<Edit> caseEdits = new ArrayList<>();
    List<String> switchLabels = new ArrayList<>();
    for (Object statement : statements) {
      if (!(statement instanceof SwitchCase switchCase)) continue;
      String caseSource =
          source.substring(
              switchCase.getStartPosition(),
              switchCase.getStartPosition() + switchCase.getLength()).strip();
      switchLabels.add(caseSource);
      Matcher matcher = labelPattern.matcher(caseSource);
      if (!matcher.matches()) continue;
      String typeName = simpleTypeName(matcher.group(1));
      Integer variant = variants.get(typeName);
      if (variant == null) continue;
      if (matched.putIfAbsent(typeName, variant) != null) {
        throw new IllegalArgumentException("Duplicate error case in Result switch: " + typeName);
      }
      String variable = matcher.group(2);
      caseEdits.add(
          new Edit(
              switchCase.getStartPosition(),
              switchCase.getLength(),
              "case " + unionName + ".Bind" + variant + "(var " + variable + ") ->"));
    }
    if (matched.isEmpty()) return;

    if (caseEdits.size() != switchLabels.size()) {
      String unsupported =
          switchLabels.stream()
              .filter(
                  label ->
                      !label.matches(
                          "^case\\s+[\\w.$]+\\s+[A-Za-z_$][\\w$]*\\s*->$"))
              .findFirst()
              .orElseGet(
                  () ->
                      switchLabels.stream()
                          .filter(
                              label -> {
                                Matcher matcher = labelPattern.matcher(label);
                                return matcher.matches()
                                    && !variants.containsKey(simpleTypeName(matcher.group(1)));
                              })
                          .findFirst()
                          .orElse("unknown case"));
      throw new IllegalArgumentException(
          "Result error switch may only contain cases for bound error types; unsupported case: "
              + unsupported);
    }

    List<String> missing =
        variants.keySet().stream().filter(typeName -> !matched.containsKey(typeName)).toList();
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException(
          "Non-exhaustive Result error switch; missing cases: " + String.join(", ", missing));
    }

    edits.add(
        new Edit(
            selector.getStartPosition(),
            selector.getLength(),
            "(" + unionName + ") (" + selector + ")"));
    edits.addAll(caseEdits);
  }

  private static String simpleTypeName(String typeName) {
    int lastDot = Math.max(typeName.lastIndexOf('.'), typeName.lastIndexOf('$'));
    return typeName.substring(lastDot + 1);
  }

  private static List<Bind> discoverBinds(Block body, String source) {
    List<Bind> binds = new ArrayList<>();
    for (Object rawStatement : body.statements()) {
      if (!(rawStatement instanceof VariableDeclarationStatement statement)) continue;
      if (statement.fragments().size() != 1
          || !(statement.fragments().getFirst() instanceof VariableDeclarationFragment fragment)
          || !(fragment.getInitializer() instanceof MethodInvocation invocation)
          || !invocation.getName().getIdentifier().equals("bind")
          || invocation.arguments().size() != 1) {
        continue;
      }
      if (!"var".equals(statement.getType().toString())) {
        throw new IllegalArgumentException(
            "Only var declarations are supported for bind: " + statement);
      }
      Expression resultExpression = (Expression) invocation.arguments().getFirst();
      ITypeBinding resultType = resultExpression.resolveTypeBinding();
      if (resultType == null || resultType.getTypeArguments().length != 2
          || !resultType.getErasure().getQualifiedName()
              .equals("io.github.joseevb.result.Result")) {
        throw new IllegalArgumentException(
            "Cannot resolve bind argument as Result<T, E>: " + resultExpression);
      }
      String successType = sourceType(resultType.getTypeArguments()[0]);
      String errorType = sourceType(resultType.getTypeArguments()[1]);
      if (successType.isBlank() || errorType.isBlank()
          || successType.contains("capture#") || errorType.contains("capture#")
          || successType.contains("?") || errorType.contains("?")) {
        throw new IllegalArgumentException(
            "Wildcard/captured bind types are not supported yet: " + resultType);
      }
      binds.add(new Bind(statement, resultExpression.toString(),
          fragment.getName().getIdentifier(), successType, errorType));
    }
    return binds;
  }

  private static String renderBind(Bind bind, String unionName, int index) {
    return "var "
        + bind.variable()
        + " = $.bind(("
        + bind.expression()
        + ").mapErr(error -> new "
        + unionName
        + ".Bind"
        + index
        + "(error)));";
  }

  private static String sourceType(ITypeBinding binding) {
    String qualified = binding.getQualifiedName();
    return qualified.isBlank() ? binding.getName() : qualified;
  }

  private static String digest(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new AssertionError(exception);
    }
  }

  private record GenCall(MethodInvocation invocation, Block body) {}
  private record Bind(Statement statement, String expression, String variable,
      String successType, String errorType) {}
  private record Edit(int start, int length, String replacement) {}
}
