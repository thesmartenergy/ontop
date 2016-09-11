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

import com.github.thesmartenergy.ontop.ONTOP;
import com.github.thesmartenergy.ontop.OntopException;
import com.github.thesmartenergy.rdfp.RDFP;
import fr.inria.edelweiss.kgraph.core.Graph;
import fr.inria.edelweiss.kgtool.load.Load;
import fr.inria.edelweiss.kgtool.transform.Transformer;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.system.stream.StreamManager;
import org.apache.jena.system.JenaSystem;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 *
 * @author Maxime Lefrançois <maxime.lefrancois at emse.fr>
 */
@Mojo(name = "generate", requiresProject = true, threadSafe = false, defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class GenerateResourcesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.url}", readonly = true)
    private String base;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject mavenProject;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Component
    private BuildPluginManager pluginManager;

    /**
     * Skip plug-in execution.
     */
    @Parameter(property = "skip", defaultValue = "false")
    private boolean skip;

    /**
     * location of the ontologies.
     */
    @Parameter(property = "inputDirectory", defaultValue = "${basedir}/src/main/ontop")
    private File inputDirectory;

    /**
     * location of the doxia site descriptor.
     */
    @Parameter(property = "siteDirectory", defaultValue = "${basedir}/src/site")
    private File siteDirectory;

    /**
     * location of the fake site.
     */
    @Parameter(property = "fakeSiteDirectory", defaultValue = "${project.build.directory}/ontop/site")
    private File fakeSiteDirectory;

    /**
     * location of the fake site.
     */
    @Parameter(property = "siteOutputDirectory", defaultValue = "${project.reporting.outputDirectory}")
    private File siteOutputDirectory;

    /**
     * location of the build site.
     */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private File buildDirectory;

    private File ontopDirectory;
    private File finalDirectory;
    private File reportFile;

    @Override
    public void execute() throws MojoExecutionException {

        ontopDirectory = new File(buildDirectory, "ontop/generated");
        finalDirectory = new File(siteOutputDirectory, "WEB-INF/classes/_ontop");
        reportFile = new File(buildDirectory, "ontop.txt");
        reportFile.delete();

        getLog().info("relativizing URLs to " + base);

        if (skip) {
            getLog().info("Skipping execution...");
            return;
        }
        JenaSystem.DEBUG_INIT = false;
        StreamManager.logAllLookups = false;

        generateFolderStructure();

        try (FileWriter fw = new FileWriter(reportFile, true);
                BufferedWriter bw = new BufferedWriter(fw);
                PrintWriter out = new PrintWriter(bw)) {

            Model inputConf;
            try {
                inputConf = RDFDataMgr.loadModel(new File(inputDirectory, "config.ttl").toURI().toString());
            } catch (Exception ex) {
                out.println("not found config.ttl. Aborting.");
                return;
            }

            OntologiesMap map = parseOntologies(inputConf, out);
            generateHtmlDocumentation(out, map);
            generateRdfRepresentations(out, map);
            Model generatedConf = generateConf(out, map);

            GraphsMap graphsMap = parseGraph(inputConf, out);
            generateRdfRepresentationsGraphs(out, graphsMap);
            generatedConf.add(generateConf(out, graphsMap));

            copyRepresentationDirectory(out, inputConf, generatedConf);

            try {
                File ontopConfig = new File(ontopDirectory, "config.ttl");
                generatedConf.write(new FileOutputStream(ontopConfig), "TTL", base);
            } catch (FileNotFoundException ex) {
                throw new MojoExecutionException("error while serializing configuration file", ex);
            }

            copyGeneratedRepresentationDirectory(out);
        } catch (IOException ex) {
            throw new MojoExecutionException("IO Exception with report file", ex);
        }

    }

    private void generateFolderStructure() throws MojoExecutionException {
        try {
            FileUtils.forceMkdir(fakeSiteDirectory);
            FileUtils.cleanDirectory(fakeSiteDirectory);
            FileUtils.copyFile(new File(siteDirectory, "site.xml"), new File(fakeSiteDirectory, "site.xml"));
            FileUtils.forceMkdir(new File(fakeSiteDirectory, "xhtml"));
        } catch (final IOException ioe) {
            throw new MojoExecutionException("Failed to generate directory: " + fakeSiteDirectory, ioe);
        }

        try {
            FileUtils.forceMkdir(ontopDirectory);
            FileUtils.cleanDirectory(ontopDirectory);
        } catch (final IOException ioe) {
            throw new MojoExecutionException("Failed to generate directory: " + ontopDirectory, ioe);
        }

        try {
            FileUtils.forceMkdir(finalDirectory);
        } catch (final IOException ioe) {
            throw new MojoExecutionException("Failed to generate directory: " + finalDirectory, ioe);
        }
    }

    private OntologiesMap parseOntologies(Model conf, PrintWriter report) throws MojoExecutionException {
        OntologiesMap map = new OntologiesMap();
        VersionedOntologyFactory factory = new VersionedOntologyFactory(base, inputDirectory);

        final String queryString = "PREFIX ontop: <https://w3id.org/ontop/> \n"
                + "SELECT * WHERE {\n"
                + " ?x a          ontop:OntologySet ; \n"
                + "    ontop:fileSelector ?selector ."
                + "}";

        final Query query = QueryFactory.create(queryString);
        try (QueryExecution qexec = QueryExecutionFactory.create(query, conf)) {
            ResultSet results = qexec.execSelect();
            for (; results.hasNext();) {
                QuerySolution soln = results.nextSolution();
                RDFNode selector = soln.get("selector");
                if (!selector.isLiteral()) {
                    report.println(" cannot read from " + selector + ". Expected a literal.");
                    continue;
                }
                String regex = selector.asLiteral().getLexicalForm();
                final Pattern p;
                final Path directoryPath = Paths.get(inputDirectory.toURI());
                try {
                    p = Pattern.compile(regex);
                } catch (PatternSyntaxException ex) {
                    report.println(" cannot read from " + regex + ". Invalid regular expression: " + ex.getMessage());
                    continue;
                }
                try {
                    Files.walk(Paths.get(inputDirectory.toURI()))
                            .filter(new Predicate<Path>() {
                                @Override
                                public boolean test(Path t) {
                                    String relativePath = directoryPath.relativize(t).toString();
                                    Matcher m = p.matcher(relativePath);
                                    return m.matches();
                                }
                            }).forEach(new Consumer<Path>() {
                        @Override
                        public void accept(Path t) {
                            String ontologyPath = directoryPath.relativize(t).toString();
                            File ontologyFile = new File(inputDirectory, ontologyPath);
                            VersionedOntology ontology;
                            try {
                                ontology = factory.parse(ontologyFile);
                                report.println("new ontology: filePath: " + ontology.getFilePath() + ", ontologyPath: " + ontology.getOntologyPath() + ", versionPath: " + ontology.getVersionPath() + "\n");
                                map.registerOntology(ontology);
                            } catch (OntopException ex) {
                                report.println("error while analyzing file " + ontologyFile + ":\n " + ex.getMessage() + "\n\n");
                            }
                        }
                    });
                } catch (IOException ex) {
                    report.println(ex.getMessage());
                }
            }
        }
        final String queryString2 = "PREFIX ontop: <https://w3id.org/ontop/> \n"
                + "SELECT * WHERE {\n"
                + " ?x a          ontop:Ontology ; \n"
                + "    ontop:filePath ?filePath ."
                + "}";

        final Query query2 = QueryFactory.create(queryString2);
        try (QueryExecution qexec = QueryExecutionFactory.create(query2, conf)) {
            ResultSet results = qexec.execSelect();
            for (; results.hasNext();) {
                QuerySolution soln = results.nextSolution();
                RDFNode filePathNode = soln.get("filePath");
                if (!filePathNode.isLiteral()) {
                    report.println(" cannot read from " + filePathNode + ". Expected a literal.");
                    continue;
                }
                String filePath = filePathNode.asLiteral().getLexicalForm();
                File ontologyFile = new File(inputDirectory, filePath);
                if (!ontologyFile.exists() || ontologyFile.isDirectory()) {
                    report.println("path " + filePath + " does not resolve to a file.");
                    continue;
                }
                VersionedOntology ontology;
                try {
                    ontology = factory.parse(ontologyFile);
                    report.println("new ontology: filePath: " + ontology.getFilePath() + ", ontologyPath: " + ontology.getOntologyPath() + ", versionPath: " + ontology.getVersionPath() + "\n");
                    map.registerOntology(ontology);
                } catch (OntopException ex) {
                    report.println("error while analyzing file " + ontologyFile + ":\n " + ex.getMessage() + "\n\n");
                }
            }
        }
        return map;
    }

    private GraphsMap parseGraph(Model conf, PrintWriter report) {
        GraphsMap map = new GraphsMap();
        GraphResourceFactory factory = new GraphResourceFactory(base, inputDirectory);

        final String queryString = "BASE <https://w3id.org/rdfp/> \n"
                + "PREFIX ontop: <https://w3id.org/ontop/> \n"
                + "PREFIX rdfp: <https://w3id.org/rdfp/> \n"
                + "SELECT * WHERE {\n"
                + " ?x a          rdfp:Graph ; \n"
                + "    ontop:filePath ?filePath ."
                + "}";

        final Query query = QueryFactory.create(queryString);
        try (QueryExecution qexec = QueryExecutionFactory.create(query, conf)) {
            ResultSet results = qexec.execSelect();
            for (; results.hasNext();) {
                QuerySolution soln = results.nextSolution();
                RDFNode filePathNode = soln.get("filePath");
                if (!filePathNode.isLiteral()) {
                    report.println(" cannot read from " + filePathNode + ". Expected a literal.");
                    continue;
                }
                String filePath = filePathNode.asLiteral().getLexicalForm();

                File graphFile = new File(inputDirectory, filePath);
                if (!graphFile.exists() || graphFile.isDirectory()) {
                    report.println("path " + filePath + " does not resolve to a file.");
                    continue;
                }

                Resource x = soln.getResource("x");
                if (!x.isURIResource() || !x.getURI().startsWith(base)) {
                    report.println("URI " + x + "should start with " + base);
                    continue;
                }
                String graphResourcePath = x.getURI().substring(base.length());
                GraphResource graphResource;
                try {
                    graphResource = factory.parse(graphResourcePath, graphFile);
                    report.println("new graph: filePath: " + graphResource.getFilePath() + ", graphPath: " + graphResource.getGraphPath());
                    map.registerGraph(graphResource);
                } catch (OntopException ex) {
                    report.println("error while analyzing file " + graphFile + ":\n " + ex.getMessage() + "\n\n");
                }
            }
        }
        return map;
    }

    private void generateHtmlDocumentation(PrintWriter report, OntologiesMap map) throws MojoExecutionException {
        for (VersionedOntology ontology : map.getOntologyResources().values()) {
            try {
                Graph g = Graph.create();
                Load ld = Load.create(g);
                String input = ontology.getFile().toURI().toString();
                ld.load(input);

                String test = GenerateResourcesMojo.class.getClassLoader().getResource("generate-documentation-md.rul").toString();
                Transformer t = Transformer.create(g, test);
                String html = t.transform();

                String outputFileName = ontology.getVersionPath() + ".md";
                File outputFile = new File(fakeSiteDirectory, "markdown/" + outputFileName);
                FileUtils.forceMkdir(outputFile.getParentFile());
                FileOutputStream out = new FileOutputStream(outputFile);
                out.write(html.getBytes());
            } catch (Exception ex) {
                report.println("Failed to generate documentation for ontology: " + ontology.getVersionPath() + ":\n " + ex.getMessage() + "\n\n");
                getLog().warn("Failed to generate documentation for ontology: " + ontology.getVersionPath(), ex);
            }
        }

        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-site-plugin"),
                        version("3.5")
                ),
                goal("site"),
                configuration(
                        element(name("siteDirectory"), fakeSiteDirectory.getAbsolutePath()),
                        element(name("outputDirectory"), ontopDirectory.getAbsolutePath()),
                        element(name("reportPlugins"),
                                element(name("plugin"),
                                        element(name("groupId"), "org.apache.maven.plugins"),
                                        element(name("artifactId"), "maven-project-info-reports-plugin"),
                                        element(name("reports"),
                                                element(name("report"), "summary"))))
                ),
                executionEnvironment(
                        mavenProject,
                        mavenSession,
                        pluginManager
                )
        );

        for (String folder : new String[]{"css", "fonts", "css", "images", "img", "js"}) {
            try {
                FileUtils.deleteDirectory(new File(ontopDirectory, folder));
            } catch (IOException ex) {
                getLog().warn("unable to delete useless file: " + ex.getMessage());
            }
        }

        for (String file : new String[]{"project-info.html", "project-summary.html"}) {
            FileUtils.deleteQuietly(new File(ontopDirectory, file));
        }
    }

    private void generateRdfRepresentations(PrintWriter report, OntologiesMap map) {
        for (VersionedOntology ontology : map.getOntologyResources().values()) {
            try {
                Model model = RDFDataMgr.loadModel(ontology.getFile().toURI().toString());

                String ttlPath = ontology.getVersionPath() + ".ttl";
                File ttl = new File(ontopDirectory, ttlPath);
                FileUtils.forceMkdir(ttl.getParentFile());
                if (FilenameUtils.getExtension(ontology.getFile().getName()).endsWith("ttl")) {
                    FileUtils.copyFile(ontology.getFile(), ttl);
                } else {
                    OutputStream out = new FileOutputStream(ttl);
                    model.write(out, "TTL", base);
                }

                String rdfxmlName = ontology.getVersionPath() + ".rdf";
                File rdfxml = new File(ontopDirectory, rdfxmlName);
                FileUtils.forceMkdir(rdfxml.getParentFile());
                OutputStream out = new FileOutputStream(rdfxml);
                model.write(out, "RDF/XML", base);

//                return new Representation(RDFP.APPLICATION_RDFXML_TYPE, outputFile);
            } catch (Exception ex) {
                report.println("Failed to generate RDFXML for ontology: " + ontology.getVersionPath() + ":\n " + ex.getMessage() + "\n\n");
                getLog().warn("Failed to generate RDFXML for ontology: " + ontology.getVersionPath(), ex);
            }
        }
    }

    private void generateRdfRepresentationsGraphs(PrintWriter report, GraphsMap map) {
        for (GraphResource graph : map.getGraphResources().values()) {
            try {
                Model model = RDFDataMgr.loadModel(graph.getFile().toURI().toString());

                String ttlPath = graph.getGraphPath() + ".ttl";
                File ttl = new File(ontopDirectory, ttlPath);
                FileUtils.forceMkdir(ttl.getParentFile());
                if (FilenameUtils.getExtension(graph.getFile().getName()).endsWith("ttl")) {
                    FileUtils.copyFile(graph.getFile(), ttl);
                } else {
                    OutputStream out = new FileOutputStream(ttl);
                    model.write(out, "TTL", base);
                }

                String rdfxmlName = graph.getGraphPath() + ".rdf";
                File rdfxml = new File(ontopDirectory, rdfxmlName);
                FileUtils.forceMkdir(rdfxml.getParentFile());
                OutputStream out = new FileOutputStream(rdfxml);
                model.write(out, "RDF/XML", base);

//                return new Representation(RDFP.APPLICATION_RDFXML_TYPE, outputFile);
            } catch (Exception ex) {
                report.println("Failed to generate RDFXML for graph: " + graph.getGraphPath() + ":\n " + ex.getMessage() + "\n\n");
                getLog().warn("Failed to generate RDFXML for graph: " + graph.getGraphPath(), ex);
            }
        }
    }

    private Model generateConf(PrintWriter report, OntologiesMap map) {
        Model model = ModelFactory.createDefaultModel();
//        getLog().info(base + " " + RDF.uri + " " + RDFS.uri + " " + RDFP.NS);
        model.setNsPrefix("", base);
        model.setNsPrefix("rdf", RDF.uri);
        model.setNsPrefix("rdfs", RDFS.uri);
        model.setNsPrefix("ontop", RDFP.NS);
        Map<String, String> redirections = map.getRedirections();
        for (String path : redirections.keySet()) {
            model.add(model.getResource(path), RDFS.isDefinedBy, model.getResource(redirections.get(path)));
        }
        Map<String, VersionedOntology> ontologyResources = map.getOntologyResources();
        for (String path : ontologyResources.keySet()) {
            VersionedOntology ontology = ontologyResources.get(path);

            report.println("Writing model for path=" + path + ", filePath: " + ontology.getFilePath() + ", ontologyPath: " + ontology.getOntologyPath() + ", versionPath: " + ontology.getVersionPath() + "\n");

            Resource graph = model.getResource(ontology.getVersionPath());
            model.add(graph, RDF.type, RDFP.Graph);

            Resource ttl = model.getResource(ontology.getVersionPath() + ".ttl");
            model.add(graph, RDFP.representedBy, ttl);
            model.add(ttl, RDF.type, RDFP.Resource);
            model.add(ttl, RDFP.mediaType, "text/turtle");

            Resource html = model.getResource(ontology.getVersionPath() + ".html");
            model.add(graph, RDFP.representedBy, html);
            model.add(html, RDF.type, RDFP.Resource);
            model.add(html, RDFP.mediaType, "text/html");

            Resource rdf = model.getResource(ontology.getVersionPath() + ".rdf");
            model.add(graph, RDFP.representedBy, rdf);
            model.add(rdf, RDF.type, RDFP.Resource);
            model.add(rdf, RDFP.mediaType, "application/rdf+xml");

            if (path.equals("index")) {
                model.add(model.getResource(ontology.getVersionPath()), RDFP.alias, model.getResource(path));

                Resource r = model.getResource(path + ".ttl");
                model.add(ttl, RDFP.alias, r);
                model.add(r, RDFP.mediaType, "text/turtle");

                r = model.getResource(path + ".html");
                model.add(html, RDFP.alias, r);
                model.add(r, RDFP.mediaType, "text/html");

                r = model.getResource(path + ".rdf");
                model.add(rdf, RDFP.alias, r);
                model.add(r, RDFP.mediaType, "application/rdf+xml");

                model.add(model.getResource(ontology.getVersionPath()), RDFP.alias, model.getResource(""));
            }
            if (path.equals(ontology.getOntologyPath())) {
                model.add(model.getResource(ontology.getVersionPath()), RDFP.alias, model.getResource(path));

                Resource r = model.getResource(path + ".ttl");
                model.add(ttl, RDFP.alias, r);
                model.add(r, RDFP.mediaType, "text/turtle");

                r = model.getResource(path + ".html");
                model.add(html, RDFP.alias, r);
                model.add(r, RDFP.mediaType, "text/html");

                r = model.getResource(path + ".rdf");
                model.add(rdf, RDFP.alias, r);
                model.add(r, RDFP.mediaType, "application/rdf+xml");
            }
        }
        return model;
    }

    private Model generateConf(PrintWriter report, GraphsMap map) {
        Model model = ModelFactory.createDefaultModel();
//        getLog().info(base + " " + RDF.uri + " " + RDFS.uri + " " + RDFP.NS);
        model.setNsPrefix("", base);
        model.setNsPrefix("rdf", RDF.uri);
        model.setNsPrefix("rdfs", RDFS.uri);
        model.setNsPrefix("ontop", RDFP.NS);
        Map<String, String> redirections = map.getRedirections();
        for (String path : redirections.keySet()) {
            model.add(model.getResource(path), RDFS.isDefinedBy, model.getResource(redirections.get(path)));
        }
        Map<String, GraphResource> graphResources = map.getGraphResources();
        for (String path : graphResources.keySet()) {
            GraphResource graphResource = graphResources.get(path);

            report.println("Writing model for path=" + path + ", filePath: " + graphResource.getFilePath() + ", graphPath: " + graphResource.getGraphPath());

            Resource graph = model.getResource(graphResource.getGraphPath());
            model.add(graph, RDF.type, RDFP.Graph);

            Resource ttl = model.getResource(graphResource.getGraphPath() + ".ttl");
            model.add(graph, RDFP.representedBy, ttl);
            model.add(ttl, RDF.type, RDFP.Resource);
            model.add(ttl, RDFP.mediaType, "text/turtle");

            Resource rdf = model.getResource(graphResource.getGraphPath() + ".rdf");
            model.add(graph, RDFP.representedBy, rdf);
            model.add(rdf, RDF.type, RDFP.Resource);
            model.add(rdf, RDFP.mediaType, "application/rdf+xml");

            if (path.equals("index")) {
                model.add(model.getResource(graphResource.getGraphPath()), RDFP.alias, model.getResource(path));

                Resource r = model.getResource(path + ".ttl");
                model.add(ttl, RDFP.alias, r);
                model.add(r, RDFP.mediaType, "text/turtle");

                r = model.getResource(path + ".rdf");
                model.add(rdf, RDFP.alias, r);
                model.add(r, RDFP.mediaType, "application/rdf+xml");

                model.add(model.getResource(graphResource.getGraphPath()), RDFP.alias, model.getResource(""));
            }
        }
        return model;
    }

    private void copyRepresentationDirectory(PrintWriter report, Model inputConf, Model outputConf) throws MojoExecutionException {

        // check that every file is available at the representation path, or if it is a blank node, at every resource that is represented by this representation.
        // iterate on files.
        List<Statement> list = inputConf.listStatements(null, ONTOP.filePath, (RDFNode) null).toList();
        for (Statement s : list) {
            inputConf.remove(s);
            RDFNode filePathNode = s.getObject();
            if (!filePathNode.isLiteral()) {
                getLog().warn("representation " + filePathNode + " should be described by a literal in config.ttl");
                continue;
            }
            String filePath = filePathNode.asLiteral().getLexicalForm();
            File file = new File(inputDirectory, filePath);
            if (!file.exists()) {
                getLog().warn("file " + file + " should exist.");
                continue;
            }
            String ext = "." + FilenameUtils.getExtension(file.getName());

            Resource representation = s.getSubject();
            Resource newRepresentation = representation;

            // check representation if object of a rdfp:representedBy predicate
            if (!inputConf.contains(null, RDFP.representedBy, representation)) {
                report.println("ignoring representation of " + filePath + ", surely because it is a rdfp:Graph, a ontop:Ontology or a ontop:OntologySet");
                continue;
            }

            if (representation.isURIResource()) {
                if (!representation.getURI().endsWith(ext)) {
                    report.println("representation " + representation + " should end with " + ext);
                    continue;
                } else {
                    copy(report, file, representation.getURI());
                }
            } else {
                List<Statement> list2 = inputConf.listStatements(null, RDFP.representedBy, representation).toList();
                for (Statement s2 : list2) {
                    Resource parentRepresentation = s2.getSubject();
                    if (parentRepresentation.isURIResource()) {
                        // then infer URI of blank node representation: the URI of s2 + the extension of file
                        String representationUri = parentRepresentation.getURI() + "." + FilenameUtils.getExtension(file.getName());
                        newRepresentation = inputConf.getResource(representationUri);
                        copy(report, file, representationUri);
                    }
                }
            }
            copy(report, inputConf, outputConf, representation, newRepresentation);
            report.println("copying triples that mention the subject of " + filePath + ", and replace this resource by " + newRepresentation.getURI());
        }
    }

    private void copy(PrintWriter report, File file, String uri) {
        File dest = null;
        try {
            if (!uri.startsWith(base) || uri.contains("#")) {
                getLog().warn("representation " + uri + " should start by " + base + " and should not contain #");
            }
            dest = new File(ontopDirectory, uri.substring(base.length()));
            if (dest.exists()) {
                getLog().warn("destination file " + dest + " already exists and will be overwritten");
            }
            FileUtils.forceMkdir(dest.getParentFile());
            FileUtils.copyFile(file, dest);
        } catch (IOException ex) {
            getLog().warn("could not copy " + file + " to " + dest);
        }
        report.println("copied " + file + " to " + uri);
    }

    private void copy(PrintWriter report, Model inputModel, Model outputModel, Resource old, Resource newResource) {
        List<Statement> list = inputModel.listStatements(old, null, (RDFNode) null).toList();
        for (Statement stmt : list) {
            outputModel.add(newResource, stmt.getPredicate(), stmt.getObject());
        }

        list = inputModel.listStatements(null, null, old).toList();
        for (Statement stmt : list) {
            outputModel.add(stmt.getSubject(), stmt.getPredicate(), newResource);
        }
    }

    private void copyGeneratedRepresentationDirectory(PrintWriter report) throws MojoExecutionException {
        try {
            FileUtils.copyDirectory(ontopDirectory, finalDirectory, true);
        } catch (Exception ex) {
            throw new MojoExecutionException("Error while copying the generated representation directory to " + finalDirectory, ex);
        }
    }
}
