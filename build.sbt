// The simplest possible sbt build file is just one line:

scalaVersion := "2.12.8"
//scalaVersion := "0.13.0-RC1"
// That is, to create a valid sbt build, all you've got to do is define the
// version of Scala you'd like your project to use.

// ============================================================================

// Lines like the above defining `scalaVersion` are called "settings" Settings
// are key/value pairs. In the case of `scalaVersion`, the key is "scalaVersion"
// and the value is "2.12.8"

// It's possible to define many kinds of settings, such as:

name := "hemi-app"
organization := "com.selmank"
version := "1.3.2"

updateOptions := updateOptions.value.withCachedResolution(true)

libraryDependencies += "org.typelevel" %% "cats-core" % "1.6.0"
libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.2.0"
libraryDependencies += "org.scalaxb" %% "scalaxb" % "1.7.1"
libraryDependencies += "org.scala-lang.modules" %% "scala-swing" % "2.1.0"
libraryDependencies += "org.scalaj" %% "scalaj-http" % "2.4.1"
val circeVersion = "0.11.0"
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser",
  "io.circe" %% "circe-optics"
).map(_ % circeVersion)
libraryDependencies += "org.gnieh" %% "diffson-circe" % "3.1.1"


/*
  Java Libraries
*/

resolvers += "Spring Repository" at "http://repo.spring.io/plugins-release/"
libraryDependencies += "com.bulenkov" % "darcula" % "2018.2"














