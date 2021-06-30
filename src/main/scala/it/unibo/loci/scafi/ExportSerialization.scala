package it.unibo.loci.scafi

import com.fasterxml.jackson.annotation.JsonFormat
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json, JsonNumber}
import it.unibo.scafi.distrib.actor.serialization.{BasicJsonAnySerialization, BasicSerializers}
import it.unibo.scafi.incarnations.Incarnation
import loci.MessageBuffer
import loci.transmitter.{IdenticallyTransmittable, Serializable}
import play.api.libs.json._
import play.api.libs.json.{Json => PlayJSon}
import upickle.core.{ArrVisitor, ObjVisitor, Visitor}

import scala.util.{Failure, Success, Try}
import upickle.default._
trait ExportSerialization {
  self : Incarnation =>
  import BasicSerializers._
  private val jsonAnyHelpers = new BasicJsonAnySerialization { }
  implicit val formatSlot: Format[Slot] = new Format[Slot] {
    override def writes(o: Slot): JsValue = o match {
      case Nbr(i) => JsObject(Map("type" -> JsString("nbr"), "index" -> JsNumber(i)))
      case Rep(i) => JsObject(Map("type" -> JsString("rep"), "index" -> JsNumber(i)))
      case FunCall(i, funId) => JsObject(Map("type" -> JsString("funcall"), "index" -> JsNumber(i), "funId" -> jsonAnyHelpers.anyToJs(funId)))
      case FoldHood(i) => JsObject(Map("type" -> JsString("foldhood"), "index" -> JsNumber(i)))
      case Scope(key) => JsObject(Map("type" -> JsString("scope"), "key" -> jsonAnyHelpers.anyToJs(key)))
    }
    @annotation.nowarn
    override def reads(json: JsValue): JsResult[Slot] = JsSuccess {
      json match {
        case jo@JsObject(underlying) if underlying.contains("type") => jo.value("type") match {
          case JsString("nbr") => Nbr(jo.value("index").as[BigDecimal].toInt)
          case JsString("rep") => Rep(jo.value("index").as[BigDecimal].toInt)
          case JsString("funcall") => FunCall(jo.value("index").as[BigDecimal].toInt, jo.value("funId").as[String])
          case JsString("foldhood") => FoldHood(jo.value("index").as[BigDecimal].toInt)
          case JsString("scope") => Scope(jo.value("key").as[String])
        }
      }
    }
  }

  implicit val formatPath: Format[Path] = new Format[Path] {
    override def writes(p: Path): JsValue = JsArray(p.path.map(s => formatSlot.writes(s)))
    override def reads(json: JsValue): JsResult[Path] =
      JsSuccess(factory.path(json.validate[List[Slot]].get:_*))
  }

  implicit def formatExportMap[T:Format]: Format[Map[Path,T]] = mapFormat[Path,T]

  val readsExp: Reads[EXPORT] = (json: JsValue) => JsSuccess(
    factory.export(
      json.as[Map[Path, Any]](mapAnyFormat).toSeq: _*
    )
  )
  val writesExp: Writes[EXPORT] = (o: EXPORT) => mapAnyWrites[Path].writes(o.paths)

  implicit val exportTransmittable: IdenticallyTransmittable[EXPORT] = IdenticallyTransmittable()
  implicit val exportEncoder: Encoder[EXPORT] = (a: EXPORT) => {
    val decoded = writesExp.writes(a)
    val dataRaw = PlayJSon.toBytes(decoded).map(a => Json.fromInt(a.toInt))
    val json = Json.obj("raw" -> Json.fromValues(dataRaw))
    json
  }
  implicit val exportDecoder: Decoder[EXPORT] = (c: HCursor) => {
    //unsafe, fix
    val rawData = (c.value \\ "raw").head.asArray.get
    val raw = rawData.map(a => a.asNumber).collect { case Some(json) => json.toByte.get}.toArray
    readsExp.reads(PlayJSon.parse(raw)).asEither match {
      case Left(_) => Left(DecodingFailure.fromThrowable(new IllegalArgumentException("Fail!"), List.empty))
      case Right(value) => Right[DecodingFailure, EXPORT](value)
    }
  }

}
