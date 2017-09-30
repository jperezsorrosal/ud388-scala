import sbt.Resolver

name := "ud388-scala"

version := "0.1"

scalaVersion := "2.12.3"

val scalaTestVersion    = "3.0.1"
val akkaHttpVersion     = "10.0.5"
val slickVersion        = "3.2.1"
val h2DatabaseVersion   = "1.4.196"
val logBackVersion      = "1.1.2"
val circeVersion        = "0.8.0"

resolvers += Resolver.bintrayRepo("hseeberger", "maven")

libraryDependencies ++= List(
  "org.scalactic"       %% "scalactic"          % scalaTestVersion
  ,"org.scalatest"      %% "scalatest"          % scalaTestVersion    % "test"

  ,"com.typesafe.akka"  %% "akka-http-core"     % akkaHttpVersion
  ,"com.typesafe.akka"  %% "akka-http"          % akkaHttpVersion
  ,"com.typesafe.akka"  %% "akka-http-testkit"  % akkaHttpVersion

  ,"com.typesafe.slick" %% "slick"              % slickVersion
  ,"com.h2database"     % "h2"                  % h2DatabaseVersion

  ,"ch.qos.logback"     % "logback-classic"     % logBackVersion

  ,"de.heikoseeberger"  %% "akka-http-circe"    % "1.18.0"

  ,"commons-codec"      % "commons-codec"       % "1.10"
  ,"com.auth0"          % "java-jwt"            % "3.2.0"
)


libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

scalacOptions ++= Seq("-unchecked", "-deprecation")

parallelExecution in Test := false