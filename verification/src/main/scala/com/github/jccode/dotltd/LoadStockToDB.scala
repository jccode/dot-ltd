package com.github.jccode.dotltd

import java.nio.file.Paths
import java.sql.Timestamp

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.slick.javadsl.SlickSession
import akka.stream.alpakka.slick.scaladsl.Slick
import akka.stream.scaladsl.{FileIO, Framing, Sink}
import akka.util.ByteString
import com.github.jccode.dotltd.dao.Tables.{Stock, stocks}

case class StockItem(name: String, code: String)

object InitStockApp extends App {

  implicit val actorSystem = ActorSystem()
  implicit val materialize = ActorMaterializer()
  implicit val ec = actorSystem.dispatcher
  implicit val session = SlickSession.forConfig("dotltd")

  val source = FileIO.fromPath(Paths.get(ClassLoader.getSystemResource("a.txt").toURI))

  val pattern = """(.+)\((\d+)\)""".r
  val flow = Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true)
    .map(_.utf8String)
    .collect {
      case pattern(name, code) => StockItem(name, code)
    }

  // val sink = Sink.foreach[StockItem](println)

  import session.profile.api._
  val sink = Slick.sink[StockItem] { stock: StockItem =>
    val now = new Timestamp(System.currentTimeMillis())
    stocks += Stock(0, Some(stock.name), stock.code, now, now)
  }

  source.via(flow).runWith(sink).onComplete(_ => {
    session.close()
    actorSystem.terminate()
  })
}
