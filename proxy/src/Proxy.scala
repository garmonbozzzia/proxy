import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.CachingDirectives.cache
import akka.http.scaladsl.server.{RequestContext, RouteResult}
import akka.stream.ActorMaterializer
import akka.util.ByteString

import scala.concurrent.Future
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
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  import akka.http.caching.LfuCache
  import akka.http.caching.scaladsl.{Cache, CachingSettings}
  //#caching-directives-import
  //#always-cache
  //#cache
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


  val route =
    path(Remaining) { path =>
      extractClientIP { ip =>
        if (path == "/ru/schedules/schdullabha") calendar.getTime.traceWith(t => s"[$t: $ip]")
        extractUri { uri =>
          get {
            cache(lfuCache, keyerFunction)(
              onSuccess(Http().singleRequest(Get(uri.withHost("www.dhamma.org").withScheme("https").withPort(0)))) {
              response =>
                onSuccess(response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)) {
                  data => complete(HttpEntity(response.entity.contentType, data))
                }
              }
            )
          }
        }
      }
    }

  val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8080)

  println(s"Server online at http://localhost:8080/ru/schedules/schdullabha\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}