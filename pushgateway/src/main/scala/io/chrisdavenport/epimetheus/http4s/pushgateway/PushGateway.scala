package io.chrisdavenport.epimetheus
package http4s
package pushgateway

import org.http4s._
import cats.effect._
import cats.implicits._
import org.http4s.client._

abstract class PushGateway[F[_]]{
  def push(cr: CollectorRegistry[F], job: String): F[Unit]

  def push(cr: CollectorRegistry[F], job: String, groupingKey: Map[String, String]): F[Unit]

  def pushAdd(cr: CollectorRegistry[F], job: String): F[Unit]
  def pushAdd(cr: CollectorRegistry[F], job: String, groupingKey: Map[String, String]): F[Unit]

  def delete(job: String): F[Unit]
  def delete(job: String, groupingKey: Map[String, String]): F[Unit]
}

object PushGateway {

  def fromClient[F[_]: Sync](c: Client[F], serverUri: Uri): PushGateway[F] =
    new BasicPushGateway[F](c, serverUri)
  

  private class BasicPushGateway[F[_]: Sync](val client: Client[F], uri: Uri) extends PushGateway[F]{
    def push(cr: CollectorRegistry[F], job: String): F[Unit] = 
      push(cr, job, Map.empty)
    def push(cr: CollectorRegistry[F], job: String, groupingKey: Map[String, String]): F[Unit] =
      doPut(client, uri, job, groupingKey, cr)

    def pushAdd(cr: CollectorRegistry[F], job: String): F[Unit] =
      pushAdd(cr, job, Map.empty)
    def pushAdd(cr: CollectorRegistry[F], job: String, groupingKey: Map[String, String]): F[Unit] = 
      doPost(client, uri, job, groupingKey, cr)

    def delete(job: String): F[Unit] =
      delete(job, Map.empty)
    def delete(job: String, groupingKey: Map[String, String]): F[Unit] =
      doDelete(client, uri, job, groupingKey)

  }

  private def errorHandler[F[_]: Sync](uri: Uri)(resp: Response[F]): F[Throwable] = 
    for {
      body <- resp.bodyText.compile.string
      status = resp.status
    } yield new Exception(show"Response code from ${uri.renderString} was $status, response body: $body")

  private def requestUrl(baseUri: Uri, job: String, groupingKey: Map[String, String]): Uri =
    groupingKey.toList.foldLeft(baseUri / "metrics" / "job" /  job){ case (next, (k, v)) => next / k / v }

  private def doDelete[F[_]: Sync](
    client: Client[F], 
    baseUri: Uri, 
    job: String, 
    groupingKey: Map[String, String]
  ): F[Unit] = {
    val uri = requestUrl(baseUri, job, groupingKey)
    client.expectOr[Unit](Request[F](Method.DELETE, uri))(errorHandler(uri))
  }

  private def doPost[F[_]: Sync](
    client: Client[F],
    baseUri: Uri,
    job: String,
    groupingKey: Map[String, String],
    cr: CollectorRegistry[F]
  ): F[Unit] = {
    val uri = requestUrl(baseUri, job, groupingKey)
    cr.write004.flatMap{text =>
      client.expectOr[Unit](Request[F](Method.POST, uri).withEntity(text))(errorHandler(uri))
    }
  }

  private def doPut[F[_]: Sync](
    client: Client[F],
    baseUri: Uri,
    job: String,
    groupingKey: Map[String, String],
    cr: CollectorRegistry[F]
  ): F[Unit] = {
    val uri = requestUrl(baseUri, job, groupingKey)
    cr.write004.flatMap{text =>
      client.expectOr[Unit](Request[F](Method.PUT, uri).withEntity(text))(errorHandler(uri))
    }
  }

}
