
val slickVersion = "3.2.1"
val akkaVersion = "2.5.4"
val playVersion = "2.6.10"

lazy val shareSettings = Seq(
  organization := "com.github.jccode",
  scalaVersion := "2.12.6",

  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.7.1",
    "com.typesafe" % "config" % "1.3.1",
  )
)

lazy val root = (project in file("."))
  .settings(shareSettings)
  .settings(
    name := "dot-ltd",
    version := "0.1",
  )
  .aggregate(verification)


lazy val verification = (project in file("verification"))
  .settings(shareSettings)
  .settings(
    name := "verification",
    version := "0.1",
  )
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.slick" %% "slick" % slickVersion,
      "com.typesafe.slick" %% "slick-codegen" % slickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
      "com.github.jccode" %% "slickx" % "0.1",
      "mysql" % "mysql-connector-java" % "5.1.36",

      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
      "com.lightbend.akka" %% "akka-stream-alpakka-slick" % "0.20",

      "com.typesafe.akka" %% "akka-http" % "10.0.9",
      "com.typesafe.akka" %% "akka-http-testkit" % "10.0.9" % Test,
      "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.5",

      "com.typesafe.play" %% "play-json" % playVersion
    )
  )
  .settings(
    slick := slickCodeGenTask.value,
    sourceGenerators in Compile += slick
  )



lazy val slick = taskKey[Seq[File]]("gen-tables")  // register manual sbt command

// Define slick code gen task implemention
lazy val slickCodeGenTask = Def.task {
  val (dir, cp, r, s) = ((sourceManaged in Compile).value, (dependencyClasspath in Compile).value, (runner in Compile).value, streams.value)
  val pkg = "com.github.jccode.dotltd.dao"
  val slickProfile = "slick.jdbc.MySQLProfile"
  val jdbcDriver = "com.mysql.jdbc.Driver"
  val url = "jdbc:mysql://localhost:3306/dotltd?useUnicode=true&characterEncoding=UTF-8"
  val user = "jc"
  val password = "jc"
  val included = ""
  val excluded = ""
  r.run("com.github.jccode.slickx.codegen.CodeGenerator", cp.files, Array(slickProfile, jdbcDriver, url, dir.getPath, pkg, user, password, "true", "com.github.jccode.slickx.codegen.CodeGenerator", included, excluded), s.log)
  val outputFile = dir / pkg.replace(".", "/") / "Tables.scala"
  Seq(outputFile)
}
