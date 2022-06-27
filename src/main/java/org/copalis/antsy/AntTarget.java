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

import org.apache.tools.ant.Target;

/**
 * A wrapper around Ant's {@link Target} class.
 * Usually, the only public method invoked on this will be {@link AntTarget#finished()}
 *
 * @author gilesjb
 */
public abstract class AntTarget extends AntRef<Target> {

    public AntTarget(Target ref) {
        super(ref);
    }

    /**
     * Signals that execution of the target has finished
     */
    public abstract void finished();
}
