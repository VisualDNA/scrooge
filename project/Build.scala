import sbt._
import Keys._
//import bintray.Plugin._
//import bintray.Keys._
import com.typesafe.sbt.SbtSite.site
import com.typesafe.sbt.site.SphinxSupport.Sphinx
import net.virtualvoid.sbt.graph.Plugin.graphSettings // For dependency-graph
import sbtassembly.Plugin._
import AssemblyKeys._

object Scrooge extends Build {
  val libVersion = "3.17.0-VDNA-11"
  val utilVersion = "6.22.0"
  val finagleVersion = "6.22.0"

  def util(which: String) = "com.twitter" %% ("util-"+which) % utilVersion
  def finagle(which: String) = "com.twitter" %% ("finagle-"+which) % finagleVersion

  val compileThrift = TaskKey[Seq[File]](
    "compile-thrift", "generate thrift needed for tests")

  val thriftSettings: Seq[Setting[_]] = Seq(
    compileThrift <<= (
      streams,
      baseDirectory,
      fullClasspath in Runtime,
      sourceManaged
    ) map { (out, base, cp, outputDir) =>
      val cmd = "%s %s %s %s".format(
        (base / "src" / "scripts" / "gen-test-thrift").getAbsolutePath,
        cp.files.absString,
        outputDir.getAbsolutePath,
        base.getAbsolutePath)

      out.log.info(cmd)
      cmd ! out.log

      (outputDir ** "*.scala").get.toSeq ++
      (outputDir ** "*.java").get.toSeq
    },
    sourceGenerators <+= compileThrift
  )

  val sharedSettings = Seq(
    version := libVersion,
    organization := "com.twitter",
    crossScalaVersions := Seq("2.10.4"),
    scalaVersion := "2.10.4",

    resolvers ++= Seq(
      "sonatype-public" at "https://oss.sonatype.org/content/groups/public",
      "VisualDNA Releases" at "https://maven.visualdna.com/nexus/content/repositories/releases"
    ),
    
    credentials ++= {
 val sonatype = ("Sonatype Nexus Repository Manager", "maven.visualdna.com")
 def loadMavenCredentials(file: java.io.File): Seq[Credentials] = {
 xml.XML.loadFile(file) \ "servers" \ "server" map (s => {
 val host = (s \ "id").text
 val realm = sonatype._1
 val hostToUse = "maven.visualdna.com"
 Credentials(realm, hostToUse, (s \ "username").text, (s \ "password").text)
 })
 }
 val mavenCredentials = Path.userHome / ".m2" / "settings.xml"
 loadMavenCredentials(mavenCredentials.asFile)
 },


    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "2.2.2" % "test",
      "junit" % "junit" % "4.10" % "test" exclude("org.mockito", "mockito-all")
    ),
    resolvers += "twitter-repo" at "http://maven.twttr.com",
    resolvers += Resolver.mavenLocal,

    scalacOptions ++= Seq("-encoding", "utf8"),
    scalacOptions += "-deprecation",
    javacOptions ++= Seq("-source", "1.6", "-target", "1.6", "-Xlint:unchecked"),
    javacOptions in doc := Seq("-source", "1.6"),

    // Sonatype publishing
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    publishMavenStyle := true,
    pomExtra := (
      <url>https://github.com/twitter/scrooge</url>
      <licenses>
        <license>
          <name>Apache License, Version 2.0</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0</url>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:twitter/scrooge.git</url>
        <connection>scm:git:git@github.com:twitter/scrooge.git</connection>
      </scm>
      <developers>
        <developer>
          <id>twitter</id>
          <name>Twitter Inc.</name>
          <url>https://www.twitter.com/</url>
        </developer>
      </developers>),
    publishTo <<= version { (v: String) =>
      val nexus = "https://maven.visualdna.com/nexus/content/repositories/"
        if (v.trim.endsWith("SNAPSHOT"))
          Some("snapshots" at nexus + "snapshots")
        else
          Some("releases" at nexus + "releases")
    },

    resourceGenerators in Compile <+=
      (resourceManaged in Compile, name, version) map { (dir, name, ver) =>
        val file = dir / "com" / "twitter" / name / "build.properties"
        val buildRev = Process("git" :: "rev-parse" :: "HEAD" :: Nil).!!.trim
        val buildName = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date)
        val contents = (
          "name=%s\nversion=%s\nbuild_revision=%s\nbuild_name=%s"
        ).format(name, ver, buildRev, buildName)
        IO.write(file, contents)
        Seq(file)
      }
  ) ++ graphSettings

  val jmockSettings = Seq(
    libraryDependencies ++= Seq(
      "org.jmock" % "jmock" % "2.4.0" % "test",
      "cglib" % "cglib" % "2.1_3" % "test",
      "asm" % "asm" % "1.5.3" % "test",
      "org.objenesis" % "objenesis" % "1.1" % "test",
      "org.mockito" % "mockito-core" % "1.9.5" % "test"
    )
  )

  lazy val scrooge = Project(
    id = "scrooge",
    base = file("."),
    settings = Project.defaultSettings ++
      sharedSettings
  ).aggregate(
    scroogeGenerator, scroogeCore,
    scroogeRuntime,
    scroogeScalaz
  )

  lazy val scroogeGenerator = Project(
    id = "scrooge-generator",
    base = file("scrooge-generator"),
    settings = Project.defaultSettings ++
      inConfig(Test)(thriftSettings) ++
      sharedSettings ++
      assemblySettings ++
      jmockSettings
  ).settings(
    name := "scrooge-generator",
    libraryDependencies ++= Seq(
      util("core") exclude("org.mockito", "mockito-all"),
      util("codec") exclude("org.mockito", "mockito-all"),
      "org.apache.thrift" % "libthrift" % "0.9.3-VDNA-3",
      "com.github.scopt" %% "scopt" % "2.1.0",
      "com.novocode" % "junit-interface" % "0.8" % "test->default" exclude("org.mockito", "mockito-all"),
      "com.github.spullara.mustache.java" % "compiler" % "0.8.12",
      "org.codehaus.plexus" % "plexus-utils" % "1.5.4",
      "com.google.code.findbugs" % "jsr305" % "1.3.9",
      "commons-cli" % "commons-cli" % "1.2",
      "org.scalaz" %% "scalaz-concurrent" % "7.1.0" % "test",
      finagle("core") exclude("org.mockito", "mockito-all"),
      finagle("thrift") % "test"
    ),
    test in assembly := {},  // Skip tests when running assembly.
    mainClass in assembly := Some("com.twitter.scrooge.Main")
  ).dependsOn(scroogeRuntime % "test", scroogeScalaz % "test")

  lazy val scroogeCore = Project(
    id = "scrooge-core",
    base = file("scrooge-core"),
    settings = Project.defaultSettings ++
      sharedSettings
  ).settings(
    name := "scrooge-core",
    libraryDependencies ++= Seq(
      "org.apache.thrift" % "libthrift" % "0.9.3-VDNA-3" % "provided"
    ),
    crossScalaVersions += "2.11.2"
  )

  lazy val scroogeRuntime = Project(
    id = "scrooge-runtime",
    base = file("scrooge-runtime"),
    settings = Project.defaultSettings ++
      sharedSettings
  ).settings(
    name := "scrooge-runtime",
    libraryDependencies ++= Seq(
      finagle("thrift")
    )
  ).dependsOn(scroogeCore)

  lazy val scroogeScalaz = Project(
    id = "scrooge-scalaz",
    base = file("scrooge-scalaz"),
    settings = Project.defaultSettings ++
      sharedSettings
  ).settings(
    name := "scrooge-scalaz",
    libraryDependencies ++= Seq(
      "org.apache.thrift" % "libthrift" % "0.9.3-VDNA-3",
      "org.scalaz" %% "scalaz-concurrent" % "7.1.0",
      "io.kamon" %% "kamon-core" % "0.4.0",
      "org.slf4j" % "slf4j-api" % "1.7.10"
    ),
    crossScalaVersions += "2.11.2"
  ).dependsOn(scroogeCore)

}
