/*
 *  Copyright 2009 Giles Burgess
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.copalis.antsy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.sun.source.util.DocTrees;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;


/**
 * Javadoc Doclet that generates facade classes for Ant tasks
 *
 * @author gilesjb
 */
public class AntDoclet implements Doclet {

    private static final String ORG_APACHE_TOOLS_ANT = "org.apache.tools.ant";
    private static final String ORG_APACHE_TOOLS_ANT_TASK = ORG_APACHE_TOOLS_ANT + ".Task";

    public static final String
        OPT_CATALOG_CLASS = "-catalog",
        OPT_DESTINATION_DIR = "-d",
        OPT_PACKAGE = "-outPackage";

    public static int optionLength(String name) {
        if (OPT_DESTINATION_DIR.equals(name) ||
                OPT_CATALOG_CLASS.equals(name) ||
                OPT_PACKAGE.equals(name)) return 2;
        return 0;
    }

    private PrintStream out;
    private int indent = 0;
    private String base, catalog, basePackage;

    private DocTrees docTrees;

    private LinkedList<TypeElement> types = new LinkedList<TypeElement>();
    private HashSet<TypeElement> seen = new HashSet<TypeElement>();

    void queueType(TypeElement elem) {
        String name = elem.getQualifiedName().toString();
        if (name.startsWith(ORG_APACHE_TOOLS_ANT) && !isAntTask(elem) && !seen.contains(elem)) {
            types.add(elem);
            seen.add(elem);
        }
    }

    void code(String str) {
        String align = "    ".repeat(indent);
        out.println(align + str.replace("\n", "\n" + align));
    }

    void format(String str, Object... args) {
        code(String.format(str, args));
    }

    void document(Element doc) {
        javaDoc(docTrees.getDocCommentTree(doc));
    }

    void document(ExecutableElement doc) {
        javaDoc(docTrees.getDocCommentTree(doc));
    }

    void javaDoc(Object obj) {
        if (Objects.isNull(obj)) return;
        String str = obj.toString();
        if (str.length() == 0) return;
        code("/**");
        for (String line : str.split("[\r\n]+")) {
            code(" * " + line);
        }
        code(" */");
    }

    void processRoot(DocletEnvironment root) throws FileNotFoundException {
        Set<TypeElement> elements = root.getIncludedElements().stream()
                .filter(el -> el.getKind().isClass())
                .map(el -> (TypeElement) el)
                .collect(Collectors.toCollection(() ->
                        new TreeSet<>(Comparator.comparing(t -> t.getSimpleName().toString()))));

        docTrees = root.getDocTrees();

        String packag = catalog.substring(0, catalog.lastIndexOf('.'));
        String cat = catalog.substring(catalog.lastIndexOf('.') + 1);
        String dir = base + '/' + packag.replace('.', '/');
        new File(dir).mkdirs();
        out = new PrintStream(dir + '/' + cat + ".java");
        try {
            code("package " + packag + ';');
            code("");
            javaDoc("Class constants for Ant Task facades");
            code("public interface " + cat + " {");
            for (TypeElement el : elements) {
                processTask(el);
            }
            code("}");
        } finally {
            out.close();
        }

        while (!types.isEmpty()) {
            processType(types.removeFirst());
        }
    }

    void processTask(TypeElement task) {

        if (task.getModifiers().contains(Modifier.ABSTRACT) || !isAntTask(task) || !isConstructable(task)
                || task.getEnclosingElement().getKind() != ElementKind.PACKAGE)
            return;
        if (!task.getQualifiedName().toString().startsWith(ORG_APACHE_TOOLS_ANT)) return;
        if (deprecated(task)) return;

        String name = refName(task);
        indent++;
        document(task);
        format("static Class<%s> %s = %s.class;", name, task.getSimpleName().toString().toLowerCase(), name);
        indent--;
        PrintStream tmp = out;
        try {
            processTaskType(task);
        } finally {
            out = tmp;
        }
    }

    void processTaskType(TypeElement type) {
        String full = refName(type);
        String packg = full.substring(0, full.lastIndexOf('.'));
        String name = full.substring(full.lastIndexOf('.') + 1);

        try {
            String dir = base + '/' + packg.replace('.', '/');
            new File(dir).mkdirs();
            out = new PrintStream(dir + '/' + name + ".java");
            try {
                code("package " + packg + ';');

                code("");
                document(type);
                format("public class %s extends %s<%s> {",
                        name, AntTask.class.getName(), type.getQualifiedName());
                indent++;
                format("public %s(String name, org.apache.tools.ant.Project project) {super(name, %s.class, project);}",
                        name, type.getQualifiedName());
                processMethods(full, type);
            } finally {
                indent--;
                code("}");
                out.close();
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    void processType(TypeElement type) {
        boolean constructable = isConstructable(type);
        String full = refName(type);
        String packg = full.substring(0, full.lastIndexOf('.'));
        String name = full.substring(full.lastIndexOf('.') + 1);

        try {
            String dir = base + '/' + packg.replace('.', '/');
            new File(dir).mkdirs();
            out = new PrintStream(dir + '/' + name + ".java");
            try {
                code("package " + packg + ';');

                code("");
                document(type);
                format("public class %s<P> extends %s<%s, P> {",
                    name, AntElement.class.getName(), type.getQualifiedName());
                indent++;
                if (constructable) {
                    format("public static %s<Void> create() {return new %1$s<Void>(new %s(), null);}",
                            name, type.getQualifiedName());
                }
                format("public %s(%s element, P parent) {super(element, parent);}",
                        name, type.getQualifiedName());
                processMethods(full + "<P>", type);
            } finally {
                indent--;
                code("}");
                out.close();
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    void processMethods(String typeName, TypeElement task) {
        processMethods(typeName, task, new HashSet<>());
    }

    void processMethods(String typeName, TypeElement task, Set<String> map) {
        for (Element el : task.getEnclosedElements()) {
            if (!map.contains(el.toString()) && el.getKind() == ElementKind.METHOD && !deprecated(el)) {
                processMethod(typeName, task, (ExecutableElement) el);
                map.add(el.toString());
            }
        }

        if (task.getSuperclass() instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
            processMethods(typeName, te, map);
        }
    }

    void processMethod(String typeName, TypeElement task, ExecutableElement method) {
        for (Processor proc : Processor.values()) {
            if (proc.processMethod(this, typeName, method)) return;
        }
        code("// unhandled: " + method + " - " + method.getReturnType());
    }

    String refName(TypeElement type) {
        Element enc = type.getEnclosingElement();
        if (enc.getKind() == ElementKind.CLASS && enc instanceof TypeElement te) {
            String name = type.getQualifiedName().toString();
            return refName(te) + '_' + name.substring(name.lastIndexOf('.') + 1);
        } else {
            String name = ((PackageElement) enc).getQualifiedName().toString() + '.' + type.getSimpleName();
            if (ORG_APACHE_TOOLS_ANT_TASK.equals(name)) {
                return AntTask.class.getName() + "<" + name + ">";
            }
            return name.replace(ORG_APACHE_TOOLS_ANT, basePackage);
        }
    }

    static String qualifiedName(TypeMirror tm) {
        TypeKind kind = tm.getKind();
        if (kind.isPrimitive()) {
            return kind.name().toLowerCase();
        } else if (kind == TypeKind.ARRAY) {
            return (tm instanceof javax.lang.model.type.ArrayType at ? qualifiedName(at.getComponentType()) : "") + "...";
        } else {
            return tm instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te
                    ? te.getQualifiedName().toString() : null;
        }
    }

    static TypeElement asTypeElement(TypeMirror tm) {
        return tm instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te ? te : null;
    }

    static boolean isAntType(TypeMirror type) {
        return Optional.ofNullable(asTypeElement(type)).map(AntDoclet::isAntType).orElse(false);
    }

    static boolean isAntType(TypeElement elem) {
        return Objects.nonNull(elem) && elem.getQualifiedName().toString().startsWith(ORG_APACHE_TOOLS_ANT);
    }

    static boolean isAntTask(TypeElement doc) {
        return doc != null && (doc.getQualifiedName().toString().equals(ORG_APACHE_TOOLS_ANT_TASK)
                || doc.getSuperclass() instanceof DeclaredType dt
                        && dt.asElement() instanceof TypeElement te
                        && isAntTask(te));
    }

    static boolean isConstructable(TypeElement type) {
        Set<Modifier> modifiers = type.getModifiers();
        if (modifiers.contains(Modifier.ABSTRACT)) return false;
        if (type.getEnclosingElement().getKind() == ElementKind.CLASS && !modifiers.contains(Modifier.STATIC))
            return false;

        int constructors = 0;
        for (Element el : type.getEnclosedElements()) {
            if (el.getKind() == ElementKind.CONSTRUCTOR) {
                constructors++;
                if (el instanceof ExecutableElement ex && ex.getModifiers().contains(Modifier.PUBLIC)
                        && ex.getParameters().isEmpty())
                    return true;
            }
        }
        return constructors == 0;
    }

    static String tries(ExecutableElement method) {
        return method.getThrownTypes().isEmpty() ? "" : "try {";
    }

    static String except(ExecutableElement method) {
        return method.getThrownTypes().isEmpty() ? "" : "} catch (Exception e) {throw new RuntimeException(e);}";
    }

    private static final Set<String> reservedIdentifiers = new HashSet<String>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "false", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "null",
            "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch",
            "synchronized", "this", "throw", "throws", "transient", "true", "try", "void", "volatile", "while"));

    static String matches(ExecutableElement method, String verb, String... prefixes) {
        String name = method.getSimpleName().toString();
        for (String prefix : prefixes) {
            if (name.startsWith(prefix) && name.length() >= prefix.length()) {
                name = name.substring(prefix.length());
                if (name.length() > 0) {
                    name = verb + name;
                    name = name.substring(0, 1).toLowerCase() + name.substring(1);
                }
                if (name.length() == 0 || reservedIdentifiers.contains(name)) {
                    return method.getSimpleName().toString();
                }
                return name;
            }
        }
        return null;
    }

    static boolean deprecated(Element doc) {
        return Objects.nonNull(doc.getAnnotation(java.lang.Deprecated.class));
    }

    enum Processor {
        IGNORE {
            public boolean processMethod(AntDoclet doc, String container, ExecutableElement method) {
                TypeMirror returnType = method.getReturnType();
                Set<Modifier> modifiers = method.getModifiers();

                boolean skip = !modifiers.contains(Modifier.PUBLIC)
                        || modifiers.contains(Modifier.ABSTRACT)
                        || modifiers.contains(Modifier.STATIC)
                        || method.getParameters().isEmpty() && returnType.getKind() == TypeKind.VOID
                        || !isAntType(returnType) && returnType.getKind() != TypeKind.VOID
                        || method.toString().endsWith("(org.apache.tools.ant.Location)")
                        || method.toString().endsWith("(org.apache.tools.ant.Project)")
                        || method.toString().endsWith("bindToOwner(org.apache.tools.ant.Task)")
                        || matches(method, "get", "is", "execute", "handle", "log") != null;
                if (skip) {
                    doc.code("//IGNORE: " + method + " - " + method.getReturnType());
                }
                return skip;
            }
        },
        CREATE {
            public boolean processMethod(AntDoclet doc, String container, ExecutableElement method) {
                String match = matches(method, "with", "create");
                TypeElement returns = asTypeElement(method.getReturnType());

                if (match != null && method.getParameters().isEmpty() && isAntType(returns) && !isAntTask(returns)) {
                    doc.queueType(returns);
                    doc.document(method);
                    doc.format("public %s<%s> %s() //CREATE\n\t{return new %1$s<%2$s>(is().%s(), this);}",
                            doc.refName(returns), container, match, method.getSimpleName());
                    return true;
                }
                return false;
            }
        },
        ADD_CONFIGURED {
            public boolean processMethod(AntDoclet doc, String container, ExecutableElement method) {
                String match = matches(method, "", "addConfigured");
                List<? extends VariableElement> parameters = method.getParameters();

                if (match != null && parameters.size() == 1) {
                    TypeMirror tm0 = parameters.get(0).asType();
                    TypeElement type0 = asTypeElement(tm0);

                    if (!isAntTask(type0) && isConstructable(type0)) {
                        int lt = container.indexOf('<');
                        String plain = lt >= 0? container.substring(0, lt) : container;
                        doc.queueType(type0);
                        doc.document(method);
                        doc.format("public %s<%s> %s() //ADD_CONFIGURED\n\t" +
                                    "{return new %1$s<%2$s>(new %s(), this) {\n\t\t" +
                                        "public %2$s end() {%s%s.this.is().%3$s(is()); return super.end();%s}};}",
                                doc.refName(type0), container, method.getSimpleName(), qualifiedName(tm0),
                                tries(method), plain, except(method));
                        return true;
                    }
                }
                return false;
            }
        },
        ADD_NON_ANT {
            public boolean processMethod(AntDoclet doc, String container, ExecutableElement method) {
                String match = matches(method, "", "add", "append");
                List<? extends VariableElement> parameters = method.getParameters();
                TypeKind returnType = method.getReturnType().getKind();

                if (match != null && returnType == TypeKind.VOID && parameters.size() == 1) {
                    VariableElement param0 = parameters.get(0);
                    TypeElement type0 = asTypeElement(param0.asType());
                    if (isAntType(type0)) return false;

                    doc.document(method);
                    doc.format("public %s %s(%s %s) //ADD_NON_ANT\n\t{%sis().%s(%4$s); return this;%s}",
                            container, method.getSimpleName(), qualifiedName(param0.asType()), param0.getSimpleName(),
                            tries(method), method.getSimpleName(), except(method));
                    return true;
                }
                return false;
            }
        },
        ADD_NEW {
            public boolean processMethod(AntDoclet doc, String container, ExecutableElement method) {
                String match = matches(method, "with", "add");
                List<? extends VariableElement> parameters = method.getParameters();
                if (match != null && match.length() > 0 && parameters.size() == 1) {
                    TypeMirror tm0 = parameters.get(0).asType();
                    TypeElement type0 = asTypeElement(tm0);

                    if (!isAntTask(type0) && isAntType(type0) && isConstructable(type0)) {
                        doc.queueType(type0);
                        doc.document(method);
                        doc.format("public %s<%s> %s() //ADD_NEW\n\t" +
                                "{%s _obj_ = new %4$s(); %sis().%s(_obj_);%s return new %1$s<%2$s>(_obj_, this);}",
                                doc.refName(type0), container, match, qualifiedName(tm0),
                                tries(method), method.getSimpleName(), except(method));
                        return true;
                    }
                }
                return false;
            }
        },
        ADD_ANT {
            public boolean processMethod(AntDoclet doc, String container, ExecutableElement method) {
                String match = matches(method, "", "add", "append");
                List<? extends VariableElement> parameters = method.getParameters();
                TypeKind returnType = method.getReturnType().getKind();
                if (Objects.isNull(match) || returnType != TypeKind.VOID || parameters.size() != 1) return false;

                VariableElement param0 = parameters.get(0);
                TypeElement type0 = asTypeElement(param0.asType());
                if (!isAntType(type0) || !isConstructable(type0)) return false;

                doc.queueType(type0);
                doc.document(method);
                doc.format("public %s %s(%s%s %s) //ADD_ANT\n\t{%sis().%s(%5$s.is()); return this;%s}",
                        container, method.getSimpleName(), doc.refName(type0), isAntTask(type0) ? "" : "<?>",
                                param0.getSimpleName(), tries(method), method.getSimpleName(), except(method));
                return true;
            }
        },
        SET {
            public boolean processMethod(AntDoclet doc, String container, ExecutableElement method) {
                String match = matches(method, "", "set");
                List<? extends VariableElement> parameters = method.getParameters();
                TypeKind returnType = method.getReturnType().getKind();

                if (match != null && returnType == TypeKind.VOID && parameters.size() == 1) {
                    VariableElement param0 = parameters.get(0);
                    doc.document(method);
                    doc.format("public %s %s(%s %s) //SET\n\t{%sis().%s(%4$s); return this;%s}",
                            container, match, qualifiedName(param0.asType()),
                            param0.getSimpleName(), tries(method), method.getSimpleName(), except(method));
                    return true;
                }
                return false;
            }
        }
        ;
        public abstract boolean processMethod(AntDoclet doc, String container, ExecutableElement method);
    }

    @Override public void init(Locale locale, Reporter reporter) {
    }

    @Override public String getName() {
        return "Antsy";
    }

    private Option option(String name, String params, Consumer<String> fn) {
        return new Option() {
            public int getArgumentCount() { return 1; }
            public String getDescription() { return ""; }
            public Kind getKind() { return Kind.STANDARD; }
            public String getParameters() { return params; }
            public List<String> getNames() {
                return Arrays.asList(name);
            }
            public boolean process(String option, List<String> arguments) {
                fn.accept(arguments.get(0));
                return true;
            }
        };
    }

    @Override public Set<? extends Option> getSupportedOptions() {
        return new HashSet<>(Arrays.asList(
                option(OPT_CATALOG_CLASS, "class", x -> { AntDoclet.this.catalog = x; }),
                option(OPT_DESTINATION_DIR, "destination", x -> { AntDoclet.this.base = x; }),
                option(OPT_PACKAGE, "package", x -> { AntDoclet.this.basePackage = x; })
            ));
    }

    @Override public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_17;
    }

    @Override public boolean run(DocletEnvironment environment) {
        try {
            processRoot(environment);
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }
}
