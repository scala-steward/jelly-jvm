# Jelly-JVM

**Jelly-JVM** is an implementation of the Jelly serialization format and the gRPC streaming protocol for the Java Virtual Machine (JVM), written in Scala 3. The supported RDF libraries are [Apache Jena](https://jena.apache.org/) and [Eclipse RDF4J](https://rdf4j.org/).

This collection of libraries aims to provide the full stack of utilities for fast and scalable RDF streaming with the [Jelly protocol]({{ proto_link( 'specification' ) }}).

!!! tip "Getting started with plugins – no code required"

    See the **[Getting started guide](getting-started-plugins.md)** for a quick way to use Jelly with your Apache Jena or RDF4J application without writing any code.

!!! tip "Getting started for developers"

    If you want to use the full feature set of Jelly-JVM in your code, see the **[Getting started guide](getting-started-devs.md)** for developers.

TODO: versioning – point to the version selector in the navbar 

{{ jvm_version() }}

{{ proto_version() }} 

## Library modules

The implementation is split into a few modules that can be used separately:

- `jelly-core` – implementation of the [Jelly serialization format]({{ proto_link( 'specification/serialization' ) }}) (using the [scalapb](https://scalapb.github.io/) library), along with generic utilities for converting the deserialized RDF data to/from the representations of RDF libraries (like Apache Jena or RDF4J). 
    - {{ module_badges('core') }}

- `jelly-jena` – conversions and interop code for the [Apache Jena](https://jena.apache.org/) library.
    - {{ module_badges('jena') }}

- `jelly-rdf4j` – conversions and interop code for the [RDF4J](https://rdf4j.org/) library.
    - {{ module_badges('rdf4j') }}

- `jelly-stream` – utilities for building [Reactive Streams](https://www.reactive-streams.org/) of RDF data (based on Pekko Streams). Useful for integrating with gRPC or other streaming protocols (e.g., Kafka, MQTT).
    - {{ module_badges('stream') }}

- `jelly-grpc` – implementation of a gRPC client and server for the [Jelly gRPC streaming protocol]({{ proto_link( 'specification/streaming' ) }}).
    - {{ module_badges('grpc') }}

## TODO: PLUGIN JARS

## Compatibility

The Jelly-JVM implementation is compatible with Java 11 and newer. Java 11, 17, and 21 are tested in CI and are guaranteed to work. Jelly is built with [Scala 3 LTS releases](https://www.scala-lang.org/blog/2022/08/17/long-term-compatibility-plans.html).

The following table shows the compatibility of the Jelly-JVM implementation with other libraries:

| Jelly | Scala       | Java | RDF4J | Apache Jena | Apache Pekko |
| ----- | ----------- | ---- | ----- | ----------- | ------------ |
| **1.0.x (current)** | 3.3.x (LTS) | 11+  | 4.x.y | 4.x.y       | 1.0.x        |

## Documentation

Below is a list of all documentation pages about Jelly-JVM. You can also browse the Javadoc using the badges in the module list above. The documentation uses examples written in Scala, but the libraries can be used from Java as well.

- [Getting started with Jena/RDF4J plugins](getting-started-plugins.md) – how to use Jelly-JVM as a plugin for Apache Jena or RDF4J, without writing any code.
- [Getting started for developers](getting-started-devs.md) – how to use Jelly-JVM in code.
- User guide
    - [Apache Jena integration](user/jena.md)
    - [RDF4J integration](user/rdf4j.md)
    - [Reactive streaming](user/reactive.md)
    - [gRPC](user/grpc.md)
    - [Userful utilities](user/utilities.md)
- Developer guide
    - [Releases](dev/releases.md)
    - [Implementing Jelly for other libraries](dev/implementing.md)
- [Main Jelly website]({{ proto_link( '' ) }}) – including the Jelly protocol specification and explanation of the the various stream types.
