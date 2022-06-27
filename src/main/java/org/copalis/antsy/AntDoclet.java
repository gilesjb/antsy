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
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ConstructorDoc;
import com.sun.javadoc.LanguageVersion;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.Tag;
import com.sun.javadoc.Type;

/**
 * Javadoc Doclet that generates facade classes for Ant tasks
 *
 * @author gilesjb
 */
public class AntDoclet {
    
    public static final String 
        OPT_CATALOG_CLASS = "-catalog",
        OPT_DESTINATION_DIR = "-d",
        OPT_PACKAGE = "-outPackage";
    
    public static final String
        ANT = "org.apache.tools.ant";
    
    public static LanguageVersion languageVersion() {
        return LanguageVersion.JAVA_1_5;
    }
    
    public static int optionLength(String name) {
        if (OPT_DESTINATION_DIR.equals(name) || 
                OPT_CATALOG_CLASS.equals(name) ||
                OPT_PACKAGE.equals(name)) return 2;
        return 0;
    }
    
    public static boolean start(RootDoc root) throws FileNotFoundException {
        String base = null, catalog = null, basePackage = null;
        
        for (String[] opt : root.options()) {
//          System.out.println("Option: " + Arrays.asList(opt));
            
            if (opt[0].equals(OPT_DESTINATION_DIR)) {
                base = opt[1];
            } else if (opt[0].equals(OPT_CATALOG_CLASS)) {
                catalog = opt[1];
            } else if (opt[0].equals(OPT_PACKAGE)) {
                basePackage = opt[1];
            }
        }
        
        try {
            new AntDoclet(base, catalog, basePackage).processRoot(root);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private PrintStream out;
    private int indent = 0;
    private final String base, catalog, basePackage;
    
    AntDoclet(String base, String catalog, String basePackage) {
        this.base = base;
        this.catalog = catalog;
        this.basePackage = basePackage;
    }
    
    private TreeSet<ClassDoc> types = new TreeSet<ClassDoc>();
    private TreeSet<ClassDoc> seen = new TreeSet<ClassDoc>();
    
    void queue(ClassDoc elem) {
        String name = elem.qualifiedName();
        if (name.startsWith("org.apache.tools.ant") && !isTaskClass(elem) && !seen.contains(elem)) types.add(elem);
    }
    
    void code(String str) {
        String align = "\t\t\t\t\t\t\t\t".substring(0, indent);
        out.println(align + str.replace("\n", "\n" + align));
    }
    
    void format(String str, Object... args) {
        code(String.format(str, args));
    }
    
    void comment(ProgramElementDoc doc) {
        comment(doc.commentText() + "\n\n@see " + doc.qualifiedName());
    }

    void comment(MethodDoc doc) {
        comment(doc.commentText() + "\n\n@see " + doc.containingClass().qualifiedName() +
                '#' + doc.name() + doc.signature());
    }

    void comment(String str) {
        if (str.length() == 0) return;
        code("/**");
        for (String line : str.split("[\r\n]+")) {
            code(" * " + line);
        }
        code(" */");
    }
    
    void processRoot(RootDoc root) throws FileNotFoundException {
        ClassDoc[] classes = root.classes();
        
        String packag = catalog.substring(0, catalog.lastIndexOf('.'));
        String cat = catalog.substring(catalog.lastIndexOf('.') + 1);
        String dir = base + '/' + packag.replace('.', '/');
        new File(dir).mkdirs();
        out = new PrintStream(dir + '/' + cat + ".java");
        try {
            code("package " + packag + ';');
            code("");
            comment("Class constants for Ant Task facades");
            code("public interface " + cat + " {");
            for (ClassDoc task : classes) {
                processTask(task);
            }
            code("}");
        } finally {
            out.close();
        }
        
        while (!types.isEmpty()) {
            ClassDoc elem = types.first();
            types.remove(elem);
            seen.add(elem);
            
            processType(elem);
        }
    }
    
    void processTask(ClassDoc task) {
        if (task.isAbstract() || !isTaskClass(task) || !isConstructable(task) || task.containingClass() != null) return;
        if (!task.qualifiedName().startsWith(ANT)) return;
        if (deprecated(task)) return;
        
        String name = refName(task);
        indent++;
        comment(task);
        format("static Class<%s> %s = %s.class;", name, task.simpleTypeName().toLowerCase(), name);
        indent--;
        PrintStream tmp = out;
        try {
            processTaskType(task);
        } finally {
            out = tmp;
        }
    }
    
    void processTaskType(ClassDoc type) {
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
                comment(type);
                format("public class %s extends %s<%s> {",
                        name, AntTask.class.getName(), type.qualifiedName());
                indent++;
                format("public %s(String name, org.apache.tools.ant.Project project) {super(name, %s.class, project);}",
                        name, type.qualifiedName());
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
    
    void processType(ClassDoc type) {
        boolean constructable = false;
        if (type.containingClass() == null || type.isStatic()) {
            for (ConstructorDoc constr : type.constructors()) {
                if (constr.parameters().length == 0 && constr.isPublic()) {
                    constructable = true;
                    break;
                }
            }
        }
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
                comment(type);
                format("public class %s<P> extends %s<%s, P> {",
                    name, AntElement.class.getName(), type.qualifiedName());
                indent++;
                if (constructable) {
                    format("public static %s<Void> create() {return new %1$s<Void>(new %s(), null);}",
                            name, type.qualifiedName());
                }
                format("public %s(%s element, P parent) {super(element, parent);}",
                        name, type.qualifiedName());
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
    
    void processMethods(String typeName, ClassDoc task) {
        processMethods(typeName, task, new TreeSet<MethodDoc>());
    }
        
    void processMethods(String typeName, ClassDoc task, Set<MethodDoc> map) {
        for (MethodDoc method : task.methods(false)) {
            if (!map.contains(method) && !deprecated(method)) {
                processMethod(typeName, task, method);
                map.add(method);
            }
        }
        if (task.superclassType() != null) { 
            processMethods(typeName, task.superclassType().asClassDoc(), map);
        }
    }
    
    void processMethod(String typeName, ClassDoc task, MethodDoc method) {
        if (method.isAbstract() || !method.isPublic() || method.isStatic()) return;
        for (Processor proc : Processor.values()) {
            if (proc.processMethod(this, typeName, method)) return;
        }
        code("// " + method + " - " + method.returnType());
    }
        
    String mkrName(String name) {
        return name.replace(ANT, basePackage);
    }
    
    String refName(ClassDoc type) {
        if (type.containingClass() != null) {
            return refName(type.containingClass()) + '_' + type.name().substring(type.name().lastIndexOf('.') + 1);
        } else {
            String pkg = mkrName(type.containingPackage().name());
            return pkg + '.' + type.name();
        }
    }

    static boolean isAntType(Type type) {
        return type.asClassDoc() != null && type.asClassDoc().qualifiedName().startsWith("org.apache.tools.ant.types");
    }

    static boolean isAnt(Type type) {
        return type.asClassDoc() != null && type.asClassDoc().qualifiedName().startsWith("org.apache.tools.ant");
    }

    static boolean isTaskClass(ClassDoc doc) {
        return doc != null && 
            (doc.qualifiedName().equals("org.apache.tools.ant.Task") || 
                    doc.superclassType() != null && isTaskClass(doc.superclassType().asClassDoc()));
    }

    static boolean isConstructable(ClassDoc type) {
        if (type.containingClass() != null && !type.isStatic() || type.isAbstract()) return false;
        for (ConstructorDoc cd : type.constructors()) {
            Parameter[] params = cd.parameters();
            if (cd.isPublic() && params.length == 0) return true;
        }
        return false;
    }
    
    static String tries(MethodDoc method) {
        return method.thrownExceptionTypes().length > 0? "try {" : "";
    }
    
    static String except(MethodDoc method) {
        return method.thrownExceptionTypes().length > 0? "} catch (Exception e) {throw new RuntimeException(e);}" : "";
    }
    
    private static final Set<String> reservedIdentifiers = new HashSet<String>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "false", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "null",
            "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch",
            "synchronized", "this", "throw", "throws", "transient", "true", "try", "void", "volatile", "while"));
    
    static String matches(MethodDoc method, String verb, String... prefixes) {
        String name = method.name();
        for (String prefix : prefixes) {
            if (name.startsWith(prefix) && name.length() >= prefix.length()) {
                name = name.substring(prefix.length());
                if (name.length() > 0) {
                    name = verb + name;
                    name = name.substring(0, 1).toLowerCase() + name.substring(1);
                }
                if (name.length() == 0 || reservedIdentifiers.contains(name)) {
                    return method.name();
                }
                return name;
            }
        }
        return null;
    }
    
    static boolean deprecated(ProgramElementDoc doc) {
        for (AnnotationDesc annot : doc.annotations()) {
            if (annot.annotationType().typeName().equals("java.lang.Deprecated")) return true;
        }
        for (Tag tag : doc.tags()) {
            if (tag.name().equals("@deprecated")) return true;
        }
        return false;
    }
    
    enum Processor {
        IGNORE {
            public boolean processMethod(AntDoclet doc, String container, MethodDoc method) {
                String returns = method.returnType().qualifiedTypeName();
                return method.parameters().length == 0 && (returns.equals("void") || !returns.startsWith("org.apache.tools.ant")) ||
                        method.toString().endsWith("(org.apache.tools.ant.Location)") ||
                        method.toString().endsWith("(org.apache.tools.ant.Project)") ||
                        method.toString().endsWith("bindToOwner(org.apache.tools.ant.Task)") ||
                        matches(method, "get", "is", "execute", "handle", "log") != null;
            }
        },
        CREATE {
            public boolean processMethod(AntDoclet doc, String container, MethodDoc method) {
                String match = matches(method, "begin", "create");
                ClassDoc returns = method.returnType().asClassDoc();
                if (match != null && method.parameters().length == 0 && isAnt(returns) && !isTaskClass(returns)) {
                    doc.queue(returns);
                    doc.comment(method);
                    doc.format("public %s<%s> %s() //CREATE\n\t{return new %1$s<%2$s>(is().%s(), this);}",
                            doc.refName(returns), container, match, method.name());
                    return true;
                }
                return false;
            }
        },
        ADD_CONFIGURED {
            public boolean processMethod(AntDoclet doc, String container, MethodDoc method) {
                String match = matches(method, "", "addConfigured");
                if (match != null && method.parameters().length == 1) {
                    ClassDoc type = method.parameters()[0].type().asClassDoc();
                    if (!isTaskClass(type) && isConstructable(type)) {
                        int lt = container.indexOf('<');
                        String plain = lt >= 0? container.substring(0, lt) : container;
                        doc.queue(type);
                        doc.comment(method);
                        doc.format("public %s<%s> %s() //ADD_CONFIGURED\n\t" + 
                                    "{return new %1$s<%2$s>(new %s(), this) {\n\t\t" + 
                                        "public %2$s end() {%s%s.this.is().%3$s(is()); return super.end();%s}};}",
                                doc.refName(type), container, method.name(), type.qualifiedName(), tries(method), plain, except(method));
                        return true;
                    }
                }
                return false;
            }
        },
        ADD_NON_ANT {
            public boolean processMethod(AntDoclet doc, String container, MethodDoc method) {
                String match = matches(method, "", "add", "append");
                if (match != null && method.returnType().typeName().equals("void") && method.parameters().length == 1) {
                    Parameter param = method.parameters()[0]; // should be 1 param
                    if (isAntType(param.type())) return false;
                    doc.comment(method);
                    doc.format("public %s %s(%s %s) //ADD_NON_ANT\n\t{%sis().%s(%4$s); return this;%s}", 
                            container, method.name(), param.type(), param.name(), tries(method), method.name(), except(method));
                }
                return false;
            }
        },
        ADD_ANT {
            public boolean processMethod(AntDoclet doc, String container, MethodDoc method) {
                String match = matches(method, "", "add", "append");
                if (match != null && method.returnType().typeName().equals("void") && method.parameters().length == 1) {
                    Parameter param = method.parameters()[0]; // should be 1 param
                    if (!isAntType(param.type())) return false;
                    doc.queue(param.type().asClassDoc());
                    doc.comment(method);
                    doc.format("public %s %s(%s<?> %s) //ADD_ANT\n\t{%sis().%s(%4$s.is()); return this;%s}", 
                            container, method.name(), doc.refName(param.type().asClassDoc()),
                            param.name(), tries(method), method.name(), except(method));
                }
                return false;
            }
        },
        ADD_NEW {
            public boolean processMethod(AntDoclet doc, String container, MethodDoc method) {
                String match = matches(method, "begin", "add");
                if (match != null && match.length() > 0 && method.parameters().length == 1) {
                    ClassDoc type = method.parameters()[0].type().asClassDoc();
                    if (!isTaskClass(type) && isAntType(type) && isConstructable(type)) {
                        doc.queue(type);
                        doc.comment(method);
                        doc.format("public %s<%s> %s() //ADD_NEW\n\t" +
                                "{%s _obj_ = new %4$s(); %sis().%s(_obj_);%s return new %1$s<%2$s>(_obj_, this);}",
                                doc.refName(type), container, match, type.qualifiedName(), tries(method), method.name(), except(method));
                        return true;
                    }
                }
                return false;
            }
        },
        SET {
            public boolean processMethod(AntDoclet doc, String container, MethodDoc method) {
                String match = matches(method, "", "set");
                if (match != null && method.returnType().typeName().equals("void") && method.parameters().length == 1) {
                    Parameter param = method.parameters()[0]; // should be 1 param
                    doc.comment(method);
                    doc.format("public %s %s(%s %s) //SET\n\t{%sis().%s(%4$s); return this;%s}", 
                            container, match, param.type(), param.name(), tries(method), method.name(), except(method));
                    return true;
                }
                return false;
            }
        }
        ;
        public abstract boolean processMethod(AntDoclet doc, String container, MethodDoc method);
    }

}
