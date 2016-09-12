# Documenting and exposing ontologies with ONTOP 

Project ONTOP eases the exposition of ontologies and other resources in Maven java web applications.

Ontologies and other resources to be exposed are placed in folder `src/main/ontop`, along with a RDF configuration file `config.ttl` that describes the different resources and their representations, using the [RDF Presentation vocabulary](https://w3id.org/rdfp/). Then,

- maven plugin `ontop-maven-plugin` checks the quality of the ontologies, pre-generate their documentation, and potentially other RDF representation;
- project `ontop-jersey` exposes these resources on the web.

The documentation generation for the ontologies consists of two steps:

1. apply a [STTL aka SPARQL-Template](https://ns.inria.fr/sparql-template/) [lowering rule](https://w3id.org/ontop/documentation/loweringRule) to generate the documentation in the markdown syntax;
1. use the `maven-site-plugin` to generate the HTML documents.

In the RDF Turtle snippets below, we use the following namespace prefixes:

```
@prefix dc: <http://purl.org/dc/terms/> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix owl: <http://www.w3.org/2002/07/owl#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix vann: <http://purl.org/vocab/vann/> .
@prefix voaf: <http://purl.org/vocommons/voaf#> .
@prefix vs: <http://www.w3.org/2003/06/sw-vocab-status/ns#> .
@prefix foaf: <http://xmlns.com/foaf/0.1/>.
@prefix cc: <http://creativecommons.org/ns#>.
@prefix ontop: <https://w3id.org/ontop/>.
@prefix rdfp: <https://w3id.org/rdfp/>.
```


## Exposing an ontology

The set of ontologies to be exposed can be specified in `config.ttl` using an instance of class `ontop:OntologySet` linked to a regular expression by predicate `rdfp:fileSelector`, or one by one using an instance of class `ontop:Ontology` linked to a file path by predicate `rdfp:filePath`.


```
[] a ontop:OntologySet ;
  ontop:fileSelector "[A-Za-z]+-[0-9]+\\.[0-9]+\\.ttl" .

[] a ontop:Ontology ; 
  ontop:filePath "ontop-1.0.ttl" .
```

Then `ontop-maven-plugin` will check the quality and generate documentation for all the RDF files that match the given regular expression. 

Using ONTOP, the ontology editors are encouraged to use the markdown syntax when documenting the ontology (with predicate `dc:description`) and its resources (with predicates `rdfs:comment`), as this code will be properly formatted in HTML.

Let `base` be the maven `project.url` parameter specified in the POM. `base` is the base URI.

For now, `ontop-maven-plugin` checks the following items: 

- The file name must be of the form `NAME-MAJOR.MINOR.ttl`.
- There must be a unique instance of `owl:Ontology`, it must also be an instance of `voaf:Vocabulary`. Its URI must start with `base`. 
- The ontology must be annotated with properties `dc:title`, `dc:description`, `dc:issued`, `dc:creator`, `cc:license`.
- It must have a `vann:preferredNamespacePrefix`, a `vann:preferredNamespaceUri` that starts with `base`.
- It must have a `owl:versionIRI` that starts with `base`.
- It must have a `owl:versionInfo` that starts with `v{MAJOR}.{MINOR}`.
- Other resources that this ontology defines should be linked to the ontology with property `rdfs:isDefinedBy`.

Generated report file `target/ontop.ttl` lists the problems in every analyzed ontology document.


Then, the ontology document is accessible at its version IRI as HTML, Turtle, or RDF/XML. The canonical locations of the representations are `versionIRI.html` for the HTML, `versionIRI.ttl` for the Turtle, `versionIRI.rdf` for the RDF/XML.
If it has the highest version in the ontology series, then it is also accessible at its IRI as HTML, Turtle, or RDF/XML. It is also accessible in HTML at `ontologyIRI.html`, but the canonical location of the representation is still `versionIRI.html`.

Finally, if the ontology IRI is exactly `base`, then the latest version is accessible at `index`, `index.html`, `index.ttl`, and `index.rdf`. 


## Exposing a RDF graph


An RDF graph to be exposes must be specified in `config.ttl` using an instance of class `rdfp:Graph` linked to a file path by predicate `rdfp:filePath`.

```
<example/description> a rdfp:Graph ; 
  ontop:filePath "example-description.ttl" .
```

then file with local path `"example-description.ttl"` will be accessible as Turtle or RDF/XML at `<example/description>`. The canonical locations of the representations are `<example/description.ttl>` for the Turtle, and `<example/description.rdf>` for the RDF/XML.


## Exposing other resources


Other resources with one/several representations to be exposed must be specified in `config.ttl` using an instance of class `rdfp:Resource` linked to one/several instances of `rdfp:Representation` using predicate `rdfp:representedBy`. Then these representations specify the media type of the representation, and the local file.


```
<example/description> a rdfp:Resource ; 
  rdfp:representedBy [ ontop:filePath "description.html" ; rdfp:mediaType "text/html" ] ;
  rdfp:representedBy [ ontop:filePath "description.txt" ;  rdfp:mediaType "text/plain" ] .
```

Then, in addition to the previously mentioned Turtle and RDF/XML representations for resource `<example/description>`, two additional representations are now exposed: an HTML and a plain text representations. The canonical locations of the representations are `<example/description.html>` for the HTML, and `<example/description.txt>` for the plain text.

# Using ONTOP

Binaries, sources and documentation for `ontop` are available for download at [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Contop%22). 
To use it in your Maven project, add the ontop plugin, the maven site plugin, and the ontop dependency to your Maven project file ( `*.pom` file), like is done in [this project](https://github.com/thesmartenergy/ontop/blob/master/ontop-website/pom.xml). 
