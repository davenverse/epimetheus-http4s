ThisBuild / tlBaseVersion := "0.7" // your current series x.y

ThisBuild / organization := "io.chrisdavenport"
ThisBuild / organizationName := "Christopher Davenport"
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("christopherdavenport", "Christopher Davenport")
)

ThisBuild / tlCiReleaseBranches := Seq()

ThisBuild / crossScalaVersions := Seq("2.13.18", "3.3.8")
ThisBuild / scalaVersion := "2.13.18"
ThisBuild / versionScheme := Some("early-semver")

val catsV = "2.13.0"
val catsEffectV = "3.7.1"
val shapelessV = "2.3.7"
val fs2V = "3.14.0"
val http4sV = "0.23.37"
lazy val epimetheusV = "0.5.0"
val specs2V = "4.12.3"


lazy val `epimetheus-http4s` = tlCrossRootProject
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
  .enablePlugins(TypelevelSitePlugin)
  .settings(
    laikaTheme := tlSiteHelium.value.site
      .topNavigationBar(
        homeLink = laika.helium.config.IconLink.internal(laika.ast.Path.Root / "index.md", laika.helium.config.HeliumIcon.home)
      )
      .build
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

      "org.typelevel" %% "munit-cats-effect" % "2.2.1" % Test,
    )
)
