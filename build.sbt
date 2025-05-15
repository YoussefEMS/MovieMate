val scala3Version = "3.7.0"

lazy val root = project
  .in(file("."))
  .settings(
    name := "main",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.0.0" % Test,
      "com.github.tototoshi" %% "scala-csv" % "1.3.10",
      "com.typesafe.play" %% "play-json" % "2.10.0-RC7"
    )

  )
