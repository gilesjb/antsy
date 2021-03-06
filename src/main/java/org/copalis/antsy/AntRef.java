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

/**
 * A wrapper around an Ant object
 *
 * @author gilesjb
 * @param <T> The Ant object class
 */
public abstract class AntRef<T> {

    /**
     * The wrapped object
     */
    private final T ref;
    
    protected AntRef(T ref) {
        this.ref = ref;
    }
    
    /**
     * Gets the wrapped object
     * @return contained Ant object
     */
    public final T is() {
        return ref;
    }
}
