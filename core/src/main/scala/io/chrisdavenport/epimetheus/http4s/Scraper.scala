package io.chrisdavenport.epimetheus.http4s

import cats._
import cats.implicits._
import cats.effect._
import io.chrisdavenport.epimetheus.PrometheusRegistry
import org.http4s._
import org.http4s.dsl.Http4sDsl

object Scraper {

  def response[F[_]: Functor](cr: PrometheusRegistry[F]): F[Response[F]] =
    cr.write004.map(Response[F](Status.Ok).withEntity(_))

  def routes[F[_]: Sync](cr: PrometheusRegistry[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}; import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "metrics" => response(cr)
    }
  }
}