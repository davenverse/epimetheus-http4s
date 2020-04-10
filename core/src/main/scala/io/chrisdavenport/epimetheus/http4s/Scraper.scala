package io.chrisdavenport.epimetheus.http4s

import cats._
import cats.implicits._
import cats.effect._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import io.chrisdavenport.epimetheus.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import org.http4s.headers.`Content-Type`

object Scraper {

  lazy val prometheusContentType: `Content-Type` = `Content-Type`
    .parse(TextFormat.CONTENT_TYPE_004)
    .leftMap(_ => `Content-Type`(MediaType.text.plain))
    .merge

  private def makeResponse[F[_], T](
    b: T
  )(implicit w: EntityEncoder[F, T]
  ) =
    Response[F](Status.Ok).withEntity(b).withContentType(prometheusContentType)

  def response[F[_]: Functor](
    cr: CollectorRegistry[F]
  ): F[Response[F]] =
    cr.write004.map(makeResponse(_))

  def response[F[_]: Applicative, T[_]: Foldable](
    c: T[CollectorRegistry[F]]
  ): F[Response[F]] =
    c.foldMapA(_.write004).map(makeResponse(_))

  def responsePar[F[_]: Applicative, T[_]: Foldable](
    c: T[CollectorRegistry[F]]
  )(implicit p: Parallel[F]
  ): F[Response[F]] =
    c.parFoldMapA(_.write004).map(makeResponse(_))

  def routes[F[_]: Sync](
    cr: CollectorRegistry[F]
  ): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}; import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "metrics" => response(cr)
    }
  }

  def routes[F[_]: Sync, T[_]: Foldable](
    c: T[CollectorRegistry[F]]
  ): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}; import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "metrics" => response(c)
    }
  }

  def routesPar[F[_]: Sync, T[_]: Foldable](
    c: T[CollectorRegistry[F]]
  )(implicit p: Parallel[F]
  ): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}; import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "metrics" => responsePar(c)
    }
  }
}