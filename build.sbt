
scalaVersion := "2.12.8"

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














