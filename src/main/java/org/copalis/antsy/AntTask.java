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

import org.apache.tools.ant.Location;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

/**
 * Wrapper class for Ant tasks.
 * The task is not executed until the {@link AntTask#ok} method is called
 */
public abstract class AntTask<T extends Task> extends AntRef<T> {
    
    private boolean inferLocation = false;
    
    /**
     * Creates an instance of an AntTask class
     * @param <X> the AntTask type
     * @param <Y> the enclosed Ant Task type
     * @param type the type of AntTask to create
     * @param project the Ant project that it should be executed in
     * @return a new instance of X
     */
    public static final <X extends AntTask<Y>, Y extends Task> X create(Class<X> type, Project project) {
        try {
            return type.getConstructor(String.class, Project.class)
                    .newInstance(type.getSimpleName().toLowerCase(), project);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected AntTask(String name, T task, Project project) {
        super(task);
        task.setTaskName(name);
        task.setProject(project);
    }
    
    protected AntTask(String name, Class<T> type, Project project) {
        this(name, createInner(type), project);
    }
    
    private static final <X extends Task> X createInner(Class<X> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Specifies whether automatic location inference should be used.
     * @param value {@literal true} if inference should be used
     */
    public void inferLocation(boolean value) {
        inferLocation = value;
    }
    
    /**
     * Sets the location of the task that will be reported
     * @param fileName
     * @param lineNumber
     * @param columnNumber
     * @see org.apache.tools.ant.ProjectComponent#setLocation(Location)
     */
    public void setLocation(String fileName, int lineNumber, int columnNumber) {
        is().setLocation(new Location(fileName, lineNumber, columnNumber));
    }

    /**
     * Executes the task by calling {@link org.apache.tools.ant.Task#perform() perform()}
     * on the underlying Ant Task.
     * Should only be called once on any specific task object.
     * <p/>
     * If location inference is enabled and no location has been set,
     * the stack trace is examined and the invoking method's source file and line number is used as the task location
     * 
     * @see org.apache.tools.ant.Task#perform()
     */
    public final void run() {
        if (inferLocation && is().getLocation() == Location.UNKNOWN_LOCATION) {
            StackTraceElement elem = new Throwable().getStackTrace()[1]; // invoking method
            setLocation(elem.getFileName(), elem.getLineNumber(), 0);
        }
        is().perform();
    }
}
