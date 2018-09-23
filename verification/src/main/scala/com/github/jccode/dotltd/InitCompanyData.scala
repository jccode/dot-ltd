package com.github.jccode.dotltd

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import akka.stream.alpakka.slick.javadsl.SlickSession
import akka.stream.alpakka.slick.scaladsl.Slick
import akka.stream.scaladsl.{Flow, Keep, Sink}
import com.github.jccode.dotltd.dao.Tables.{Stock, stocks}
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.Json
import spray.json.RootJsonFormat

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.io.Source


case class CompanyItem(name: String, fullName: Option[String] = None, stockCode: Option[String] = None, engName: Option[String] = None, website: Option[String] = None)

object InitCompanyData extends App {

  val runner = new CompanyDataFlowRunner()
  runner.startFlow()

  def resolveTest(): Unit = {
    import CompanyResolverImplTest._
    def resolve = akkaHttpResolverTest _
    resolve("201000")
    resolve("500007")
    resolve("600066")
    resolve("000070")
    resolve("150209")
    resolve("200613")
    resolve("300003")
  }

}

class CompanyDataFlowRunner extends LazyLogging {

  implicit val actorSystem = ActorSystem()
  implicit val materialize = ActorMaterializer()
  import actorSystem.dispatcher
  implicit val session = SlickSession.forConfig("dotltd")
  val resolver = new CompanyResolverImpl

  import session.profile.api._
//  val source = Slick.source(stocks.filter(x => x.code.startsWith("2") || x.code.startsWith("6")).take(200).result)
  val source = Slick.source(stocks.result)

  val flow = Flow[Stock]
    .mapAsync(5)(stock => resolver.resolveCompany(stock.code).map(_.map(_.copy(name = stock.name.get))))

  val sink = Sink.foreach[Option[CompanyItem]] {
    case Some(item) => logger.info(item.toString)
    case None => logger.info("No company")
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

abstract class EastMoneyResolver extends CompanyResolver {

  private val sh_special_code = Seq("001","2","110","129","310","5","6","700","710","701","711","720","730","735","737","900")
  private val sz_special_code = Seq()

  /**
    * 判断是哪个交易所.
    * ref: https://zhidao.baidu.com/question/22307796.html?qbl=relate_question_1&word=5%BF%AA%CD%B7%B5%C4%B9%C9%C6%B1%D3%D0%C4%C4%D0%A9
    * @param code
    * @return
    */
  private def pre(code: String): String = code match {
    case c if sh_special_code.nonEmpty && sh_special_code.exists(code.startsWith) => "sh"
    case c if sz_special_code.nonEmpty && sz_special_code.exists(code.startsWith) => "sz"
    case _ => "sz"
  }

  def url(code: String): String = {
    s"http://emweb.securities.eastmoney.com/PC_HSF10/CompanySurvey/CompanySurveyAjax?code=${pre(code)}$code"
  }
}

class CompanResolverBlockImpl(implicit ec: ExecutionContext) extends EastMoneyResolver {

  override def resolveCompany(stockCode: String): Future[Option[CompanyItem]] = {
    val json = Json.parse(Source.fromURL(url(stockCode)).mkString)
    // println(json)
     println(json \ "Result" \ "jbzl")
    val info = json \ "Result" \ "jbzl"
    if (info.isDefined) {
      Future {
        Some(
          CompanyItem(
            (info \ "agjc").get.toString(),
            (info \ "gsmc").toOption.map(_.toString()),
            Some(stockCode),
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

class CompanyResolverImpl(implicit val actorSystem: ActorSystem, implicit val materializer: ActorMaterializer) extends EastMoneyResolver {

  import actorSystem.dispatcher
  import akka.http.scaladsl.model.StatusCodes._
  import CompanyResolverImpl._

  import akka.http.scaladsl.unmarshalling._

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
              CompanyItem(info.agjc, info.gsmc, Some(stockCode), info.ywmc, info.gswz)
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

  def blockResolverTest(code: String): Unit =  {
    import scala.concurrent.ExecutionContext.Implicits.global
    val resolver = new CompanResolverBlockImpl
    val f = resolver.resolveCompany(code) // 501030, 300059
    println(Await.result(f, 5 seconds))
  }

  def akkaHttpResolverTest(code: String): Unit = {
    implicit val actorSystem = ActorSystem()
    implicit val materialize = ActorMaterializer()
    implicit val ec = actorSystem.dispatcher
    val resolver = new CompanyResolverImpl
    val f = resolver.resolveCompany(code)
    f.onComplete(_ => actorSystem.terminate())
    println(Await.result(f, 5 seconds))
  }
}
