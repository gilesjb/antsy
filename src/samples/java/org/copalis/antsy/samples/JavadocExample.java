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

import java.io.File;

import org.copalis.antsy.AntProject;
import org.copalis.antsy.AntTarget;
import org.copalis.antsy.Tasks;

public class JavadocExample implements Tasks {

    static File
        src = new File("src/samples/java"),
        docs = new File("target/samples/docs");

    public static void main(String... args) {
        AntProject ant = new AntProject();
        AntTarget target = ant.startTarget("javadoc");

        ant.startBuild();
        ant.run(mkdir, t -> t.setDir(docs));
        ant.run(javadoc, t -> {
           t.setDestdir(docs);
           t.setVerbose(false);
        });
//            .destdir(docs).verbose(false)
//            .withFileset()
//                .dir(src)
//                .includes("**/*.java")
//                .end()
//            .run();

        target.finished();
        ant.buildFinished();
    }
}
