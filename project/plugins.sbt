addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.7")
addSbtPlugin("org.apache.pekko" % "pekko-grpc-sbt-plugin" % "1.0.2")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.12")
addDependencyTreePlugin

lazy val scalapbV = "0.11.13"

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % scalapbV
