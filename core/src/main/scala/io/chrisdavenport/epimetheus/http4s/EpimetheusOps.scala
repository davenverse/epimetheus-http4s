package io.chrisdavenport.epimetheus.http4s

import cats._
import cats.implicits._
import cats.effect._
import org.http4s.{Method, Status}
import org.http4s.metrics.MetricsOps
import org.http4s.metrics.TerminationType
import org.http4s.metrics.TerminationType.{Abnormal, Error, Timeout}
import shapeless._

import io.chrisdavenport.epimetheus._

/**
 * /**
  * `MetricsOps` algebra capable of recording Prometheus metrics
  *
  * For example, the following code would wrap a `org.http4s.HttpRoutes` with a `org.http4s.server.middleware.Metrics`
  * that records metrics to a given metric registry.
  * {{{
  * import org.http4s.client.middleware.Metrics
  * import io.chrisdavenport.epimetheus.http4s.Epimetheus
  * 
  * val meteredRoutes = Epimetheus.register(collectorRegistry)
  *   .map(metricOps => Metrics[IO](metricOps)(testRoutes))
  * }}}
  *
  * Analogously, the following code would wrap a `org.http4s.client.Client` with a `org.http4s.client.middleware.Metrics`
  * that records metrics to a given metric registry, classifying the metrics by HTTP method.
  * {{{
  * import org.http4s.client.middleware.Metrics
  * import io.chrisdavenport.epimetheus.http4s.Epimetheus
  *
  * val classifierFunc = (r: Request[IO]) => Some(r.method.toString.toLowerCase)
  * val meteredClient = Epimetheus.register(collectorRegistry)
  *   .map(metricOps => Metrics(metricOps, classifierFunc)(client))
  * }}}
  *
  * Registers the following metrics:
  *
  * {prefix}_response_duration_seconds{labels=classifier,method,phase} - Histogram
  *
  * {prefix}_active_request_count{labels=classifier} - Gauge
  *
  * {prefix}_request_count{labels=classifier,method,status} - Counter
  *
  * {prefix}_abnormal_terminations{labels=classifier,termination_type} - Histogram
  *
  * Labels --
  *
  * method: Enumeration
  * values: get, put, post, head, move, options, trace, connect, delete, other
  *
  * phase: Enumeration
  * values: headers, body
  *
  * code: Enumeration
  * values:  1xx, 2xx, 3xx, 4xx, 5xx
  *
  * termination_type: Enumeration
  * values: abnormal, error, timeout
  */
 */
object EpimetheusOps {

  def register[F[_]: Sync: Clock](
    cr: CollectorRegistry[F], 
    prefix: String = "org_http4s_server",
    buckets: List[Double] = Histogram.defaults
  ): F[MetricsOps[F]] = 
    MetricsCollection.build(cr, prefix, buckets)
      .map(new EpOps(_))

  private class EpOps[F[_]: Monad](metrics: MetricsCollection[F]) extends MetricsOps[F]{
    override def increaseActiveRequests(classifier: Option[String]): F[Unit] = 
      metrics.activeRequests.label(Classifier.fromOpt(classifier)).inc

    override def decreaseActiveRequests(classifier: Option[String]): F[Unit] =
      metrics.activeRequests.label(Classifier.fromOpt(classifier)).dec

    override def recordHeadersTime(
          method: Method,
          elapsed: Long,
          classifier: Option[String]): F[Unit]= 
      metrics.responseDuration
        .label((Classifier.fromOpt(classifier), method, Phase.Headers))
        .observe(nanosToSeconds(elapsed))

    override def recordTotalTime(
          method: Method,
          status: Status,
          elapsed: Long,
          classifier: Option[String]
    ): F[Unit] = metrics.responseDuration
            .label((Classifier.fromOpt(classifier), method, Phase.Body))
            .observe(nanosToSeconds(elapsed)) >>
          metrics.requests
            .label((Classifier.fromOpt(classifier), method, status))
            .inc

    override def recordAbnormalTermination(
          elapsed: Long,
          terminationType: TerminationType,
          classifier: Option[String]
    ): F[Unit]  = 
      metrics.abnormalTerminations.label((Classifier.fromOpt(classifier), terminationType))
        .observe(nanosToSeconds(elapsed))
  }


  // Elapsed is in Nanos, but buckets are in Seconds
  private def nanosToSeconds(l: Long): Double = 
    l / nanoseconds_per_second
  private val nanoseconds_per_second = 1E9


  private case class MetricsCollection[F[_]](
      responseDuration: Histogram.UnlabelledHistogram[F, (Classifier, Method, Phase)],
      activeRequests: Gauge.UnlabelledGauge[F, Classifier],
      requests: Counter.UnlabelledCounter[F, (Classifier, Method, Status)],
      abnormalTerminations: Histogram.UnlabelledHistogram[F, (Classifier, TerminationType)]
  )
  private object MetricsCollection {
    def build[F[_]: Sync: Clock](cr: CollectorRegistry[F], prefix: String, buckets: List[Double]) = for {
      responseDuration <- Histogram.labelledBuckets(
        cr,
        prefix + "_" + "response_duration_seconds",
        "Response Duration in seconds.",
        Sized("classifier","method","phase"),
        encodeResponseDuration,
        buckets:_*
      )
      activeRequests <- Gauge.labelled(
        cr, 
        prefix + "_" + "active_request_count",
        "Total Active Requests.",
        Sized("classifier"),
        {c: Classifier => Sized(c.s)}
      )
      requests <- Counter.labelled(
        cr,
        prefix + "_" + "request_count",
        "Total Requests.",
        Sized("classifier", "method", "status"),
        encodeRequest
      )
      abnormal <- Histogram.labelledBuckets(
        cr,
        prefix + "_" + "abnormal_terminations",
        "Total Abnormal Terminations.",
        Sized("classifier", "termination_type"),
        encodeAbnormal,
        buckets:_*
      )
    } yield MetricsCollection[F](responseDuration, activeRequests, requests, abnormal)
  }

  private def encodeResponseDuration(s: (Classifier, Method, Phase)) = s match {
    case (classifier, method, phase) => Sized(classifier.s, reportMethod(method), Phase.report(phase))
  }

  private def encodeRequest(s: (Classifier, Method, Status)) = s match {
    case (classifier, method, status) => Sized(classifier.s, reportMethod(method), reportStatus(status))
  }

  private def encodeAbnormal(s: (Classifier, TerminationType)) = s match {
    case (classifier, term) => Sized(classifier.s, reportTermination(term))
  }




  private def reportStatus(status: Status): String =
    status.code match {
      case hundreds if hundreds < 200 => "1xx"
      case twohundreds if twohundreds < 300 => "2xx"
      case threehundreds if threehundreds < 400 => "3xx"
      case fourhundreds if fourhundreds < 500 => "4xx"
      case _ => "5xx"
    }

  private def reportMethod(m: Method): String = m match {
    case Method.GET => "get"
    case Method.PUT => "put"
    case Method.POST => "post"
    case Method.HEAD => "head"
    case Method.MOVE => "move"
    case Method.OPTIONS => "options"
    case Method.TRACE => "trace"
    case Method.CONNECT => "connect"
    case Method.DELETE => "delete"
    case _ => "other"
  }

  def reportTermination(t: TerminationType): String = t match {
    case Abnormal => "abnormal"
    case Error => "error"
    case Timeout => "timeout"
  }

  private[EpimetheusOps] class Classifier(val s: String) extends AnyVal
  private[EpimetheusOps] object Classifier {
    def fromOpt(s: Option[String]): Classifier = new Classifier(s.getOrElse(""))
  }


  private[EpimetheusOps] sealed trait Phase
  private[EpimetheusOps] object Phase {
    case object Headers extends Phase
    case object Body extends Phase
    def report(s: Phase): String = s match {
      case Headers => "headers"
      case Body => "body"
    }
  }
}