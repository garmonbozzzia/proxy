import java.util.Calendar

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RequestContext, RouteResult}
import akka.stream.ActorMaterializer
import akka.util.ByteString

import scala.io.StdIn

//80.211.27.151/ru/schedules/schdullabha
object Proxy extends App {
  val calendar = java.util.Calendar.getInstance
  implicit class Traceable[A] (val obj: A) extends AnyVal {
    def traceWith[B](f: A => B ): A = { println(f(obj)); obj}
    def trace[U](u: => U): A = traceWith(_ => u)
    def trace: A = trace[A](obj)
  }

  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  import akka.http.caching.LfuCache
  import akka.http.caching.scaladsl.{Cache, CachingSettings}
  import akka.http.scaladsl.server.directives.CachingDirectives._

  import scala.concurrent.duration._

  val defaultCachingSettings = CachingSettings(system)
  val lfuCacheSettings =
    defaultCachingSettings.lfuCacheSettings
      .withInitialCapacity(25)
      .withMaxCapacity(50)
      .withTimeToLive(20.seconds)
      .withTimeToIdle(10.seconds)
  val cachingSettings = defaultCachingSettings.withLfuCacheSettings(lfuCacheSettings)
  val lfuCache: Cache[Uri, RouteResult] = LfuCache(cachingSettings)
  val keyerFunction: PartialFunction[RequestContext, Uri] = {
    case r: RequestContext ⇒ r.request.uri
  }

  def schedulePath = Get(Uri("https://www.dhamma.org/ru/schedules/schdullabha"))

  def load(request: HttpRequest) = for {
    response <- Http().singleRequest(request)
    data <- response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
  } yield HttpEntity(response.entity.contentType, data)

  def transformed(uri: Uri) = Get(uri.withHost("www.dhamma.org").withScheme("https").withPort(0))
  def logText = s"[${calendar.getTime}]"
  val route = ((path("assets") | path("favicon.ico") | path("system")) &  get &
  cache(lfuCache, keyerFunction) & extractUri){ uri =>
    onSuccess(load(transformed(uri).trace(logText))) (complete(_))
  } ~ ((path("ru/schedules/schdullabha") | path("")) & get &
    cache(lfuCache, keyerFunction) & onSuccess(load(schedulePath))) (complete(_))

  val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", args.lift(0).fold(80)(_.toInt))

  println(s"Server online at http://localhost:8080/ru/schedules/schdullabha\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}