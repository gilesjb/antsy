## Antsy

If you want to invoke javac from Java, or even just copy a file,
it turns out that the JDK is missing a lot of functionality that you need for common system programming tasks.

Apache Ant has a very useful set of tasks, but they are designed be invoked from `build.xml` rather than called programmatically,
and their API is clumsy.

ANTSY is a set of automatically generated fluent interfaces to Ant tasks.
Now you can easily call Ant tasks from Java.

### Echo task

```java
public class HelloWorld {
    public static void main(String... args) {
        new AntProject().task(Tasks.echo).message("Hello, World!").run();
    }
}
```

displays:

```
[echo] Hello, World!
```

### Javadoc task

```java
public class Javadoc implements Tasks {
    
    static File
        src = new File("src/samples/java"),
        docs = new File("target/samples/docs");

    public static void main(String... args) {
        AntProject ant = new AntProject();
        
        ant.task(mkdir).dir(docs).run();
        ant.task(javadoc).destdir(docs).verbose(false)
            .addFileset().dir(src).end()
            .run();
    }
}
```

which is equivalent to

```xml
<project basedir="." default="build" name="ant">
    <property name="src" value="src/samples/java"/>
    <property name="docs" value="target/samples/docs"/>

    <target name="build">
        <mkdir dir="${docs}"/>
        <javadoc destdir="${docs}" verbose="false">
            <fileset dir="${src}/>
        </javadoc>
    </target>
</project>
```