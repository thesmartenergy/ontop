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

import com.github.thesmartenergy.ontop.OntopException;
import java.io.File;
import java.util.Set;

/**
 *
 * @author Maxime Lefrançois <maxime.lefrancois at emse.fr>
 */
public class VersionedOntology {

    private final String base;
    private final String filePath;
    private final String ontologyPath;
    private final String versionPath;
    private final int major;
    private final int minor;
    private final File file;
    private final Set<String> definedResources;
    private final Set<String> referencedInternalResources;
    private final Set<String> referencedExternalResources;

    VersionedOntology(String base, String filePath, String ontologyPath, String versionPath, int major, int minor, File file, Set<String> definedResources, Set<String> referencedInternalResources, Set<String> referencedExternalResources) {
        // any file whose name does not conform to NAME-MAJOR.MINOR.ttl is rejected
        this.base = base;
        this.filePath = filePath;
        this.ontologyPath = ontologyPath;
        this.versionPath = versionPath;
        this.major = major;
        this.minor = minor;
        this.file = file;
        this.definedResources = definedResources;
        this.referencedInternalResources = referencedInternalResources;
        this.referencedExternalResources = referencedExternalResources;
    }

    public String getFilePath() {
        return filePath;
    }
    
    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public String getOntologyPath() {
        return ontologyPath;
    }

    public String getVersionPath() {
        return versionPath;
    }

    public File getFile() {
        return file;
    }
    
    public Set<String> getDefinedResources() {
        return definedResources;
    }

    public Set<String> getReferencedExternalResources() {
        return referencedExternalResources;
    }

    public Set<String> getReferencedInternalResources() {
        return referencedInternalResources;
    }

    @Override
    public String toString() {
        return base+getVersionPath();
    }

    /**
     * returns -1 if 'this' is lower than o returns 0 if 'this' is equal to o
     * returns 1 if 'this' is greater than o
     *
     * @param o
     * @return
     * @throws OntopException
     */
    public int compareVersions(VersionedOntology o) throws OntopException {
        if (!base.equals(o.base) || !ontologyPath.equals(o.ontologyPath)) {
            throw new OntopException("Version comparison must be done with same ontology series.");
        }
        if (major > o.major) {
            return 1;
        } else if (major == o.major) {
            if (minor > o.minor) {
                return 1;
            } else if (minor == o.minor) {
                return 0;
            }
        }
        return -1;
    }
}