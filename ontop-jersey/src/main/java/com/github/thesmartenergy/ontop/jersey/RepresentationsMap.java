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
package com.github.thesmartenergy.ontop.jersey;


import com.github.thesmartenergy.ontop.OntopException;
import com.github.thesmartenergy.rdfp.BaseURI;
import com.github.thesmartenergy.rdfp.RDFP;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDFS;

/**
 * A representations map is generated from a turtle document. It is parsed by
 * the dependency to be used by jersey filters and resources.
 *
 * @author Maxime Lefrançois <maxime.lefrancois at emse.fr>
 */
@ApplicationScoped
public class RepresentationsMap {
    
    private static final Logger LOG = Logger.getLogger(RepresentationsMap.class.getSimpleName());

    /**
     * value rdfs:isDefinedBy key
     */
    private final Map<String, String> redirections = new HashMap<>();

    /**
     * value ontop:alias key
     */
    private final Map<String, String> aliases = new HashMap<>();

    /**
     * key ontop:representedBy one of values
     */
    private final Map<String, Set<Representation>> multiRepresentations = new HashMap<>();

    /**
     * key ontop:mediaType some media type
     */
    private final Map<String, Representation> representations = new HashMap<>();

    @Inject
    @BaseURI
    private String base;

    private Model conf = null;

    @PostConstruct
    private void postConstruct() {
        final String confpath = "_ontop/config.ttl";
        try {
            conf = RDFDataMgr.loadModel(RepresentationsMap.class.getClassLoader().getResource(confpath).toURI().toString());
        } catch (Exception ex) {
            LOG.warning("Configuration file '" + confpath + "'. ONTOP will be disabled.");
            return;
        }

        try {
            readRedirections();
        } catch (OntopException ex) {
            throw new RuntimeException(ex.getMessage());
        }

        try {
            readAliases();
        } catch (OntopException ex) {
            throw new RuntimeException(ex.getMessage());
        }

        try {
            readRepresentations();
        } catch (OntopException ex) {
            throw new RuntimeException(ex.getMessage());
        }

        try {
            readMultiRepresentations();
        } catch (OntopException ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    public Map<String, String> getRedirections() {
        return redirections;
    }

    public Map<String, String> getAliases() {
        return aliases;
    }

    public Map<String, Set<Representation>> getMultiRepresentations() {
        return multiRepresentations;
    }

    public Map<String, Representation> getRepresentations() {
        return representations;
    }

    private void readRedirections() throws OntopException {
        StmtIterator sit = conf.listStatements(null, RDFS.isDefinedBy, (RDFNode) null);
        while (sit.hasNext()) {
            Statement s = sit.next();
            final Resource subject = s.getSubject();
            final RDFNode object = s.getObject();
            if (!subject.isURIResource()) {
                throw new OntopException("resource " + subject + " should be a URI resource");
            }
            if (!object.isURIResource()) {
                throw new OntopException("resource " + object + " should be a URI resource");
            }
            try {
                redirections.put(getLocalPath(subject.getURI()), getLocalPath(object.asResource().getURI()));
            } catch (OntopException ex) {
                throw new OntopException(ex.getMessage()); 
            }
        }

    }

    private void readRepresentations() throws OntopException {
        StmtIterator sit = conf.listStatements(null, RDFP.mediaType, (RDFNode) null);
        while (sit.hasNext()) {
            Statement s = sit.next();
            final Resource subject = s.getSubject();
            final RDFNode object = s.getObject();
            if (!subject.isURIResource()) {
                throw new OntopException("resource " + subject + " should be a URI resource");
            }
            if (!object.isLiteral()) {
                throw new OntopException("resource " + object + " should be a literal");
            }
            try {
                String localPath = getLocalPath(subject.getURI());
                MediaType mt = MediaType.valueOf(object.asLiteral().getLexicalForm());
                Representation representation = new Representation(mt, localPath);
                representations.put(localPath, representation);
            } catch (Exception ex) {
                throw new OntopException(ex.getClass().getName() + ": " + ex.getMessage());
            }
        }
    }

    private void readMultiRepresentations() throws OntopException {
        StmtIterator sit = conf.listStatements(null, RDFP.representedBy, (RDFNode) null);
        while (sit.hasNext()) {
            Statement s = sit.next();
            final Resource subject = s.getSubject();
            final RDFNode object = s.getObject();
            if (!subject.isURIResource()) {
                throw new OntopException("resource " + subject + " should be a URI resource");
            }
            if (!object.isURIResource()) {
                throw new OntopException("resource " + object + " should be a URI resource");
            }
            try {
                String localPath = getLocalPath(subject.getURI());
                String objectLocalPath = getLocalPath(object.asResource().getURI());
                Representation representation = representations.get(objectLocalPath);
                if(representation==null) {
                    throw new OntopException("representation should not be null for " + objectLocalPath);
                }
                Set<Representation> set = multiRepresentations.get(localPath);
                if (set == null) {
                    set = new HashSet<>();
                    multiRepresentations.put(localPath, set);
                }
                set.add(representation);
            } catch (Exception ex) {
                throw new OntopException(ex.getClass().getName() + ": " + ex.getMessage());
            }
        }
    }

    private void readAliases() throws OntopException {
        StmtIterator sit = conf.listStatements(null, RDFP.alias, (RDFNode) null);
        while (sit.hasNext()) {
            Statement s = sit.next();

            final Resource subject = s.getSubject();
            final RDFNode object = s.getObject();
            if (!subject.isURIResource()) {
                throw new OntopException("resource " + subject + " should be a URI resource");
            }
            if (!object.isURIResource()) {
                throw new OntopException("resource " + object + " should be a URI resource");
            }
            try {
                String localPath = getLocalPath(subject.getURI());
                String aliasLocalPath = getLocalPath(object.asResource().getURI());
                aliases.put(aliasLocalPath, localPath);
            } catch (Exception ex) {
                throw new OntopException(ex.getClass().getName() + ": " + ex.getMessage());
            }
        }
    }

    private String getLocalPath(String path) throws OntopException {
        if (!path.startsWith(base)) {
            throw new OntopException("path must start with " + base + ". got " + path);
        }
        return path.substring(base.length());
    }

}