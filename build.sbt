ThisBuild / crossScalaVersions := Seq("2.12.14", "2.13.7", "3.0.1")

val catsV = "2.6.1"
val catsEffectV = "3.1.1"
val shapelessV = "2.3.7"
val fs2V = "3.0.4"
val http4sV = "0.23.0"
lazy val epimetheusV = "0.5.0-M2"
val specs2V = "4.12.3"


lazy val `epimetheus-http4s` = project.in(file("."))
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .aggregate(core, push)

lazy val core = project.in(file("core"))
  .settings(
    name := "epimetheus-http4s",
  ).settings(sharedDeps)

lazy val push = project.in(file("pushgateway"))
.settings(
  name := "epimetheus-http4s-pushgateway",
  libraryDependencies ++= Seq(
    "org.http4s" %% "http4s-client" % http4sV
  )
).settings(sharedDeps)

lazy val site = project.in(file("site"))
  .dependsOn(core)
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .enablePlugins(DavenverseMicrositePlugin)
  .settings(
    micrositeDescription := "Epimetheus Http4s Metrics",
  )

lazy val sharedDeps = Seq(
  libraryDependencies ++= Seq(
      "org.typelevel"               %% "cats-core"                  % catsV,
      "org.typelevel"               %% "cats-effect"                % catsEffectV,

      "co.fs2"                      %% "fs2-core"                   % fs2V,
      "co.fs2"                      %% "fs2-io"                     % fs2V,

      "org.http4s"                  %% "http4s-core"                % http4sV,
      "org.http4s"                  %% "http4s-dsl"                 % http4sV,

      "io.chrisdavenport"           %% "epimetheus"                 % epimetheusV,

      "org.typelevel" %% "munit-cats-effect-3" % "1.0.5" % Test,
    )
)
