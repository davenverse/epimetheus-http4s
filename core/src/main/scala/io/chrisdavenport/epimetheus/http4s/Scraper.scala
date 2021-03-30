package io.chrisdavenport.epimetheus.http4s

import cats._
import cats.implicits._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import io.chrisdavenport.epimetheus.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import org.http4s.Header.Raw
import org.typelevel.ci.CIString

object Scraper {

  lazy val prometheusContentTypeHeader = Raw(CIString("Content-Type"), TextFormat.CONTENT_TYPE_004)
  lazy val openMetricsContentTypeHeader = Raw(CIString("Content-Type"), TextFormat.CONTENT_TYPE_OPENMETRICS_100)

  private def makeResponse004[F[_], T](b: T)(implicit w: EntityEncoder[F, T]) =
    Response[F](Status.Ok).withEntity(b).putHeaders(prometheusContentTypeHeader)

  def response004[F[_]: Functor](cr: CollectorRegistry[F]): F[Response[F]] =
    cr.write004.map(makeResponse004(_))

  def response004[F[_]: Applicative, T[_]: Foldable](c: T[CollectorRegistry[F]]): F[Response[F]] =
    c.foldMapA(_.write004).map(makeResponse004(_))

  def responsePar004[F[_]: Applicative: Parallel, T[_]: Foldable](c: T[CollectorRegistry[F]]): F[Response[F]] =
    c.parFoldMapA(_.write004).map(makeResponse004(_))

  def routes004[F[_]: Monad](cr: CollectorRegistry[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}; import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "metrics" => response004(cr)
    }
  }

  def routes004[F[_]: Monad, T[_]: Foldable](c: T[CollectorRegistry[F]]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}; import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "metrics" => response004(c)
    }
  }

  def routesPar004[F[_]: Monad: Parallel, T[_]: Foldable](c: T[CollectorRegistry[F]]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}; import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "metrics" => responsePar004(c)
    }
  }

  private def makeResponseOpenMetrics100[F[_], T](b: T)(implicit w: EntityEncoder[F, T]) =
    Response[F](Status.Ok).withEntity(b).putHeaders(openMetricsContentTypeHeader)

  def responseOpenMetrics100[F[_]: Functor](cr: CollectorRegistry[F]): F[Response[F]] =
    cr.writeOpenMetrics100.map(makeResponseOpenMetrics100(_))

  def responseOpenMetrics100[F[_]: Applicative, T[_]: Foldable](c: T[CollectorRegistry[F]]): F[Response[F]] =
    c.foldMapA(_.writeOpenMetrics100).map(makeResponseOpenMetrics100(_))

  def responseParOpenMetrics100[F[_]: Applicative: Parallel, T[_]: Foldable](c: T[CollectorRegistry[F]]): F[Response[F]] =
    c.parFoldMapA(_.write004).map(makeResponseOpenMetrics100(_))

  def routesOpenMetrics100[F[_]: Monad](cr: CollectorRegistry[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}; import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "metrics" => responseOpenMetrics100(cr)
    }
  }

  def routesOpenMetrics100[F[_]: Monad, T[_]: Foldable](c: T[CollectorRegistry[F]]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}; import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "metrics" => responseOpenMetrics100(c)
    }
  }

  def routesParOpenMetrics100[F[_]: Monad: Parallel, T[_]: Foldable](c: T[CollectorRegistry[F]]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}; import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "metrics" => responseParOpenMetrics100(c)
    }
  }
}