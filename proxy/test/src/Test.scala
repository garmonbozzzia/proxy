import java.util.Calendar

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, ThrottleMode}
import com.redis.RedisClientPool
import utest._

import scala.concurrent.Future

object Test extends TestSuite{

  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val rcp = new RedisClientPool("localhost", 6379)

  val tests = Tests{
    import Extensions._

    'Test - {
      // ключ
      val future = Future{
        Thread.sleep(2000)
        println("A")
        100
      }
      for {
        a <- future
        b <- future
        c <- future
        d <- future
      } yield "done"
    }

    'Redis - {
    }

    'RedisCache - {
      def load(key: String) = Future{
        s"loading $key".trace
        Thread.sleep(5000)
        s"loaded $key".trace
        s"${key} ${System.currentTimeMillis}"
      }
      val cacheStorage = scala.collection.mutable.Map.empty[String, Future[String]]
      def cachedOrLoad(request: String, expireTimeInSec: Long = 3600) = rcp.withClient { rc =>
        val key = s"temp.$request"
        if(!cacheStorage.contains(key)) {
          cacheStorage(key) = load(request)
          rc.setex(key, expireTimeInSec, 0)
        }
        if (rc.exists(key)) cacheStorage(key)
        else {
          rc.setex(key, expireTimeInSec, 0)
          for(res <- load(request); _ = cacheStorage(key) = Future.successful(res)) yield res
          cacheStorage(key)
        }
      }

      import scala.concurrent.duration._
      Source.cycle(() => List("AAAA").iterator).throttle(1, 500 millisecond, 1, ThrottleMode.Shaping)
        .take(100)
        .mapAsync(100)(x => cachedOrLoad(x, 10).trace(s"Asked: $x"))
        .map(_.traceWith(x => s"${Calendar.getInstance().getTime}: $x"))
        .runForeach(_ => ())
    }
  }
}
