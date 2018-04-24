import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.io.StdIn
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.util.ByteString

//80.211.27.151/ru/schedules/schdullabha
object Proxy extends App {
  implicit class Traceable[A] (val obj: A) extends AnyVal {
    def traceWith[B](f: A => B ): A = { println(f(obj)); obj}
    def trace[U](u: => U): A = traceWith(_ => u)
    def trace: A = trace[A](obj)
  }

  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  val route =
    path(Remaining) { path =>
      extractUri { uri =>
        extractRequest { request =>
          get {
            onSuccess(Http().singleRequest(Get(uri.withHost("www.dhamma.org").withScheme("https")))) { response =>
              onSuccess(response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)) { data =>
                complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, data))
              }
            }
          }
        }
      }
    }

  val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 80)

  println(s"Server online at http://localhost:8080/ru/schedules/schdullabha\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}