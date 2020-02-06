package golomb

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import scala.io.StdIn

object Server extends App {
  override def main(args: Array[String]) {

    implicit val system = ActorSystem("actor-sytem-for-server")
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    val route =
      path("ping") {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>pong</h1>"))
        }
      } ~ path("solve") {
        get {
          val solution: String = GolombRuler.solve()
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"<h1>$solution</h1>"))
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}