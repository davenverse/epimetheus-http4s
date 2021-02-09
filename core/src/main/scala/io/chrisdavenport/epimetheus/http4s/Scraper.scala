package io.chrisdavenport.epimetheus.http4s

import cats._
import cats.syntax.all._
import cats.effect._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import io.chrisdavenport.epimetheus.CollectorRegistry

object Scraper {

  def response[F[_]: Functor](cr: CollectorRegistry[F]): F[Response[F]] = 
    cr.write004.map(Response[F](Status.Ok).withEntity(_))

  def routes[F[_]: Sync](cr: CollectorRegistry[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}; import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "metrics" => response(cr)
    }
  }
}