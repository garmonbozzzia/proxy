import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RequestContext, RouteResult}
import akka.stream.ActorMaterializer
import akka.util.ByteString
import com.redis.{RedisClient, RedisClientPool}

import scala.concurrent.Future
import scala.io.StdIn
import Extensions._


object Proxy extends App {

  def logText = s"[${java.util.Calendar.getInstance.getTime}]"

  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  import akka.http.caching.LfuCache
  import akka.http.caching.scaladsl.{Cache, CachingSettings}
  import akka.http.scaladsl.server.directives.CachingDirectives._

  import scala.concurrent.duration._

  val defaultCachingSettings = CachingSettings(system)
  val lfuCacheSettings = defaultCachingSettings
    .lfuCacheSettings
    .withInitialCapacity(10)
    .withMaxCapacity(10)
    .withTimeToLive(20.seconds)
    .withTimeToIdle(10.seconds)
  val cachingSettings = defaultCachingSettings.withLfuCacheSettings(lfuCacheSettings)
  val lfuCache: Cache[Uri, RouteResult] = LfuCache(cachingSettings)
  val keyerFunction: PartialFunction[RequestContext, Uri] = {
    case r: RequestContext ⇒ r.request.uri
  }
  def cch2 = cache(routeCache, keyerFunction)
  def cch = cache(lfuCache, keyerFunction)

  def schedulePath = Get(Uri("https://www.dhamma.org/ru/schedules/schdullabha"))

  def load(request: HttpRequest): Future[HttpEntity.Strict] = for {
    response <- Http().singleRequest(request)
    _ <- if (response.status == StatusCodes.OK) Future.successful(0)
         else Future.failed(new Exception(s"Code: ${response.status}"))
    data <- response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
  } yield HttpEntity(response.entity.contentType, data)//.trace(s"Loaded ${request.uri.path}")

  val rcp = new RedisClientPool("localhost", 6379)
  val cacheStorage = scala.collection.mutable.Map.empty[Uri, Future[HttpEntity.Strict]]

//  def keyFunction(request: HttpRequest) = request.uri.path

  def cachedOrLoad(request: HttpRequest, expireTimeInSec: Long = 3600) = rcp.withClient { rc =>
    val key = request.uri
    if(!cacheStorage.contains(key)) cacheStorage(key) = load(request)
    if (rc.exists(key)) cacheStorage(key)
    else {
      rc.setex(key,expireTimeInSec, "")
      for {
        response <- load(request)
        _ = cacheStorage(key) = Future.successful(response)
      } yield response
      cacheStorage(key)
    }
  }

  def transformed(uri: Uri) = Get(uri.withHost("www.dhamma.org").withScheme("https").withPort(0))
  val route = ((pathPrefix("assets") | path("favicon.ico") | pathPrefix("system")) & get & extractUri){ uri =>
    onSuccess(cachedOrLoad(transformed(uri))) (complete(_))
  } ~ ((pathPrefix("ru"/"schedules"/"schdullabha") | pathEndOrSingleSlash) &
  get & onSuccess(cachedOrLoad(schedulePath.trace(logText), 10))) (complete(_)) ~
  path(RemainingPath)(path => complete(path.toString))

  val bindingFuture = Http().bindAndHandle(cch(route), "0.0.0.0", args.lift(0).fold(80)(_.toInt))

  println(s"Server online at http://localhost:8080/ru/schedules/schdullabha\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}

//как сделать кэширование?
//обработка запроса: map[Future]