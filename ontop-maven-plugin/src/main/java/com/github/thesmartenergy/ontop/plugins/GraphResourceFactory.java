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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDFS;

/**
 *
 * @author Maxime Lefrançois <maxime.lefrancois at emse.fr>
 */
public class GraphResourceFactory {

    private final String base;
    private final int graphsDirectoryURILength;

    public GraphResourceFactory(String base, File graphsDirectory) {
        this.base = base;
        this.graphsDirectoryURILength = graphsDirectory.toURI().toString().length();
    }

    public GraphResource parse(final String graphPath, final File file) throws OntopException {
        final String fileName = file.getName();
        final String filePath = file.toURI().toString().substring(graphsDirectoryURILength);
        final Model model;
        try {
            model = RDFDataMgr.loadModel(file.getPath());
        } catch (Exception ex) {
            throw new OntopException("Error while parsing file " + fileName + ": errors are \n ", ex);
        }
        final List<String> errors = new ArrayList<>();
        
        Resource graph = ResourceFactory.createResource(base + graphPath);
                
        // extract defined resources and check their quality
        // local names w.r.t. base
        final Set<String> definedResources = new HashSet<>();

        model.listSubjects().forEachRemaining(new Consumer<Resource>() {
            public void accept(Resource subject) {
                if (model.contains(subject, RDFS.isDefinedBy, graph)) {
                    if (!subject.isURIResource()) {
                        errors.add("All the defined resources should be URI resources.");
                    } else {
                        String uri = subject.asResource().getURI();
                        if (!uri.startsWith(base)) {
                            errors.add("Defined resources " + uri + " should start by " + base);
                        } else {
                            definedResources.add(uri.substring(base.length()));
                        }
                    }
                }

                model.listStatements(subject, null, (RDFNode) null).forEachRemaining(new Consumer<Statement>() {
                    public void accept(Statement t) {
                        Resource resource = t.getPredicate();
                        if (model.contains(resource, RDFS.isDefinedBy, graph)) {
                            if (!resource.isURIResource()) {
                                errors.add("All the defined resources should be URI resources.");
                            } else {
                                String uri = resource.asResource().getURI();
                                if (!uri.startsWith(base)) {
                                    errors.add("Defined resources " + uri + " should start by " + base);
                                } else {
                                    definedResources.add(uri.substring(base.length()));
                                }
                            }
                        } 
                        if (!t.getObject().isURIResource()) {
                            return;
                        }
                        resource = t.getObject().asResource();
                        if (model.contains(resource, RDFS.isDefinedBy, graph)) {
                            if (!resource.isURIResource()) {
                                errors.add("All the defined resources should be URI resources.");
                            } else {
                                String uri = resource.asResource().getURI();
                                if (!uri.startsWith(base)) {
                                    errors.add("Defined resources " + uri + " should start by " + base);
                                } else {
                                    definedResources.add(uri.substring(base.length()));
                                }
                            }
                        }
                    }
                });
            }
        });
        
        if (!errors.isEmpty()) {
            throw new OntopException(String.join("\n", errors.toArray(new String[]{})));
        }
        return new GraphResource(base, filePath, graphPath, file, definedResources);
    }

    private RDFNode getObject(Model model, Resource graph, String uri) throws OntopException {
        List<RDFNode> nodes = getObjects(model, graph, uri);
        if (nodes.size() > 1) {
            throw new OntopException("At most one object for property " + uri + " must be provided");
        }
        return nodes.get(0);
    }

    private List<RDFNode> getObjects(Model model, Resource graph, String uri) throws OntopException {
        List<RDFNode> nodes = model.listObjectsOfProperty(graph, model.getProperty(uri)).toList();
        if (nodes.size() < 1) {
            throw new OntopException("At least one object for property " + uri + " must be provided");
        }
        return nodes;
    }

}