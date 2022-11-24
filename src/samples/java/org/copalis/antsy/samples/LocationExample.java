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
package org.copalis.antsy.samples;

import org.copalis.antsy.AntProject;
import org.copalis.antsy.Tasks;

/**
 * @author gilesjb
 *
 */
public class LocationExample implements Tasks {

    public static void main(String... args) {
        AntProject ant = new AntProject().startBuild();

        try {
            ant.task(javac)
                .inferLocation(true)
                .run();
        } catch (RuntimeException e) {
            ant.buildFinished(e);
        }

        try {
            ant.task(javac).run();
        } catch (RuntimeException e) {
            ant.buildFinished(e);
        }

        ant.buildFinished();
    }
}
