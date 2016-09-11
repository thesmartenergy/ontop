/*
 * Copyright 2016 ITEA 12004 SEAS Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.thesmartenergy.ontop.plugins;

import java.io.File;
import java.util.Set;

/**
 *
 * @author Maxime Lefrançois <maxime.lefrancois at emse.fr>
 */
public class GraphResource {

    private final String base;
    private final String filePath;
    private final String graphPath;
    private final File file;
    private final Set<String> definedResources;

    GraphResource(String base, String filePath, String graphPath, File file, Set<String> definedResources) {
        this.base = base;
        this.filePath = filePath;
        this.graphPath = graphPath;
        this.file = file;
        this.definedResources = definedResources;
    }

    public String getFilePath() {
        return filePath;
    }
    
    public String getGraphPath() {
        return graphPath;
    }

    public File getFile() {
        return file;
    }
    
    public Set<String> getDefinedResources() {
        return definedResources;
    }

    @Override
    public String toString() {
        return base+getGraphPath();
    }

}