package com.github.jccode.dotltd

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import akka.stream.alpakka.slick.javadsl.SlickSession
import akka.stream.alpakka.slick.scaladsl.Slick
import akka.stream.scaladsl.{Flow, Keep, Sink}
import com.github.jccode.dotltd.dao.Tables.{Stock, stocks}
import play.api.libs.json.Json
import spray.json.RootJsonFormat

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.io.Source


case class CompanyItem(name: String, fullName: Option[String] = None, stockCode: Option[String] = None, engName: Option[String] = None, website: Option[String] = None)

object InitCompanyData extends App {

  val runner = new CompanyDataFlowRunner()
  runner.startFlow()

//  CompanyResolverImplTest.akkaHttpResolverTest()

}

class CompanyDataFlowRunner {

  implicit val actorSystem = ActorSystem()
  implicit val materialize = ActorMaterializer()
  import actorSystem.dispatcher
  implicit val session = SlickSession.forConfig("dotltd")
  val resolver = new CompanyResolverImpl

  import session.profile.api._
  val source = Slick.source(stocks.take(500).drop(400).result)

  val flow = Flow[Stock]
    .mapAsync(5)(stock => resolver.resolveCompany(stock.code))

  val sink = Sink.foreach[Option[CompanyItem]] {
    case Some(item) => println(item)
    case None => println("No company")
  }


  def startFlow(): Unit = {
    source.via(flow).runWith(sink).onComplete { _ =>
      session.close()
      actorSystem.terminate()
    }
  }
}


trait CompanyResolver {
  def resolveCompany(stockCode: String): Future[Option[CompanyItem]]
}

class CompanResolverBlockImpl(implicit ec: ExecutionContext) extends CompanyResolver {
  private def url(code: String): String = {
    val pre = if (code.startsWith("6")) "sh" else "sz"
    s"http://emweb.securities.eastmoney.com/PC_HSF10/CompanySurvey/CompanySurveyAjax?code=$pre$code"
  }

  override def resolveCompany(stockCode: String): Future[Option[CompanyItem]] = {
    val json = Json.parse(Source.fromURL(url(stockCode)).mkString)
    // println(json)
    // println(json \ "Result" \ "jbzl")
    val info = json \ "Result" \ "jbzl"
    if (info.isDefined) {
      Future {
        Some(
          CompanyItem(
            (info \ "agjc").get.toString(),
            (info \ "gsmc").toOption.map(_.toString()),
            (info \ "ywmc").toOption.map(_.toString()),
            (info \ "gswz").toOption.map(_.toString())
          )
        )
      }
    } else {
      Future { None }
    }
  }
}

class CompanyResolverImpl(implicit val actorSystem: ActorSystem, implicit val materializer: ActorMaterializer) extends CompanyResolver {

  import actorSystem.dispatcher
  import akka.http.scaladsl.model.StatusCodes._
  import CompanyResolverImpl._

  import akka.http.scaladsl.unmarshalling._

  private def url(code: String): String = {
    val pre = if (code.startsWith("6")) "sh" else "sz"
    s"http://emweb.securities.eastmoney.com/PC_HSF10/CompanySurvey/CompanySurveyAjax?code=$pre$code"
  }

  override def resolveCompany(stockCode: String): Future[Option[CompanyItem]] = {
    import CompanyResolverImpl.JsonProtocol._
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    Http().singleRequest(HttpRequest(uri = url(stockCode))).flatMap { res =>
      res.status match {
        case OK => {
          /*
          Unmarshal(res.entity).to[String].map { jsonStr =>
            val json = jsonStr.parseJson
            println(json.prettyPrint)
          }
          */
          Unmarshal(res.entity).to[Res].map { obj =>
            obj.Result.jbzl.map { info =>
              CompanyItem(info.agjc, info.gsmc, info.ywmc, info.gswz)
            }
          }
        }
        case _ => Future { None }
      }
    }
  }
}

object CompanyResolverImpl {
  import spray.json.DefaultJsonProtocol

  case class Info(agjc: String, gsmc: Option[String], ywmc: Option[String], gswz: Option[String])
  case class InfoWrapper(jbzl: Option[Info])
  case class Res(Result: InfoWrapper)

  object JsonProtocol extends DefaultJsonProtocol {
    implicit val infoFormat: RootJsonFormat[Info] = jsonFormat4(Info)
    implicit val infoWrapperFormat: RootJsonFormat[InfoWrapper] = jsonFormat1(InfoWrapper)
    implicit val resformat: RootJsonFormat[Res] = jsonFormat1(Res)
  }
}

/**
  * CompanyResolverImplement test
  */
object CompanyResolverImplTest {

  def blockResolverTest(): Unit =  {
    import scala.concurrent.ExecutionContext.Implicits.global
    val resolver = new CompanResolverBlockImpl
    val f = resolver.resolveCompany("300059") // 501030, 300059
    println(Await.result(f, 5 seconds))
  }

  def akkaHttpResolverTest(): Unit = {
    implicit val actorSystem = ActorSystem()
    implicit val materialize = ActorMaterializer()
    implicit val ec = actorSystem.dispatcher
    val resolver = new CompanyResolverImpl
    val f = resolver.resolveCompany("300059")
    f.onComplete(_ => actorSystem.terminate())
    println(Await.result(f, 5 seconds))
  }
}
