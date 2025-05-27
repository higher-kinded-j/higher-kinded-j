// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.alias;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes({
  "org.higherkindedj.alias.GenerateHKTAlias",
  "org.higherkindedj.alias.GenerateHKTAliases"
})
@SupportedSourceVersion(SourceVersion.RELEASE_24)
public class HKTAliasProcessor extends AbstractProcessor {
  private boolean firstRound = true;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    // This init log is crucial to confirm the processor is even loaded.
    processingEnv
        .getMessager()
        .printMessage(Diagnostic.Kind.NOTE, "HKTAliasProcessor: Initialized successfully.");
    firstRound = true;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Messager messager = processingEnv.getMessager();

    if (firstRound) {
      messager.printMessage(Diagnostic.Kind.NOTE, "HKTAliasProcessor: process() called.");
      firstRound = false;
    }

    if (annotations.isEmpty() && roundEnv.processingOver()) {
      messager.printMessage(
          Diagnostic.Kind.NOTE,
          "HKTAliasProcessor: No annotations to process in this final round.");
      return false;
    }
    if (annotations.isEmpty()) {
      messager.printMessage(
          Diagnostic.Kind.NOTE,
          "HKTAliasProcessor: No annotations for this round, but processing not over.");
      return false;
    }

    messager.printMessage(
        Diagnostic.Kind.NOTE,
        "HKTAliasProcessor: Processing annotations: "
            + annotations.stream()
                .map(TypeElement::getQualifiedName)
                .collect(Collectors.joining(", ")));
    messager.printMessage(
        Diagnostic.Kind.NOTE,
        "HKTAliasProcessor: Round "
            + (roundEnv.processingOver() ? "final" : "intermediate")
            + ", error raised: "
            + roundEnv.errorRaised());

    for (TypeElement annotationElement : annotations) {
      messager.printMessage(
          Diagnostic.Kind.NOTE,
          "HKTAliasProcessor: Checking for elements annotated with "
              + annotationElement.getQualifiedName());
      Set<? extends Element> annotatedElements =
          roundEnv.getElementsAnnotatedWith(annotationElement);
      messager.printMessage(
          Diagnostic.Kind.NOTE,
          "HKTAliasProcessor: Found "
              + annotatedElements.size()
              + " elements for "
              + annotationElement.getQualifiedName());

      for (Element element : annotatedElements) {
        messager.printMessage(
            Diagnostic.Kind.NOTE,
            "HKTAliasProcessor: Processing element "
                + element.getSimpleName()
                + " ("
                + element.getKind()
                + ") annotated with "
                + annotationElement.getQualifiedName());

        GenerateHKTAlias[] aliasAnnos = element.getAnnotationsByType(GenerateHKTAlias.class);
        messager.printMessage(
            Diagnostic.Kind.NOTE,
            "HKTAliasProcessor: Element "
                + element.getSimpleName()
                + " has "
                + aliasAnnos.length
                + " @GenerateHKTAlias instances.");

        for (GenerateHKTAlias aliasAnno : aliasAnnos) {
          messager.printMessage(
              Diagnostic.Kind.NOTE,
              "HKTAliasProcessor: Attempting to generate for alias spec: " + aliasAnno.name());
          try {
            generateAliasInterface(aliasAnno, element);
          } catch (IOException e) {
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                "HKTAliasProcessor: IOException for alias "
                    + aliasAnno.name()
                    + ": "
                    + e.getMessage(),
                element);
          } catch (MirroredTypeException mte) {
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                "HKTAliasProcessor: MirroredTypeException for alias "
                    + aliasAnno.name()
                    + ". This usually means a class was not compiled/available. Details: "
                    + mte.getMessage()
                    + (mte.getTypeMirror() != null
                        ? " (TypeMirror available: " + mte.getTypeMirror().toString() + ")"
                        : ""),
                element);
          } catch (Exception e) { // Catch any other unexpected errors
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                "HKTAliasProcessor: UNEXPECTED Exception for alias "
                    + aliasAnno.name()
                    + ": "
                    + e.toString(),
                element);
            // Print stack trace to standard error for more details if possible
            // (Messager might not show full stack traces easily)
            e.printStackTrace();
          }
        }
      }
    }
    return true; // Indicates annotations were claimed and processed
  }

  private void generateAliasInterface(GenerateHKTAlias alias, Element originatingElement)
      throws IOException {
    String aliasName = alias.name();
    if (aliasName == null || aliasName.trim().isEmpty()) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "@GenerateHKTAlias 'name' cannot be empty.",
              originatingElement);
      return;
    }

    String localTargetPackage = alias.targetPackage();

    if (localTargetPackage.trim().isEmpty()) {
      String originatingPackage =
          processingEnv
              .getElementUtils()
              .getPackageOf(originatingElement)
              .getQualifiedName()
              .toString();
      if (originatingPackage.isEmpty()) {
        localTargetPackage = "org.higherkindedj.generated.aliases";
      } else {
        localTargetPackage = originatingPackage + ".aliases";
      }
    }

    final String finalTargetPackage = localTargetPackage;

    // --- Type Information Extraction ---
    String baseInterfaceName =
        getClassNameFromAnnotation(
            () -> alias.baseInterface(), originatingElement, aliasName, "baseInterface");
    String hktWitnessName =
        getClassNameFromAnnotation(
            () -> alias.hktWitness(), originatingElement, aliasName, "hktWitness");
    String fHktWitnessName =
        getClassNameFromAnnotation(
            () -> alias.f_hktWitness(), originatingElement, aliasName, "f_hktWitness");
    if (fHktWitnessName != null && fHktWitnessName.equals("void")) fHktWitnessName = null;
    String errorTypeName =
        getClassNameFromAnnotation(
            () -> alias.errorType(), originatingElement, aliasName, "errorType");
    if (errorTypeName != null && errorTypeName.equals("void")) errorTypeName = null;
    String valueTypeName =
        getClassNameFromAnnotation(
            () -> alias.valueType(), originatingElement, aliasName, "valueType");
    if (valueTypeName != null && valueTypeName.equals("void")) valueTypeName = null;
    List<String> valueTypeArgsNames =
        getClassNamesFromAnnotationArray(
            () -> Arrays.stream(alias.valueTypeArgs()).toList(),
            originatingElement,
            aliasName,
            "valueTypeArgs");
    if (valueTypeArgsNames != null
        && (valueTypeArgsNames.isEmpty()
            || (valueTypeArgsNames.size() == 1 && valueTypeArgsNames.get(0).equals("void")))) {
      valueTypeArgsNames = null;
    }

    // --- Collect all FQNs needed and determine imports ---
    Set<String> allFqnsNeeded = new HashSet<>();
    // ... (populate allFqnsNeeded as in the previous response)
    if (baseInterfaceName != null) allFqnsNeeded.add(baseInterfaceName);
    if (hktWitnessName != null) allFqnsNeeded.add(hktWitnessName);
    if (fHktWitnessName != null) allFqnsNeeded.add(fHktWitnessName);
    if (errorTypeName != null) allFqnsNeeded.add(errorTypeName);
    if (valueTypeName != null) allFqnsNeeded.add(valueTypeName);
    if (valueTypeArgsNames != null) allFqnsNeeded.addAll(valueTypeArgsNames);
    allFqnsNeeded.add("org.higherkindedj.hkt.Kind");
    if (baseInterfaceName != null) {
      if (getSimpleName(baseInterfaceName).contains("MonadError"))
        allFqnsNeeded.add("org.higherkindedj.hkt.MonadError");
      else if (getSimpleName(baseInterfaceName).contains("Monad"))
        allFqnsNeeded.add("org.higherkindedj.hkt.Monad");
    }

    Map<String, String> localSimpleNameToFqnForImport = new HashMap<>();
    Set<String> importsToWrite = new HashSet<>();

    for (String fqn : allFqnsNeeded) {
      if (fqn == null || fqn.equals("void")) continue;
      String simpleName = getSimpleName(fqn);
      String packageName = "";
      int lastDot = fqn.lastIndexOf('.');
      if (lastDot > 0) packageName = fqn.substring(0, lastDot);

      if (packageName.equals("java.lang")
          || packageName.equals(finalTargetPackage)) { // Use finalTargetPackage here
        continue;
      }
      if (!localSimpleNameToFqnForImport.containsKey(simpleName)) {
        localSimpleNameToFqnForImport.put(simpleName, fqn);
        importsToWrite.add(fqn);
      } else {
        if (!localSimpleNameToFqnForImport.get(simpleName).equals(fqn)) {
          // Conflict, simple name already mapped to a different FQN
        }
      }
    }
    // Assign to a final variable for lambda capture
    final Map<String, String> finalSimpleNameToFqnForImport = localSimpleNameToFqnForImport;

    StringBuilder sb = new StringBuilder();
    sb.append("package ").append(finalTargetPackage).append(";\n\n"); // Use finalTargetPackage

    for (String imp : importsToWrite) {
      sb.append("import ").append(imp).append(";\n");
    }
    sb.append("\n");
    // ... (Javadoc and interface declaration start)
    sb.append("/**\n");
    sb.append(" * Generated type alias for: ").append(aliasName).append("\n");
    sb.append(" * Source: ").append(originatingElement.getSimpleName()).append("\n");
    sb.append(" */\n");

    String genericParamsString = "";
    if (alias.genericParameters() != null && alias.genericParameters().length > 0) {
      genericParamsString = "<" + String.join(", ", alias.genericParameters()) + ">";
    }

    sb.append("public interface ").append(aliasName).append(genericParamsString);
    sb.append(" extends ")
        .append(
            getReferenceName(baseInterfaceName, finalTargetPackage, finalSimpleNameToFqnForImport));
    sb.append("<");

    // --- Construct generic arguments using final captured variables ---
    String hktRef =
        getReferenceName(hktWitnessName, finalTargetPackage, finalSimpleNameToFqnForImport);
    String fHktRef =
        (fHktWitnessName != null)
            ? getReferenceName(fHktWitnessName, finalTargetPackage, finalSimpleNameToFqnForImport)
            : null;
    String errorTypeRef =
        (errorTypeName != null)
            ? getReferenceName(errorTypeName, finalTargetPackage, finalSimpleNameToFqnForImport)
            : null;

    String witnessArg = hktRef;
    if (fHktRef != null) {
      witnessArg += "<" + fHktRef;
      if (hktWitnessName != null
          && hktWitnessName.equals("org.higherkindedj.hkt.trans.either_t.EitherTKind.Witness")
          && errorTypeRef != null) {
        witnessArg += ", " + errorTypeRef;
      }
      witnessArg += ">";
    }
    sb.append(witnessArg);

    String baseInterfaceSimpleName = getSimpleName(baseInterfaceName);
    if (baseInterfaceSimpleName.equals("MonadError") && errorTypeRef != null) {
      sb.append(", ").append(errorTypeRef);
    } else if (baseInterfaceSimpleName.equals("Kind")
        || baseInterfaceSimpleName.endsWith("Functor")
        || baseInterfaceSimpleName.endsWith("Applicative")
        || baseInterfaceSimpleName.endsWith("Monad")) {
      String valueTypeRef =
          (valueTypeName != null)
              ? getReferenceName(valueTypeName, finalTargetPackage, finalSimpleNameToFqnForImport)
              : null;
      if (valueTypeRef != null) {
        String vType = valueTypeRef;
        if (valueTypeArgsNames != null && !valueTypeArgsNames.isEmpty()) {
          // This is the part that caused the error, ensure captured variables are final
          String collectedValueTypeArgs =
              valueTypeArgsNames.stream()
                  .map(
                      argFqn ->
                          getReferenceName(
                              argFqn,
                              finalTargetPackage,
                              finalSimpleNameToFqnForImport)) // Use final versions
                  .collect(Collectors.joining(", "));
          vType += "<" + collectedValueTypeArgs + ">";
        }
        sb.append(", ").append(vType);
      } else if (alias.genericParameters() != null && alias.genericParameters().length > 0) {
        sb.append(", ").append(alias.genericParameters()[alias.genericParameters().length - 1]);
      } else {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                "Cannot determine value type for Kind-based alias "
                    + aliasName
                    + ". Specify 'valueType' or 'genericParameters'.",
                originatingElement);
        return;
      }
    }
    sb.append(">");
    sb.append(" {}\n");

    // Write the file (same as before)
    JavaFileObject jfo =
        processingEnv
            .getFiler()
            .createSourceFile(
                finalTargetPackage + "." + aliasName, originatingElement); // Use finalTargetPackage
    try (PrintWriter out = new PrintWriter(jfo.openWriter())) {
      out.print(sb.toString());
    }
    processingEnv
        .getMessager()
        .printMessage(
            Diagnostic.Kind.NOTE,
            "Generated HKT alias: "
                + finalTargetPackage
                + "."
                + aliasName); // Use finalTargetPackage
  }

  private String getSimpleName(String canonicalName) {
    if (canonicalName == null) return "void"; // Should not happen if checks are in place
    return canonicalName.substring(canonicalName.lastIndexOf('.') + 1);
  }

  private String getQualifiedNameStr(TypeMirror typeMirror) {
    if (typeMirror == null) return null;
    // TypeMirror.toString() often works, but using TypeElement is more robust for qualified names.
    Element element = processingEnv.getTypeUtils().asElement(typeMirror);
    if (element instanceof TypeElement) {
      return ((TypeElement) element).getQualifiedName().toString();
    }
    return typeMirror.toString(); // Fallback
  }

  private String getClassNameFromAnnotation(
      Runnable classAccessTrigger, Element originatingElement, String aliasName, String fieldName) {
    try {
      classAccessTrigger.run(); // Intentionally try to access the class to trigger MTE
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.WARNING,
              "MirroredTypeException not thrown for "
                  + fieldName
                  + " in alias "
                  + aliasName
                  + ". Direct class access occurred, which can be unreliable in annotation"
                  + " processors.",
              originatingElement);
      // This path is problematic, as direct class access is what we want to avoid.
      // If we reach here, it means the class was already compiled and on the processor's classpath.
      // The original code would have proceeded, but it's better to signal this might be an issue.
      // Ideally, even in this case, we'd get a TypeMirror via AnnotationMirror API, but that's a
      // bigger refactor.
      return null; // Indicate that the preferred MTE path was not taken.
    } catch (MirroredTypeException mte) {
      return getQualifiedNameStr(mte.getTypeMirror());
    }
  }

  private List<String> getClassNamesFromAnnotationArray(
      Runnable classArrayAccessTrigger,
      Element originatingElement,
      String aliasName,
      String fieldName) {
    try {
      classArrayAccessTrigger.run();
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.WARNING,
              "MirroredTypesException not thrown for "
                  + fieldName
                  + " in alias "
                  + aliasName
                  + ". Direct class array access occurred.",
              originatingElement);
      return new ArrayList<>(); // Indicate problematic path
    } catch (MirroredTypesException mtes) {
      return mtes.getTypeMirrors().stream()
          .map(this::getQualifiedNameStr)
          .collect(Collectors.toList());
    }
  }

  private String getReferenceName(
      String fqn, String targetPackage, Map<String, String> simpleNameToFqnMapping) {
    if (fqn == null || fqn.equals("void")) {
      return "void"; // Or handle as an error if null is not expected
    }
    String simpleName = getSimpleName(fqn);
    String packageName = "";
    int lastDot = fqn.lastIndexOf('.');
    if (lastDot > 0) {
      packageName = fqn.substring(0, lastDot);
    }

    if (packageName.equals("java.lang") || packageName.equals(targetPackage)) {
      return simpleName; // No import needed, simple name is fine
    }

    // Check if this FQN was the one chosen for import under its simple name
    if (simpleNameToFqnMapping.containsKey(simpleName)
        && simpleNameToFqnMapping.get(simpleName).equals(fqn)) {
      return simpleName;
    }

    // If not imported with simple name (due to conflict or other reasons), use FQN
    return fqn;
  }
}
