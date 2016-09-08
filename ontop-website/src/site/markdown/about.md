# ONTOP - Ontology Platform

Project ONTOP includes:

- a [STTL aka SPARQL-Template](https://w3id.org/ontop/documentation/loweringRule) transformation that enables to automatically generate an HTML documentation for any ontology;
- a Maven plugin that automatically controls the quality of the ontologies to be published;
- a Maven project that serves the ontologies and other resources following the Web architecture design principles.


## What this project contains

This project contains the sources for the Ontology Platform project:

- `ontop-core` defines core classes that are used in all other projects
- `ontop-maven-plugin` is a maven plugin bound to the generate-resources lifecycle phase. It checks the quality of the ontologies, generates the html documentation for the ontologies, and the site configuration file that is used on runtime to best serve the different ontologies.
- `ontop-jersey` is an extension of Jersey that exposes the ontologies, their documentation, and other resources.
- `ontop-website` contains the sources of the project website at https://w3id.org/ontop/

Binaries, sources and documentation for `ontop` are available for download at [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Contop%22). 
To use it in your Maven project, add the ontop plugin, the maven site plugin, and the ontop dependency to your Maven project file ( `*.pom` file), like the POM of this project.

(more doc soon...)
 

## Used in

This project is used in the following related projects:

- [RDFP - RDF Presentation project](https://w3id.org/rdfp/);
- [PEP - Process Execution Platform project](https://w3id.org/pep/);
- [Lindt - Linked Datatypes project](https://w3id.org/lindt/);
- [SEAS - Smart Energy Aware Systems ontology ](https://w3id.org/seas/);
- [CNR Smart Charging Provider SEAS pilot platform](http://cnr-seas.cloudapp.net/scp/).
