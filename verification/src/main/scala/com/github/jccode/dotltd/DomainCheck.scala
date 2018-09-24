package com.github.jccode.dotltd

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.model.{HttpHeader, HttpRequest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future


object DomainCheck extends App {


}

object DomainResolverTest {

  def check(): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    import system.dispatcher

    val resolver = new CndnsResolver

    Future.sequence(
      Seq(
        resolver.isAvailable("jcchen", "ltd"),
        resolver.isAvailable("xiamol", "ltd"),
        resolver.isAvailable("qq100", "ltd"),
        resolver.isAvailable("a", "ltd"),
        resolver.isAvailable("163", "ltd"),
        resolver.isAvailable("chen", "ltd")
      )
    )
      .map(x => { println(x); x })
      .onComplete(_ => {
        resolver.shutdown().flatMap { _ =>
          materializer.shutdown()
          system.terminate()
        }
      })
  }
}


trait DomainResolver {
  def isAvailable(name: String, suffix: String): Future[Either[String, Boolean]]
}

class CndnsResolver(implicit system: ActorSystem, materializer: ActorMaterializer) extends DomainResolver with LazyLogging {
  import akka.http.scaladsl.model.HttpHeader.ParsingResult._
  import scala.collection.immutable.Seq
  import system.dispatcher

  private[this] val HOST = "https://www.cndns.com"

  private[this] def url_sign(name: String) =
    s"$HOST/Ajax/buildkey.ashx?module=getkey&domainName=$name"

  private[this] def url_domain_check(name: String, suffix: String, sign: String) =
    s"$HOST/Ajax/domainQuery.ashx?panel=domain&domainName=$name&domainSuffix=.$suffix&usrcls=1&cookieid=1&usrname=&sbtype=1&domainquerysign=$sign"

  private[this] val headers = Seq(
    HttpHeader.parse("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_3) AppleWebKit/604.5.6 (KHTML, like Gecko) Version/11.0.3 Safari/604.5.6'") match {
      case Ok(header, _) => header
    }
  )

  private[this] def getSignature(name: String): Future[Either[String, String]] = {
    Http().singleRequest(HttpRequest(uri = url_sign(name), headers = headers)).flatMap { res =>
      res.status match {
        case OK => {
          Unmarshal(res.entity).to[String].map { content =>
            Right[String, String](content)
          }
        }
        case _@err => {
          Future {
            Left[String, String](err.toString())
          }
        }
      }
    }
  }

  private[this] def parseResponse(content: String): Either[String, Boolean] = {
    if (content.trim.isEmpty)
      return Left[String, Boolean]("Empty response from server")
    val c = content.split("\\|")
    if (c.size < 4)
      return Left[String, Boolean]("Unexpected response from server")
    if (c(4).nonEmpty) { // 域名可用
      Right[String, Boolean](true)
    } else { // 不可用
      Right[String, Boolean](false)
    }
  }

  override def isAvailable(name: String, suffix: String): Future[Either[String, Boolean]] = {
    import akka.http.scaladsl.model.StatusCodes._
    import akka.http.scaladsl.unmarshalling.Unmarshal

    getSignature(name).flatMap {
      case Right(sign) => {
        Http().singleRequest(HttpRequest(uri = url_domain_check(name, suffix, sign), headers = headers)).flatMap { res =>
          res.status match {
            case OK => {
              Unmarshal(res.entity).to[String].map { content =>
                logger.debug(content)
                parseResponse(content)
              }
            }
            case err => {
              println(err)
              Future {
                Left[String, Boolean](err.toString())
              }
            }
          }
        }
      }
      case Left(err) => Future { Left[String, Boolean](err.toString) }
    }
  }

  def shutdown() = Http().shutdownAllConnectionPools()
}
