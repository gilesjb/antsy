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

import org.apache.tools.ant.ProjectComponent;
import org.apache.tools.ant.Task;

/**
 * A wrapper around an Ant object that has a parent
 * @author gilesjb
 *
 * @param <T> the type of the wrapped object
 * @param <P> the type of the parent object
 */
public abstract class AntElement<T, P> extends AntRef<T> {
    private P parent;
    
    @SuppressWarnings("unchecked")
    protected AntElement(T element, P parent) {
        super(element);
        if (element instanceof ProjectComponent) {
            ProjectComponent comp = (ProjectComponent) element;
            if (parent instanceof AntTask) {
                comp.setProject(((AntTask<Task>) parent).is().getProject());
            } else if (parent instanceof ProjectComponent) {
                comp.setProject(((ProjectComponent) parent).getProject());
            }
        }
        this.parent = parent;
    }
    
    /**
     * Ends this element and returns the parent.
     * The method should only be called once on any element.
     * 
     * @return the enclosing parent object
     */
    public P end() {
        try {
            return parent;
        } finally {
            parent = null;
        }
    }
}
