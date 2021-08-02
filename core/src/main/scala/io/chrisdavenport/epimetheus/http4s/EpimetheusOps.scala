package io.chrisdavenport.epimetheus.http4s

import cats._
import cats.implicits._
import cats.effect._
import org.http4s.{Method, Status}
import org.http4s.metrics.MetricsOps
import org.http4s.metrics.TerminationType
import org.http4s.metrics.TerminationType.{Abnormal, Error, Timeout, Canceled}

import io.chrisdavenport.epimetheus._

/**
 * `MetricsOps` algebra capable of recording Prometheus metrics
 *
 * For example, the following code would wrap a `org.http4s.HttpRoutes` with a `org.http4s.server.middleware.Metrics`
 * that records metrics to a given metric registry.
 * {{{
 * import org.http4s.client.middleware.Metrics
 * import io.chrisdavenport.epimetheus.http4s.Epimetheus
 * 
 * val meteredRoutes = EpimetheusOps.register(collectorRegistry)
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
 * val meteredClient = EpimetheusOps.register(collectorRegistry)
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
object EpimetheusOps {

  def server[F[_]: Sync](
    cr: CollectorRegistry[F],
    buckets: List[Double] = Histogram.defaults,
    reportMethod: Method => String = EpimetheusOps.defaultReportMethod,
    reportStatus: Status => String = EpimetheusOps.defaultReportStatus
  ): F[MetricsOps[F]] = 
    register(cr, Name("org_http4s_server"), buckets, reportMethod, reportStatus)

  def client[F[_]: Sync](
    cr: CollectorRegistry[F],
    buckets: List[Double] = Histogram.defaults,
    reportMethod: Method => String = EpimetheusOps.defaultReportMethod,
    reportStatus: Status => String = EpimetheusOps.defaultReportStatus
  ): F[MetricsOps[F]] = 
    register(cr, Name("org_http4s_client"), buckets, reportMethod, reportStatus)

  def register[F[_]: Sync](
    cr: CollectorRegistry[F], 
    prefix: Name,
    buckets: List[Double] = Histogram.defaults,
    reportMethod: Method => String = EpimetheusOps.defaultReportMethod,
    reportStatus: Status => String = EpimetheusOps.defaultReportStatus
  ): F[MetricsOps[F]] = 
    MetricsCollection.build(cr, prefix, buckets, reportMethod, reportStatus)
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
      responseDuration: UnlabelledHistogram[F, (Classifier, Method, Phase)],
      activeRequests: UnlabelledGauge[F, Classifier],
      requests: UnlabelledCounter[F, (Classifier, Method, Status)],
      abnormalTerminations: UnlabelledHistogram[F, (Classifier, TerminationType)]
  )
  private object MetricsCollection {
    def build[F[_]: Sync](
      cr: CollectorRegistry[F],
      prefix: Name, 
      buckets: List[Double],
      reportMethod: Method => String,
      reportStatus: Status => String
    ) = for {
      responseDuration <- Histogram.labelledBuckets(
        cr,
        prefix |+| Name("_") |+| Name("response_duration_seconds"),
        "Response Duration in seconds.",
        Sized(Label("classifier"),Label("method"), Label("phase")),
        encodeResponseDuration(_, reportMethod),
        buckets:_*
      )
      activeRequests <- Gauge.labelled(
        cr, 
        prefix |+| Name("_") |+| Name("active_request_count"),
        "Total Active Requests.",
        Sized(Label("classifier")),
        {(c: Classifier) => Sized(c.s)}
      )
      requests <- Counter.labelled(
        cr,
        prefix |+| Name("_") |+| Name("request_count"),
        "Total Requests.",
        Sized(Label("classifier"), Label("method"), Label("status")),
        encodeRequest(_, reportMethod, reportStatus)
      )
      abnormal <- Histogram.labelledBuckets(
        cr,
        prefix |+| Name("_") |+| Name("abnormal_terminations"),
        "Total Abnormal Terminations.",
        Sized(Label("classifier"), Label("termination_type")),
        encodeAbnormal,
        buckets:_*
      )
    } yield MetricsCollection[F](responseDuration, activeRequests, requests, abnormal)
  }

  private def encodeResponseDuration(s: (Classifier, Method, Phase), reportMethod: Method => String) = s match {
    case (classifier, method, phase) => Sized(classifier.s, reportMethod(method), reportPhase(phase))
  }

  private def encodeRequest(s: (Classifier, Method, Status), reportMethod: Method => String, reportStatus: Status => String) = s match {
    case (classifier, method, status) => Sized(classifier.s, reportMethod(method), reportStatus(status))
  }

  private def encodeAbnormal(s: (Classifier, TerminationType)) = s match {
    case (classifier, term) => Sized(classifier.s, reportTermination(term))
  }

  def defaultReportStatus(status: Status): String =
    status.code match {
      case hundreds if hundreds < 200 => "1xx"
      case twohundreds if twohundreds < 300 => "2xx"
      case threehundreds if threehundreds < 400 => "3xx"
      case fourhundreds if fourhundreds < 500 => "4xx"
      case _ => "5xx"
    }

  def secondaryReportStatus(status: Status): String = status.code.show

  def defaultReportMethod(m: Method): String = m match {
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

  def secondaryReportMethod(m: Method): String = m.name.toLowerCase()

  private def reportTermination(t: TerminationType): String = t match {
    case Canceled => "canceled"
    case Abnormal(_) => "abnormal"
    case Error(_) => "error"
    case Timeout => "timeout"
  }

  private def reportPhase(p: Phase): String = p match {
    case Phase.Headers => "headers"
    case Phase.Body => "body"
  }

  private[EpimetheusOps] class Classifier(val s: String) extends AnyVal
  private[EpimetheusOps] object Classifier {
    def fromOpt(s: Option[String]): Classifier = new Classifier(s.getOrElse(""))
  }


  private[EpimetheusOps] sealed trait Phase
  private[EpimetheusOps] object Phase {
    case object Headers extends Phase
    case object Body extends Phase
  }
}
