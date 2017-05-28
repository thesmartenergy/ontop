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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

/**
 *
 * @author Maxime Lefrançois <maxime.lefrancois at emse.fr>
 */
public class VersionedOntologyFactory {

    static final Pattern p = Pattern.compile("^([a-zA-Z][a-zA-Z0-9-]*)-([0-9]+)\\.([0-9]+)\\.ttl$");

    // maps file name to ontology paths. 
    static final Map<String, String> ontoShortNames = new HashMap<>();

    private final String base;
    private final int ontologiesDirectoryURILength;

    public VersionedOntologyFactory(String base, File ontologiesDirectory) {
        this.base = base;
        this.ontologiesDirectoryURILength = ontologiesDirectory.toURI().toString().length();
    }

    public VersionedOntology parse(final File file) throws OntopException {
        final String fileName = file.getName();
        final String filePath = file.toURI().toString().substring(ontologiesDirectoryURILength);
//        Logger.getAnonymousLogger().info("filePath: " + filePath);
        final Matcher m = p.matcher(fileName);
        boolean b = m.matches();
        if (!b) {
            throw new OntopException("File " + fileName + " does not match the pattern NAME-MAJOR.MINOR.ttl");
        }
        final String name = m.group(1);
        final int major = Integer.parseInt(m.group(2));
        final int minor = Integer.parseInt(m.group(3));
        final Model model;
        try {
            model = RDFDataMgr.loadModel(file.getPath());
        } catch (Exception ex) {
            throw new OntopException("Error while parsing file " + fileName + ": errors are \n ", ex);
        }
        final List<String> errors = new ArrayList<>();

        final Resource ontology = getOntologyResource(model);
        final String ontologyUri = ontology.getURI();
//        Logger.getAnonymousLogger().info("ontologyUri: " + ontologyUri);

        if (!ontoShortNames.containsKey(name)) {
            ontoShortNames.put(name, ontologyUri);
        } else {
            String expectedOntologyUri = ontoShortNames.get(name);
            if (!ontologyUri.equals(expectedOntologyUri)) {
                errors.add("For a file with name " + name + ", one previously got an ontology " + expectedOntologyUri + ". Was expecting this URI agains.");
            }
        }

        final String ontologyPath = getPrimaryResourceUri(ontologyUri).substring(base.length());
//        Logger.getAnonymousLogger().info("ontologyPath: " + ontologyPath);

        if (!model.contains(ontology, RDF.type, model.getResource("http://purl.org/vocommons/voaf#Vocabulary"))) {
            errors.add("Ontology must be a voaf:Vocabulary");
        }

        try {
            getObjects(model, ontology, "http://purl.org/dc/terms/title");
        } catch (OntopException ex) {
            errors.add(ex.getMessage());
        }

        try {
            getObjects(model, ontology, "http://purl.org/dc/terms/description");
        } catch (OntopException ex) {
            errors.add(ex.getMessage());
        }

        try {
            getDate(model, ontology, "http://purl.org/dc/terms/issued");
        } catch (OntopException ex) {
            errors.add(ex.getMessage());
        }

//            try {
//                getDates(model, ontology, "http://purl.org/dc/terms/modified");
//            } catch (OntopException ex) {
//                errors.add(ex.getMessage());
//            }
        try {
            getObjects(model, ontology, "http://purl.org/dc/terms/creator");
        } catch (OntopException ex) {
            errors.add(ex.getMessage());
        }

        try {
            getObject(model, ontology, "http://purl.org/dc/terms/license");
        } catch (OntopException ex) {
            errors.add(ex.getMessage());
        }

//            // check vann:preferredNamesapcePrefix, vann:preferredNamesapceUri, check on prefix.cc that they do not exist,...
        final String preferredNamespacePrefix = getLiteral(model, ontology, "http://purl.org/vocab/vann/preferredNamespacePrefix");
//            // is index means that this ontology must be mapped to URL base
//
//            final String expectedOntologyPrimaryResourceUri = isIndex ? base : base + filePath + name;
//            final String ontologyPrimaryResourceUri = getPrimaryResourceUri(ontology.getURI());
//            if (!ontologyPrimaryResourceUri.equals(expectedOntologyPrimaryResourceUri)) {
//                errors.add("Ontology URL expected to be <" + expectedOntologyPrimaryResourceUri + ">. Instead, got <" + ontologyPrimaryResourceUri);
//            }

        final String preferredNamespaceUri = getUri(model, ontology, "http://purl.org/vocab/vann/preferredNamespaceUri");
//            String preferredNamespacePrimaryResourceUri = getPrimaryResourceUri(preferredNamespaceUri);

        final String versionIRI = getUri(model, ontology, "http://www.w3.org/2002/07/owl#versionIRI");
        if (!versionIRI.startsWith(base)) {
            errors.add("Version IRI of ontology " + file + " must start with " + base);
        }
        final String versionPath = getPrimaryResourceUri(versionIRI).substring(base.length());
//        Logger.getAnonymousLogger().info("versionPath: " + versionPath);

        final String versionInfo = getLiteral(model, ontology, "http://www.w3.org/2002/07/owl#versionInfo");
        if (!versionInfo.startsWith("v" + major + "." + minor)) {
            errors.add("The owl:versionInfo is expected to start with " + "v" + major + "." + minor);
        }

        if (!errors.isEmpty()) {
            throw new OntopException(String.join("\n\t", errors.toArray(new String[]{})));
        }

        // extract defined resources and check their quality
        // local names w.r.t. base
        final Set<String> definedResources = new HashSet<>();
        // local names w.r.t. base
        final Set<String> referencedInternalResources = new HashSet<>();
        // absolute URIs
        final Set<String> referencedExternalResources = new HashSet<>();

        model.listSubjects().forEachRemaining(new Consumer<Resource>() {
            public void accept(Resource subject) {
                if (model.contains(subject, RDFS.isDefinedBy, ontology)) {
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
                } else if (subject.isURIResource()) {
                    if (subject.getURI().startsWith(base)) {
                        referencedInternalResources.add(subject.getURI().substring(base.length()));
                    } else {
                        referencedExternalResources.add(subject.getURI());
                    }
                }

                model.listStatements(subject, null, (RDFNode) null).forEachRemaining(new Consumer<Statement>() {
                    public void accept(Statement t) {
                        Resource resource = t.getPredicate();
                        if (model.contains(resource, RDFS.isDefinedBy, ontology)) {
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
                        } else if (resource.isURIResource()) {
                            if (resource.getURI().startsWith(base)) {
                                referencedInternalResources.add(resource.getURI().substring(base.length()));
                            } else {
                                referencedExternalResources.add(resource.getURI());
                            }
                        }
                        if (!t.getObject().isURIResource()) {
                            return;
                        }
                        resource = t.getObject().asResource();
                        if (model.contains(resource, RDFS.isDefinedBy, ontology)) {
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
                        } else if (resource.isURIResource()) {
                            if (resource.getURI().startsWith(base)) {
                                referencedInternalResources.add(resource.getURI().substring(base.length()));
                            } else {
                                referencedExternalResources.add(resource.getURI());
                            }
                        }
                    }
                });
            }
        });

        if (!errors.isEmpty()) {
            throw new OntopException(String.join("\n", errors.toArray(new String[]{})));
        }
        return new VersionedOntology(base, filePath, ontologyPath, versionPath, major, minor, file, definedResources, referencedInternalResources, referencedExternalResources);
    }

    private Resource getOntologyResource(Model model) throws OntopException {
        List<Resource> resources = model.listResourcesWithProperty(RDF.type, OWL2.Ontology).toList();
        if (resources.size() != 1) {
            throw new OntopException("Exactly one ontology must be defined. got " + resources.size());
        }
        return resources.get(0);
    }

    private String getLiteral(Model model, Resource ontology, String uri) throws OntopException {
        RDFNode node = getObject(model, ontology, uri);
        if (!node.isLiteral()) {
            throw new OntopException("At object for property " + uri + " must be a literal");
        }
        return node.asLiteral().getLexicalForm();
    }

    private String getUri(Model model, Resource ontology, String uri) throws OntopException {
        RDFNode node = getObject(model, ontology, uri);
        if (!node.isURIResource()) {
            throw new OntopException("At object for property " + uri + " must be a URI");
        }
        return node.asResource().getURI();
    }

    private RDFNode getObject(Model model, Resource ontology, String uri) throws OntopException {
        List<RDFNode> nodes = getObjects(model, ontology, uri);
        if (nodes.size() > 1) {
            throw new OntopException("At most one object for property " + uri + " must be provided");
        }
        return nodes.get(0);
    }

    private List<RDFNode> getObjects(Model model, Resource ontology, String uri) throws OntopException {
        List<RDFNode> nodes = model.listObjectsOfProperty(ontology, model.getProperty(uri)).toList();
        if (nodes.size() < 1) {
            throw new OntopException("At least one object for property " + uri + " must be provided");
        }
        return nodes;
    }

    private List<Literal> getDates(Model model, Resource ontology, String uri) throws OntopException {
        List<Literal> dates = new ArrayList<>();
        for (RDFNode node : getObjects(model, ontology, uri)) {
            if (!node.isLiteral()) {
                throw new OntopException("node " + node + " should be a literal");
            }
            dates.add(node.asLiteral());
        }
        // TODO: missing, validate each date.
        return dates;
    }

    private Literal getDate(Model model, Resource ontology, String uri) throws OntopException {
        List<RDFNode> nodes = model.listObjectsOfProperty(ontology, model.getProperty(uri)).toList();
        if (nodes.size() != 1) {
            throw new OntopException("A unique valid " + uri + " date must be provided");
        } else {
            RDFNode date = nodes.get(0);
            if (!date.isLiteral()) {
                throw new OntopException("The " + uri + " date must be a literal");
            }
            if (!date.asLiteral().getDatatypeURI().equals("http://www.w3.org/2001/XMLSchema#date")
                    && !date.asLiteral().getDatatypeURI().equals("http://www.w3.org/2001/XMLSchema#dateTime")) {
                throw new OntopException("The " + uri + " date must be a xsd:date or a xsd:dateTime");
            }
            // TODO: check that it is valid, etc.
            return date.asLiteral();
        }
    }

    private String getPrimaryResourceUri(String resourceUri) {
        int pos = resourceUri.indexOf("#");
        if (pos != -1) {
            return resourceUri.substring(0, pos);
        } else {
            return resourceUri;
        }
    }
}