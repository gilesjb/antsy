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
import java.io.PrintStream;
import java.util.function.Consumer;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.BuildLogger;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.DemuxInputStream;
import org.apache.tools.ant.DemuxOutputStream;
import org.apache.tools.ant.Location;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.Task;

/**
 * A facade around an Ant Project and BuildLogger
 *
 * @author gilesjb
 */
public class AntProject {

    private final Project project;
    private final BuildLogger logger;

    public AntProject() {
        this(new Project(), new DefaultLogger());
    }

    public AntProject(Project project, BuildLogger logger) {
        this.project = project;
        this.logger = logger;

        logger.setOutputPrintStream(System.out);
        logger.setErrorPrintStream(System.err);
        logger.setMessageOutputLevel(Project.MSG_INFO);

        project.setBaseDir(new File("."));
        project.addBuildListener(logger);
    }

    public AntProject setStreams() {
        System.setIn(new DemuxInputStream(project));
        System.setOut(new PrintStream(new DemuxOutputStream(project, false)));
        System.setErr(new PrintStream(new DemuxOutputStream(project, true)));
        return this;
    }

    /**
     * Gets the underlying Ant Project
     * @return a {@link org.apache.tools.ant.Project Project}
     */
    public Project project() {
        return project;
    }

    /**
     * Starts a new target, returning an object
     * @param name the target name
     * @return a new {@link org.apache.tools.ant.Target Target} object
     */
    public AntTarget startTarget(String name) {
        final Target current = new Target();
        current.setProject(project);
        current.setName(name);
        logger.targetStarted(new BuildEvent(current));
        return new AntTarget(current) {
            public void finished() {
                logger.targetFinished(new BuildEvent(current));
            }
        };
    }

    /**
     * Signals that the build has started
     */
    public AntProject startBuild() {
        project.fireBuildStarted();
        return this;
    }

    /**
     * Signals that the build has completed successfully
     */
    public void buildFinished() {
        project.fireBuildFinished(null);
    }

    /**
     * Signals that the build has failed
     * @param message a description of the failure
     * @param cause the exception that caused the build failure
     */
    public void buildFinished(String message, Throwable cause) {
        project.fireBuildFinished(new BuildException(message, cause));
    }

    /**
     * Signals that the build has failed
     * @param cause the exception that caused the build failure
     */
    public void buildFinished(Throwable cause) {
        project.fireBuildFinished(new BuildException(cause));
    }

    public <X extends Task> X task(Class<X> type) {
        try {
            X task = type.getConstructor().newInstance();
            task.setTaskName(type.getSimpleName().toLowerCase());
            task.setProject(project);
            if (task.getLocation() == Location.UNKNOWN_LOCATION) {
                StackTraceElement elem = new Throwable().getStackTrace()[1]; // invoking method
                task.setLocation(new Location(elem.getFileName(), elem.getLineNumber(), 0));
            }
            return task;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <X extends Task> X task(Class<X> type, Consumer<X> fn) {
        try {
            X task = task(type);
            fn.accept(task);
            return task;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <X extends Task> void run(Class<X> type, Consumer<X> fn) {
        task(type, fn).execute();
    }
}
