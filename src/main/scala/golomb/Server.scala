package golomb

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult, ThrottleMode}
import akka.stream.scaladsl._

import scala.concurrent.duration._
import scala.collection.immutable.Queue
import scala.concurrent.Promise
import scala.io.StdIn

object Server extends App {
  override def main(args: Array[String]) {

    implicit val system = ActorSystem("actors")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    // The source to broadcast (just ints for simplicity)
    val dataSource = Source.queue[String](1000, OverflowStrategy.backpressure).throttle(1, 1 seconds, 1, ThrottleMode.Shaping).map(_.toString)
    val p = Promise[SourceQueueWithComplete[String]]
    val s = dataSource.mapMaterializedValue { m =>
      p.trySuccess(m)
      m
    }

    // get the materialized queue so we can push into it
    p.future.map { queue =>
      (0 to 10000).foreach { i =>
        queue.offer(s"hello $i").map {
          case QueueOfferResult.Enqueued => println(s"enqueued")
          case QueueOfferResult.Dropped => println(s"dropped")
          case QueueOfferResult.Failure(ex) => println(s"Offer failed ${ex.getMessage}")
          case QueueOfferResult.QueueClosed => println("Source Queue closed")
        }
      }
    }

    // Go via BroadcastHub to allow multiple clients to connect
    val runnableGraph: RunnableGraph[Source[String, NotUsed]] =
      s.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right)

    val producer: Source[String, NotUsed] = runnableGraph.run()
    producer.runWith(Sink.ignore) // avoid back pressure on original flow

    // TODO Ignore data sent from client
    val wsHandler: Flow[Message, Message, NotUsed] =
      Flow[Message]
        .merge(producer) // Stream the data we want to the client
        .map(l => TextMessage(l.toString))

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
      } ~ path("wstest0") {
        get {
          extractUpgradeToWebSocket { upgrade =>
            val numbers = Source(1 to 10).map(i => TextMessage(i.toString))
            complete(upgrade.handleMessagesWithSinkSource(Sink.ignore, numbers))
          }
        }
      } ~ path("bhub") {
        get {
          handleWebSocketMessages(wsHandler)
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
