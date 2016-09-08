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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
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
@Mojo(name = "generate-resources", requiresProject = true, threadSafe = false, defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
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
    @Parameter(property = "ontologiesDirectory", defaultValue = "${basedir}/src/ontologies")
    private File ontologiesDirectory;

    /**
     * location of the representations.
     */
    @Parameter(property = "representationsDirectory", defaultValue = "${basedir}/src/representations")
    private File representationsDirectory;

    /**
     * location of the doxia site descriptor.
     */
    @Parameter(property = "siteDirectory", defaultValue = "${basedir}/src/site")
    private File siteDirectory;

    /**
     * location of the fake site.
     */
    @Parameter(property = "fakeSiteDirectory", defaultValue = "${project.build.directory}/fake-site")
    private File fakeSiteDirectory;

    /**
     * location of the fake site.
     */
    @Parameter(property = "siteOutputDirectory", defaultValue = "${project.reporting.outputDirectory}")
    private File siteOutputDirectory;

    /**
     * location of the fake site.
     */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private File buildDirectory;

    private File gRepresentationsDirectory;
    private File generatedResources;
    private File reportFile;

    @Override
    public void execute() throws MojoExecutionException {

        gRepresentationsDirectory = new File(buildDirectory, "generated-representations");
        generatedResources = new File(siteOutputDirectory, "WEB-INF/classes/_ontop");
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
            OntologiesMap map = parseOntologies(out);
            generateHtmlDocumentation(out, map);
            generateRdfRepresentations(out, map);
            Model generatedConf = generateConf(out, map);

            copyGeneratedRepresentationDirectory(out);
            Model conf = copyRepresentationDirectory(out);

            try {
                generatedConf.add(conf);
                File ontopConfig = new File(generatedResources, "ontop-config.ttl");
                generatedConf.write(new FileOutputStream(ontopConfig), "TTL", base);
            } catch (FileNotFoundException ex) {
                throw new MojoExecutionException("error while serializing configuration file", ex);
            }
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
            FileUtils.forceMkdir(gRepresentationsDirectory);
            FileUtils.cleanDirectory(gRepresentationsDirectory);
        } catch (final IOException ioe) {
            throw new MojoExecutionException("Failed to generate directory: " + gRepresentationsDirectory, ioe);
        }

        try {
            FileUtils.forceMkdir(generatedResources);
        } catch (final IOException ioe) {
            throw new MojoExecutionException("Failed to generate directory: " + generatedResources, ioe);
        }
    }

    private OntologiesMap parseOntologies(PrintWriter report) throws MojoExecutionException {
        Collection<File> ontologyFiles = FileUtils.listFiles(ontologiesDirectory, new String[]{"ttl", "rdf"}, true);
        OntologiesMap map = new OntologiesMap();
        VersionedOntologyFactory factory = new VersionedOntologyFactory(base, ontologiesDirectory);

        for (final File ontologyFile : ontologyFiles) {
            VersionedOntology ontology;
            try {
                ontology = factory.parse(ontologyFile);
                report.write("new ontology: filePath: " + ontology.getFilePath() + ", ontologyPath: " + ontology.getOntologyPath() + ", versionPath: " + ontology.getVersionPath() + "\n");
                map.registerOntology(ontology);
            } catch (OntopException ex) {
                report.write("error while analyzing file " + ontologyFile + ":\n " + ex.getMessage() + "\n\n");
                getLog().warn("error while analyzing file " + ontologyFile, ex);
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

                String test = GenerateResourcesMojo.class.getClassLoader().getResource("generate-documentation.rul").toString();
                Transformer t = Transformer.create(g, test);
                String html = t.transform();

                String outputFileName = ontology.getVersionPath() + ".xhtml";
                File outputFile = new File(fakeSiteDirectory, "xhtml/" + outputFileName);
                FileUtils.forceMkdirParent(outputFile);
                FileOutputStream out = new FileOutputStream(outputFile);
                out.write(html.getBytes());
            } catch (Exception ex) {
                report.write("Failed to generate documentation for ontology: " + ontology.getVersionPath() + ":\n " + ex.getMessage() + "\n\n");
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
                        element(name("outputDirectory"), gRepresentationsDirectory.getAbsolutePath()),
                        element(name("reportPlugins"), 
                                element(name("plugin"), 
                                        element(name("groupId"), "org.apache.maven.plugins"),
                                        element(name("artifactId"), "maven-project-info-reports-plugin"),
                                        element(name("reports"), 
                                                element(name("report"),"summary"))))
                ),
                executionEnvironment(
                        mavenProject,
                        mavenSession,
                        pluginManager
                )
        );
        try {
            FileUtils.deleteDirectory(new File(gRepresentationsDirectory, "css"));
            FileUtils.deleteDirectory(new File(gRepresentationsDirectory, "fonts"));
            FileUtils.deleteDirectory(new File(gRepresentationsDirectory, "css"));
            FileUtils.deleteDirectory(new File(gRepresentationsDirectory, "images"));
            FileUtils.deleteDirectory(new File(gRepresentationsDirectory, "img"));
            FileUtils.deleteDirectory(new File(gRepresentationsDirectory, "js"));
        } catch (IOException ex) {
            throw new MojoExecutionException("unable to delete useless folders", ex);
        }
    }

    private void generateRdfRepresentations(PrintWriter report, OntologiesMap map) {
        for (VersionedOntology ontology : map.getOntologyResources().values()) {
            try {
                Model model = RDFDataMgr.loadModel(ontology.getFile().toURI().toString());

                String ttlPath = ontology.getVersionPath() + ".ttl";
                File ttl = new File(gRepresentationsDirectory, ttlPath);
                FileUtils.forceMkdirParent(ttl);
                if (FilenameUtils.getExtension(ontology.getFile().getName()).endsWith("ttl")) {
                    FileUtils.copyFile(ontology.getFile(), ttl);
                } else {
                    OutputStream out = new FileOutputStream(ttl);
                    model.write(out, "TTL", base);
                }

                String rdfxmlName = ontology.getVersionPath() + ".rdf";
                File rdfxml = new File(gRepresentationsDirectory, rdfxmlName);
                FileUtils.forceMkdirParent(rdfxml);
                OutputStream out = new FileOutputStream(rdfxml);
                model.write(out, "RDF/XML", base);

//                return new Representation(ONTOP.APPLICATION_RDFXML_TYPE, outputFile);
            } catch (Exception ex) {
                report.write("Failed to generate RDFXML for ontology: " + ontology.getVersionPath() + ":\n " + ex.getMessage() + "\n\n");
                getLog().warn("Failed to generate RDFXML for ontology: " + ontology.getVersionPath(), ex);
            }
        }
    }

    private Model generateConf(PrintWriter report, OntologiesMap map) {
        Model model = ModelFactory.createDefaultModel();
//        getLog().info(base + " " + RDF.uri + " " + RDFS.uri + " " + ONTOP.NS);
        model.setNsPrefix("", base);
        model.setNsPrefix("rdf", RDF.uri);
        model.setNsPrefix("rdfs", RDFS.uri);
        model.setNsPrefix("ontop", ONTOP.NS); 
        Map<String, String> redirections = map.getRedirections();
        for (String path : redirections.keySet()) {
            model.add(model.getResource(path), RDFS.isDefinedBy, model.getResource(redirections.get(path)));
        }
        Map<String, VersionedOntology> ontologyResources = map.getOntologyResources();
        for (String path : ontologyResources.keySet()) {
            VersionedOntology ontology = ontologyResources.get(path);

            report.write("Writing model for path=" + path + ", filePath: " + ontology.getFilePath() + ", ontologyPath: " + ontology.getOntologyPath() + ", versionPath: " + ontology.getVersionPath() + "\n");

            Resource graph = model.getResource(ontology.getVersionPath());
            model.add(graph, RDF.type, ONTOP.Graph);

            Resource ttl = model.getResource(ontology.getVersionPath() + ".ttl");
            model.add(graph, ONTOP.representedBy, ttl);
            model.add(ttl, RDF.type, ONTOP.Resource);
            model.add(ttl, ONTOP.mediaType, "text/turtle");

            Resource html = model.getResource(ontology.getVersionPath() + ".html");
            model.add(graph, ONTOP.representedBy, html);
            model.add(html, RDF.type, ONTOP.Resource);
            model.add(html, ONTOP.mediaType, "text/html");

            Resource rdf = model.getResource(ontology.getVersionPath() + ".rdf");
            model.add(graph, ONTOP.representedBy, rdf);
            model.add(rdf, RDF.type, ONTOP.Resource);
            model.add(rdf, ONTOP.mediaType, "application/rdf+xml");

            if (path.equals("index")) {
                model.add(model.getResource(ontology.getVersionPath()), ONTOP.alias, model.getResource(path));

                Resource r = model.getResource(path + ".ttl");
                model.add(ttl, ONTOP.alias, r);
                model.add(r, ONTOP.mediaType, "text/turtle");
                
                r = model.getResource(path + ".html");
                model.add(html, ONTOP.alias, r);
                model.add(r, ONTOP.mediaType, "text/html");

                r = model.getResource(path + ".rdf");
                model.add(rdf, ONTOP.alias, r);
                model.add(r, ONTOP.mediaType, "application/rdf+xml");
                
                model.add(model.getResource(ontology.getVersionPath()), ONTOP.alias, model.getResource(""));
            }
            if (path.equals(ontology.getOntologyPath())) {
                model.add(model.getResource(ontology.getVersionPath()), ONTOP.alias, model.getResource(path));

                Resource r = model.getResource(path + ".ttl");
                model.add(ttl, ONTOP.alias, r);
                model.add(r, ONTOP.mediaType, "text/turtle");
                
                r = model.getResource(path + ".html");
                model.add(html, ONTOP.alias, r);
                model.add(r, ONTOP.mediaType, "text/html");

                r = model.getResource(path + ".rdf");
                model.add(rdf, ONTOP.alias, r);
                model.add(r, ONTOP.mediaType, "application/rdf+xml");
            } 
        }
        try {
            File out = new File(gRepresentationsDirectory, "ontop-config.ttl");
            model.write(new FileOutputStream(out), "TTL", base);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(GenerateResourcesMojo.class.getName()).log(Level.SEVERE, null, ex);
        }
        return model;
    }

    private void copyGeneratedRepresentationDirectory(PrintWriter report) throws MojoExecutionException {
        try {
            FileUtils.copyDirectory(gRepresentationsDirectory, generatedResources, true);
        } catch (Exception ex) {
            throw new MojoExecutionException("Error while copying the generated representation directory to " + generatedResources, ex);
        }
    }

    private Model copyRepresentationDirectory(PrintWriter report) throws MojoExecutionException {
        // parsing the configuration file in representationsDirectory
        final Model conf = RDFDataMgr.loadModel(new File(representationsDirectory, "ontop-config.ttl").toURI().toString());

        // check that every file is available at the representation path, or if it is a blank node, at every resource that is represented by this representation.
        // iterate on files.
        List<Statement> list = conf.listStatements(null, ONTOP.filePath, (RDFNode) null).toList();
        for (Statement s : list) {
            conf.remove(s);
            RDFNode filePathNode = s.getObject();
            if (!filePathNode.isLiteral()) {
                getLog().warn("representation " + filePathNode + " should be described by a literal in ontop-conf.ttl");
                continue;
            }
            File file = new File(representationsDirectory, filePathNode.asLiteral().getLexicalForm());
            if (!file.exists()) {
                getLog().warn("file " + file + " should exist.");
                continue;
            }
            String ext = "." + FilenameUtils.getExtension(file.getName());

            Resource representation = s.getSubject();

            if (representation.isURIResource()) {
                if (!representation.getURI().endsWith(ext)) {
                    getLog().warn("representation " + representation + " should end with " + ext);
                } else {
                    copy(report, file, representation.getURI());
                }
            } else {
                List<Statement> list2 = conf.listStatements(null, ONTOP.representedBy, representation).toList();
                for (Statement s2 : list2) {
                    Resource parentRepresentation = s2.getSubject();
                    if (parentRepresentation.isURIResource()) {
                        // then infer URI of blank node representation: the URI of s2 + the extension of file
                        String representationUri = parentRepresentation.getURI() + "." + FilenameUtils.getExtension(file.getName());
                        Resource representationResource = conf.getResource(representationUri);
                        replace(report, conf, representation, representationResource);
                        copy(report, file, representationUri);
                    }
                }
            }

        }
        return conf;
    }

    private void copy(PrintWriter report, File file, String uri) {
        File dest = null;
        try {
            if (!uri.startsWith(base) || uri.contains("#")) {
                getLog().warn("representation " + uri + " should start by " + base + " and should not contain #");
            }
            dest = new File(generatedResources, uri.substring(base.length()));
            if (dest.exists()) {
                getLog().warn("destination file " + dest + " already exists and will be overwritten");
            }
            FileUtils.forceMkdirParent(dest);
            FileUtils.copyFile(file, dest);
        } catch (IOException ex) {
            getLog().warn("could not copy " + file + " to " + dest);
        }
    }

    private void replace(PrintWriter report, Model model, Resource old, Resource newResource) {
        List<Statement> list = model.listStatements(old, null, (RDFNode) null).toList();
        for (Statement stmt : list) {
            model.remove(stmt);
            model.add(newResource, stmt.getPredicate(), stmt.getObject());
        }

        list = model.listStatements(null, null, old).toList();
        for (Statement stmt : list) {
            model.remove(stmt);
            model.add(stmt.getSubject(), stmt.getPredicate(), newResource);
        }
    }

}
