///*
// * Copyright 2016 ITEA 12004 SEAS Project.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package com.github.thesmartenergy.ontop;
//
//import com.github.thesmartenergy.ontop.resources.GraphResource;
//import com.github.thesmartenergy.ontop.resources.OntologyVersion;
//import com.github.thesmartenergy.ontop.resources.SiteResource;
//import com.github.thesmartenergy.ontop.resources.Version;
//import com.github.thesmartenergy.ontop.resources.VersionedOntology;
//import com.github.thesmartenergy.rdfp.RDFP;
//import com.sun.research.ws.wadl.Representation;
//import java.io.File;
//import java.io.IOException;
//import java.io.PrintWriter;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//import java.util.function.Consumer;
//import java.util.function.Predicate;
//import java.util.logging.Level;
//import java.util.logging.Logger;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//import java.util.regex.PatternSyntaxException;
//import javax.ws.rs.core.MediaType;
//import org.apache.jena.query.Query;
//import org.apache.jena.query.QueryExecution;
//import org.apache.jena.query.QueryExecutionFactory;
//import org.apache.jena.query.QueryFactory;
//import org.apache.jena.query.QuerySolution;
//import org.apache.jena.query.ResultSet;
//import org.apache.jena.rdf.model.Model;
//import org.apache.jena.rdf.model.RDFNode;
//import org.apache.jena.rdf.model.Resource;
//import org.apache.jena.rdf.model.Statement;
//import org.apache.jena.rdf.model.StmtIterator;
//import org.apache.jena.riot.RDFDataMgr;
//import org.apache.jena.vocabulary.RDF;
//import org.apache.jena.vocabulary.RDFS;
//
///**
// * A resources map is generated from a turtle document. It is parsed by the
// * dependency to be used by jersey filters and resources.
// *
// * @author Maxime Lefrançois <maxime.lefrancois at emse.fr>
// */
//public class ResourcesMap {
//
//    final String base;
//
//    public ResourcesMap(String base) {
//        this.base = base;
//    }
//    
//    
//    /**
//     * value rdfs:isDefinedBy key .
//     */
//    private final Map<String, String> redirections = new HashMap<>();
//
//    /**
//     * resources can be of the following classes: LocalFileRepresentation
//     * MultipleRepresentedResource GraphResource OntologyVersion
//     * VersionedOntology
//     *
//     */
//    private final Map<String, SiteResource> resources = new HashMap<>();
//
//    /**
//     * instantiates a resource map reading a configuration file.
//     *
//     * @param conf
//     * @return
//     * @throws OntopException
//     */
//    public static ResourcesMap read(String base, Model conf, File directory, PrintWriter report) throws OntopException {
//        
//        ResourcesMap map = new ResourcesMap(base);
//        
//        List<String> errors = new ArrayList<>();
//
//        try {
//            map.readRedirections(conf, report);
//        } catch (OntopException ex) {
//            errors.addAll(ex.getMessages());
//        }
//        
//        try {
//            map.readOntologySet(conf, directory, report);
//        } catch (OntopException ex) {
//            errors.addAll(ex.getMessages());
//        }
//
//        try {
//            map.readOntology(conf, directory, report);
//        } catch (OntopException ex) {
//            errors.addAll(ex.getMessages());
//        }
//
//        try {
//            map.readGraph(conf, directory, report);
//        } catch (OntopException ex) {
//            errors.addAll(ex.getMessages());
//        }
//
//        try {
//            map.readRepresentations(conf, report);
//        } catch (OntopException ex) {
//            errors.addAll(ex.getMessages());
//        }
//
////        try {
////            map.readMultiRepresentations(conf, report);
////        } catch (OntopException ex) {
////            errors.addAll(ex.getMessages());
////        }
//        
//        return map;
//    }
//
//    private void readOntologySet(Model conf, File directory, PrintWriter report) throws OntopException {
//        List<String> errors = new ArrayList<>();
//        final String queryString = "BASE <https://w3id.org/rdfp/> \n"
//                + "PREFIX ontop: <https://w3id.org/ontop/> \n"
//                + "PREFIX rdfp: <https://w3id.org/rdfp/> \n"
//                + "SELECT * WHERE {\n"
//                + " ?x a          ontop:OntologySet ; \n"
//                + "    rdfp:fileSelector ?selector ."
//                + "}";
//
//        final Query query = QueryFactory.create(queryString);
//        try (QueryExecution qexec = QueryExecutionFactory.create(query, conf)) {
//            ResultSet results = qexec.execSelect();
//            for (; results.hasNext();) {
//                QuerySolution soln = results.nextSolution();
//                RDFNode selector = soln.get("selector");
//                if (!selector.isLiteral()) {
//                    errors.add(" cannot read from " + selector + ". Expected a literal.");
//                    continue;
//                }
//                String regex = selector.asLiteral().getLexicalForm();
//                final Pattern p;
//                final Path directoryPath = Paths.get(directory.toURI());
//                try {
//                    p = Pattern.compile(regex);
//                } catch (PatternSyntaxException ex) {
//                    errors.add(" cannot read from " + regex + ". Invalid regular expression: " + ex.getMessage());
//                    continue;
//                }
//                try {
//                    Files.walk(Paths.get(directory.toURI()))
//                            .filter(new Predicate<Path>() {
//                                @Override
//                                public boolean test(Path t) {
//                                    String relativePath = directoryPath.relativize(t).toString();
//                                    Matcher m = p.matcher(relativePath);
//                                    return m.matches();
//                                }
//                            }).forEach(new Consumer<Path>() {
//                        @Override
//                        public void accept(Path t) {
//                            Resource anon = conf.createResource();
//                            try {
//                                registerOntology(anon, directory, directoryPath.relativize(t).toString(), report);
//                            } catch (OntopException ex) {
//                                errors.add("error wihgle registering ontology: " + ex.getMessage());
//                            }
//                        }
//                    });
//                } catch (IOException ex) {
//                    errors.add(ex.getMessage());
//                }
//            }
//        }
//        if(!errors.isEmpty()) {
//            throw new OntopException(errors);
//        }
//    }
//
//
//    private void readOntology(Model conf, File directory, PrintWriter report) throws OntopException {
//        List<String> errors = new ArrayList<>();
//        final String queryString = "BASE <https://w3id.org/rdfp/> \n"
//                + "PREFIX ontop: <https://w3id.org/ontop/> \n"
//                + "PREFIX rdfp: <https://w3id.org/rdfp/> \n"
//                + "SELECT * WHERE {\n"
//                + " ?x a          ontop:Ontology ; \n"
//                + "    rdfp:filePath ?filePath ."
//                + "}";
//
//        final Query query = QueryFactory.create(queryString);
//        try (QueryExecution qexec = QueryExecutionFactory.create(query, conf)) {
//            ResultSet results = qexec.execSelect();
//            for (; results.hasNext();) {
//                QuerySolution soln = results.nextSolution();
//                Resource x = soln.getResource("x");
//                RDFNode selector = soln.get("filePath");
//                if (!selector.isLiteral()) {
//                    errors.add(" cannot read from " + selector + ". Expected a literal.");
//                    continue;
//                }
//                String filePath = selector.asLiteral().getLexicalForm();
//
//                File ontology = new File(directory, filePath);
//                if (!ontology.exists() || ontology.isDirectory()) {
//                    report.println("path " + filePath + " does not resolve to a file.");
//                }
//                try {
//                    registerOntology(x, directory, filePath, report);
//                } catch (OntopException ex) {
//                    report.println("errosr while registering ontology: " + ex.getMessage());
//                }
//            }
//        }
//        if(!errors.isEmpty()) {
//            throw new OntopException(errors);
//        }
//    }
//
//    public void registerOntology(Resource x, final File directory, final String filePath, PrintWriter report) throws OntopException {
//        final File file = new File(directory, filePath);
//        final Model model;
//        try {
//            model = RDFDataMgr.loadModel(file.getPath());
//        } catch (Exception ex) {
//            throw new OntopException("Error while parsing file " + file);
//        }
//        if(x.isAnon()) {
//            if(resources.containsKey(filePath)) {
//                throw new OntopException("a resource with path "+ filePath + " already exists");
//            }
//            final OntologyVersion ontologyVersion = new OntologyVersion(null, base, model, filePath, null, null, null, file);
//            resources.put(filePath, ontologyVersion);
//        } else if(!x.getURI().startsWith(base)) {
//            throw new OntopException("Ontology version URI " + x + " should start with " + base);
//        } else {
//            String ontologyPath = x.getURI().substring(base.length());
//            if(resources.containsKey(ontologyPath)) {
//                throw new OntopException("a resource with path "+ filePath + " already exists");
//            }
//            final OntologyVersion ontologyVersion = new OntologyVersion(null, base, model, ontologyPath, null, null, null, file);
//            resources.put(ontologyPath, ontologyVersion);
//        }
//    }
//
//    private void readGraph(Model conf, File directory, PrintWriter report) throws OntopException {
//        List<String> errors = new ArrayList<>();
//        final String queryString = "BASE <https://w3id.org/rdfp/> \n"
//                + "PREFIX ontop: <https://w3id.org/ontop/> \n"
//                + "PREFIX rdfp: <https://w3id.org/rdfp/> \n"
//                + "SELECT * WHERE {\n"
//                + " ?x a          rdfp:Graph ; \n"
//                + "    rdfp:filePath ?filePath ."
//                + "}";
//
//        final Query query = QueryFactory.create(queryString);
//        try (QueryExecution qexec = QueryExecutionFactory.create(query, conf)) {
//            ResultSet results = qexec.execSelect();
//            for (; results.hasNext();) {
//                QuerySolution soln = results.nextSolution();
//                Resource x = soln.getResource("x");
//                RDFNode selector = soln.get("filePath");
//                if (!selector.isLiteral()) {
//                    errors.add(" cannot read from " + selector + ". Expected a literal.");
//                    continue;
//                }
//                String filePath = selector.asLiteral().getLexicalForm();
//
//                File graph = new File(directory, filePath);
//                if (!graph.exists() || graph.isDirectory()) {
//                    report.println("path " + filePath + " does not resolve to a file.");
//                }
//                try {
//                    registerGraph(x, directory, filePath, report);
//                } catch (OntopException ex) {
//                    report.println("errosr while registering ontology: " + ex.getMessage());
//                }
//            }
//        }
//        if(!errors.isEmpty()) {
//            throw new OntopException(errors);
//        }
//    }
//
//    public void registerGraph(Resource x, final File directory, final String filePath, PrintWriter report) throws OntopException {
//        final File file = new File(directory, filePath);
//        final Model model;
//        try {
//            model = RDFDataMgr.loadModel(file.getPath());
//        } catch (Exception ex) {
//            throw new OntopException("Error while parsing file " + file);
//        }
//        if(x.isAnon()) {
//            if(resources.containsKey(filePath)) {
//                throw new OntopException("a resource with path "+ filePath + " already exists");
//            }
//            final GraphResource graphResource = new GraphResource(base, model, file, filePath);
//            resources.put(filePath, graphResource);
//        } else if(!x.getURI().startsWith(base)) {
//            throw new OntopException("Ontology version URI " + x + " should start with " + base);
//        } else {
//            String graphPath = x.getURI().substring(base.length());
//            if(resources.containsKey(graphPath)) {
//                throw new OntopException("a resource with path "+ filePath + " already exists");
//            }
//            final GraphResource graphResource = new GraphResource(base, model, file, graphPath);
//            resources.put(graphPath, graphResource);
//        }
//    }
//
//
////    public void registerOntology(VersionedOntology ontology) throws OntopException {
////        registerVersionUri(ontology);
////        boolean isMostRecent = registerMainUri(ontology);
////        registerResources(ontology, isMostRecent);
////    }
////
////    private void registerVersionUri(VersionedOntology ontology) throws OntopException {
////        String versionPath = ontology.getVersionPath();
////        if (ontologies.containsKey(versionPath)) {
////            throw new OntopException("An ontology with version URI " + versionPath + " already exists.");
////        } else {
////            ontologies.put(consolidate(versionPath), ontology);
////        }
////
////    }
////
////    private boolean registerMainUri(VersionedOntology ontology) throws OntopException {
////        String ontologyPath = ontology.getOntologyPath();
////        VersionedOntology other = ontologies.get(ontologyPath);
////        if (other == null || ontology.compareVersions(other) > 0) {
////            ontologies.put(consolidate(ontologyPath), ontology);
////            return true;
////        }
////        return false;
////    }
////
////    private void registerResources(VersionedOntology ontology, boolean mostRecent) {
////        for (String resource : ontology.getDefinedResources()) {
////            String primaryResource = getPrimaryResourceUri(resource);
////            if (mostRecent || !redirections.containsKey(primaryResource)) {
////                redirections.put(primaryResource, ontology.getVersionPath());
////            }
////        }
////    }
////    public Map<String, String> getAliases() {
////        return aliases;
////    }
////
////    public Map<String, Set<Representation>> getMultiRepresentations() {
////        return multiRepresentations;
////    }
////
////    public Map<String, Representation> getRepresentations() {
////        return representations;
////    }
////
//    private void readRedirections(Model inputConf, PrintWriter report) throws OntopException {
//        List<String> errors = new ArrayList<>();
//        StmtIterator sit = inputConf.listStatements(null, RDFS.isDefinedBy, (RDFNode) null);
//        while (sit.hasNext()) {
//            Statement s = sit.next();
//            final Resource subject = s.getSubject();
//            final RDFNode object = s.getObject();
//            if (!subject.isURIResource()) {
//                errors.add("resource " + subject + " should be a URI resource");
//                continue;
//            }
//            if (!object.isURIResource()) {
//                errors.add("resource " + object + " should be a URI resource");
//                continue;
//            }
//            redirections.put(getLocalPath(subject.getURI()), getLocalPath(object.asResource().getURI()));
//        }
//        if(!errors.isEmpty()) {
//            throw new OntopException(errors);
//        }
//    }
////
////    private void readRepresentations(Model inputConf) throws OntopException {
////        StmtIterator sit = inputConf.listStatements(null, RDFP.mediaType, (RDFNode) null);
////        while (sit.hasNext()) {
////            Statement s = sit.next();
////            final Resource subject = s.getSubject();
////            final RDFNode object = s.getObject();
////            if (!subject.isURIResource()) {
////                throw new OntopException("resource " + subject + " should be a URI resource");
////            }
////            if (!object.isLiteral()) {
////                throw new OntopException("resource " + object + " should be a literal");
////            }
////            try {
////                String localPath = getLocalPath(subject.getURI());
////                MediaType mt = MediaType.valueOf(object.asLiteral().getLexicalForm());
////                Representation representation = new Representation(mt, localPath);
////                representations.put(localPath, representation);
////            } catch (Exception ex) {
////                throw new OntopException(ex.getClass().getName() + ": " + ex.getMessage());
////            }
////        }
////    }
////
////    private void readMultiRepresentations(Model inputConf) throws OntopException {
////        StmtIterator sit = inputConf.listStatements(null, RDFP.representedBy, (RDFNode) null);
////        while (sit.hasNext()) {
////            Statement s = sit.next();
////            final Resource subject = s.getSubject();
////            final RDFNode object = s.getObject();
////            if (!subject.isURIResource()) {
////                throw new OntopException("resource " + subject + " should be a URI resource");
////            }
////            if (!object.isURIResource()) {
////                throw new OntopException("resource " + object + " should be a URI resource");
////            }
////            try {
////                String localPath = getLocalPath(subject.getURI());
////                String objectLocalPath = getLocalPath(object.asResource().getURI());
////                Representation representation = representations.get(objectLocalPath);
////                if (representation == null) {
////                    throw new OntopException("representation should not be null for " + objectLocalPath);
////                }
////                Set<Representation> set = multiRepresentations.get(localPath);
////                if (set == null) {
////                    set = new HashSet<>();
////                    multiRepresentations.put(localPath, set);
////                }
////                set.add(representation);
////            } catch (Exception ex) {
////                throw new OntopException(ex.getClass().getName() + ": " + ex.getMessage());
////            }
////        }
////    }
//
//    private String getLocalPath(String path) throws OntopException {
//        if (!path.startsWith(base)) {
//            throw new OntopException("path must start with " + base + ". got " + path);
//        }
//        return path.substring(base.length());
//    }
//}
