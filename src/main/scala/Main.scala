package com.selmank

import java.nio.charset.StandardCharsets

import scala.xml.XML
import scala.xml.Elem
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date

import com.selmank.SwingApp.woos

import scala.xml.{Elem, XML}
import io.circe.{Json, Printer, parser}
import io.circe.optics.JsonPath.root
import io.circe.parser.parse
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.jawn.decode
import scalaj.http.{Http, HttpOptions, HttpResponse, Token}
import com.selmank._
import comp._
import gnieh.diffson.circe._

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import scala.concurrent.duration._



object Main {
  println("Hello, World!")
  val xmlpath = ""
  val proStoreRecordList = new ProStoreRecordList(xmlpath)
  val recordList:List[RECORD] = proStoreRecordList.recordList
  val recordInStock:List[RECORD] = proStoreRecordList.recordInStock
  val recordRetailInStock:List[RECORD] = proStoreRecordList.recordRetailInStock
  val recordBoxesInStock2WithPrices = proStoreRecordList.recordBoxesInStock2WithPrices

  val record1:RECORD = recordList(0)
  val record2:RECORD = recordList(1)
  val record3:RECORD = recordInStock(0)
  val record4:RECORD = recordInStock(1)
  val record5:RECORD = recordInStock(2)
  val list:List[RECORD] = List[RECORD](record3.copy(SATISFIYATI1 = 100f, BAKIYE=99f),record4,record5)

  //println(sendRecordsToWeb(list))

  def updateBoxRecord(): Unit ={
    recordBoxesInStock2WithPrices.map(sendRecordToWeb(_))
  }

  def updateRetailRecord(): Unit ={
    recordRetailInStock.map(sendRecordToWeb(_))
  }



}

object comp {
  val categoryNumGenel = 99

  def sendItemJson(json:Json): HttpResponse[String] = {
    val post_url = ""
    val post = Http(post_url)
      .postData(json.toString())
      .header("content-type", "application/json")
      .header("Charset", "UTF-8")
      .option(HttpOptions.connTimeout(10000))
      .option(HttpOptions.readTimeout(50000))
      .auth(Woo.CONSUMER_KEY, Woo.CONSUMER_SECRET).asString
    post
  }

  def sendRecordToWeb(item:RECORD): String = {
    if (!isRecordExistOnWebBySku(item)){
      println("Sending: " + item.STOK_ADI)
      sendItemRecord(item)
      "Sending: " + item.STOK_ADI
    }
    else {
      updateItemRecordBySku(item)
      println("Updating: " + item.STOK_ADI)
      "Updating: " + item.STOK_ADI
    }
  }

  def sendRecordsToWeb(item:List[RECORD]) = {
    val itemOnWeb = item.filter(isRecordExistOnWebBySku(_))
    val newItems = item diff itemOnWeb
    newItems.map(sendRecordToWeb(_))
    println("Updating: " + item(0).STOK_ADI)
    batchUpdateRecords(itemOnWeb)
    "Updating: " + item(0).STOK_ADI
  }

  def deleteRecordOnWeb(item:RECORD):String = {
    if (isRecordExistOnWebBySku(item)){
      println("Deleting: " + item.STOK_ADI)
      deleteItemRecordBySku(item)
      "Deleting: " + item.STOK_ADI
    }
    else {
      println("Item is not existed already: " + item.STOK_ADI)
      "ITEM HAS NOT EXISTED ON WEB ALREADY: " + item.STOK_ADI
    }
  }

  private def sendItemRecord(item:RECORD): HttpResponse[String] = {
    val json = recordNewToJSON(item)
    val post_url = ""
    val post = Http(post_url)
      .postData(json.toString())
      .header("content-type", "application/json")
      .header("Charset", "UTF-8")
      .option(HttpOptions.connTimeout(10000))
      .option(HttpOptions.readTimeout(50000))
      .auth(Woo.CONSUMER_KEY, Woo.CONSUMER_SECRET).asString
    post
  }

  private def updateItemRecordBySku(item:RECORD): HttpResponse[String] = {
    val json:Json = recordExistedToJSON(item)
    val id:Int = getWooID(item)
    val post_url = "" + id.toString
    val jsonStringWithoutNulls:String = removeNullsFromJson(json)
    val post = Http(post_url)
      .postData(jsonStringWithoutNulls)
      .header("content-type", "application/json")
      .header("Charset", "UTF-8")
      .option(HttpOptions.connTimeout(10000))
      .option(HttpOptions.readTimeout(50000))
      .auth(Woo.CONSUMER_KEY, Woo.CONSUMER_SECRET).asString
    post
  }



  private def batchUpdateRecords(item:List[RECORD]) = {
    val woos:List[WooItem] = item.map(recordExistedToBatchWoo(_))
    val map:Map[WooItem, RECORD] = (woos zip item) toMap
    val futures = woos map (p=>getWooID(map(p)))
    //val futureSeq:Future[List[Int]] = Future.sequence(futures)
    //val t:List[Int]= Await.result(futureSeq, 1000 seconds)
    val woosWithIds = (woos zip futures).toMap
    val list = woos.map( p=> p.copy(id = Some(woosWithIds(p).toString)))
    val updateBatch = new UpdateJsonObject(list)
    val json:Json = updateBatch.asJson
    val post_url = ""
    val jsonStringWithoutNulls:String = removeNullsFromJson(json)
        val post = Http(post_url)
          .postData(jsonStringWithoutNulls)
          .header("content-type", "application/json")
          .header("Charset", "UTF-8")
          .option(HttpOptions.connTimeout(10000))
          .option(HttpOptions.readTimeout(50000))
          .auth(Woo.CONSUMER_KEY, Woo.CONSUMER_SECRET).asString
        post
  }

  private def deleteItemRecordBySku(item:RECORD): HttpResponse[String] = {
    val id:Int = getWooID(item)
    val post_url = "" + id.toString + "?force=true"
    val post = Http(post_url)
      .method("DELETE")
      .option(HttpOptions.connTimeout(10000))
      .option(HttpOptions.readTimeout(50000))
      .auth(Woo.CONSUMER_KEY, Woo.CONSUMER_SECRET).asString
    post
  }

  def isRecordBox(item:RECORD): Boolean= {
    item.BIRIM2 != ""
    //item.BIRIM2 == "KT"
  }

  def getWooID(item:RECORD): Int ={
    val response = getItemRecordBySku(item.STOK_KODU)
    val itemsListJson:List[Json] = rootJsonToList(responseToJSON(response))
    val list = itemsListJson.map(p=>toWooJsonObject(p.toString()))
    val woo = list(0).toSeq(0)
    woo.id toInt
  }

  def getItemRecordBySku(sku:String): HttpResponse[String] = {
    val get_url = "" + sku
    val get = Http(get_url)
      .header("content-type", "application/json")
      .header("Charset", "UTF-8")
      .option(HttpOptions.connTimeout(10000))
      .option(HttpOptions.readTimeout(50000))
      .auth(Woo.CONSUMER_KEY, Woo.CONSUMER_SECRET).asString
    get
  }

  def getOrders(): HttpResponse[String] = {
    val orders = Http("").auth(Woo.CONSUMER_KEY, Woo.CONSUMER_SECRET).asString
    orders
  }
  def getAllProducts() = {
    val totalPages = getProductPageCount()
    val futures:IndexedSeq[Future[List[Json]]] = (1 to totalPages) map (i=> { Future {getProductsAsJsonList(i)}})
    val futureSeq:Future[IndexedSeq[List[Json]]] = Future.sequence(futures)
    val t:IndexedSeq[List[Json]]= Await.result(futureSeq, 1000 seconds)
    val merge:List[Json] = t.reduceLeft(_ ++ _)
    val woos:List[WooJsonObject] = merge.map(p => toWooJsonObject(p.toString).toSeq(0))
    woos
  }
  def getProductsAsJsonList(page:Int = 1,per_page:Int = 100):List[Json] = {
    val products = Http(s"")
      .header("content-type", "application/json")
      .header("Charset", "UTF-8")
      .option(HttpOptions.connTimeout(10000))
      .option(HttpOptions.readTimeout(50000))
      .auth(Woo.CONSUMER_KEY, Woo.CONSUMER_SECRET).asString
    val json = responseToJSON(products)
    val list = rootJsonToList(json)
    list
  }

  def getProductCount():Int = {
    val firstProduct = Http("")
      .header("content-type", "application/json")
      .header("Charset", "UTF-8")
      .option(HttpOptions.connTimeout(10000))
      .option(HttpOptions.readTimeout(50000))
      .auth(Woo.CONSUMER_KEY, Woo.CONSUMER_SECRET).asString
    val header = firstProduct.headers.filter(_._1 == "X-WP-Total" )
    toInt(header.head._2(0)).getOrElse(0)
  }
  def getProductPageCount(perPage:Int = 100): Int = {
    val per_page = perPage
    val total = getProductCount()
    val totalPage = (total / per_page) + 1
    totalPage
  }

  def toInt(s: String): Option[Int] = {
    try {
      Some(s.toInt)
    } catch {
      case e: Exception => None
    }
  }

  def getProductByID(id:Int): HttpResponse[String] = {
    val product = Http("" + id.toString).auth(Woo.CONSUMER_KEY, Woo.CONSUMER_SECRET).asString
    product

  }


  def isRecordExistOnWebBySku(item:RECORD): Boolean = {
    println("Controlling: " + item.STOK_ADI)
    getItemRecordBySku(item.STOK_KODU).body != "[]"
  }


  def recordNewToJSON(item:RECORD): Json = {
    val stockName = item.STOK_ADI trim
    val status = Woo.STATUS_DRAFT
    val price = if(!isRecordBox(item)) item.SATISFIYATI1 toString else item.BIRIM2SATISFIYATI toString
    val manageStock = Woo.STOCK_MANAGEMENT_TRUE
    val quantity = if (item.BAKIYE.toInt < 0) 0 else {
      if(!isRecordBox(item)) item.BAKIYE.toInt else getBoxQuantityFromRecord(item)
    }
    val sku = item.STOK_KODU
    val imgDefaultId = 1172
    val fiyatDegisimTarihi = new Meta_data3("date_1",item.FIYAT_DEGISIM_TARIHI)
    val gtin = new Meta_data3("gtin_hemi", if(!isRecordBox(item)) item.BARKOD toString else item.BIRIM2BARKODU toString)
    val uretimYeri = new Meta_data3("text_2",item.URETIM_YERI_NO)
    val yerliUretim = new Meta_data3("checkbox_3", if (item.URETIM_YERI_NO == "Türkiye") "1" else "0")
    val metaDataList:List[Meta_data3] = List[Meta_data3](fiyatDegisimTarihi,uretimYeri,yerliUretim,gtin)
    val woo = new WooItem(None,Some(stockName), Some("simple"), Some(status), price, Some(stockName), Some(stockName), Some(sku), Some(manageStock), quantity, Some(List(new Categories(id = categoryNumGenel))), Some(List(new Images(id = imgDefaultId))),Some(metaDataList))
    woo.asJson
  }

  def recordExistedToWooItem(item:RECORD):WooItem = {
    val correspondingWebItem = findCorrespondingWebItem(item)
    //If corresponding item exists

    def price : String = {
      if(correspondingWebItem.nonEmpty) priceThatMustBeOnWeb(correspondingWebItem.get)
      else {
        if(!isRecordBox(item)) item.SATISFIYATI1 toString else item.BIRIM2SATISFIYATI toString
      }
    }

    def quantity = {
      if(correspondingWebItem.nonEmpty) quantityThatMustBeOnWeb(correspondingWebItem.get)
      else {
        if (item.BAKIYE.toInt < 0) 0 else {
          if (!isRecordBox(item)) item.BAKIYE.toInt else getBoxQuantityFromRecord(item)
        }
      }

    }

    //If item is not published and name is different
    def nameOnTheWeb:Option[String] = {
      //Eger corresponding bos degilse yani varsa
      if(correspondingWebItem.nonEmpty) {
        // Eger yayinlanmis bir urunse isim degisikligi yapma
        if(correspondingWebItem.get.status == "publish") {
          return None
        }
        // Eger yayinlanmis bir uurn degilse ismi kontrol et
        else {
          val webItemName = correspondingWebItem.get.name
          // Eger esit degilse esitle
          if(webItemName != item.STOK_ADI.trim) {
            return Some(item.STOK_ADI.trim)
          } else {return None}  //Esitse esitleme
        }

      } else {
        return None
      }
    }
    val fiyatDegisimTarihi = new Meta_data3("date_1",item.FIYAT_DEGISIM_TARIHI)
    val uretimYeri = new Meta_data3("text_2",item.URETIM_YERI_NO)
    val yerliUretim = new Meta_data3("checkbox_3", if (item.URETIM_YERI_NO == "Türkiye") "1" else "0")
    val gtin = new Meta_data3("gtin_hemi", if(!isRecordBox(item)) item.BARKOD toString else item.BIRIM2BARKODU toString)
    //val prostoreStockName = new Meta_data3("prostore_stok_ismi", item.STOK_ADI)
    val metaDataList:List[Meta_data3] = List[Meta_data3](fiyatDegisimTarihi,uretimYeri,yerliUretim,gtin)

    //val name = item.STOK_ADI
    //val wooWithName = new WooItem(None, Some(name), None, None, price, Some(name), Some(name), None, None, quantity, None, None,Some(metaDataList))
    val woo = new WooItem(None, nameOnTheWeb, None, None, price, None, None, None, None, quantity, None, None,Some(metaDataList))

    woo
  }

  def recordExistedToJSON(item:RECORD): Json = {
    val wooItem = recordExistedToWooItem(item)
    wooItem.asJson
  }

  def getBoxQuantityFromRecord(item:RECORD): Int = {
    val boxQuantity = item.BAKIYE.toInt / item.KAT2.toInt
    boxQuantity
  }


  def recordExistedToBatchWoo(item:RECORD): WooItem = {
    val price = if(!isRecordBox(item)) item.SATISFIYATI1 toString else item.BIRIM2SATISFIYATI toString
    val quantity = if (item.BAKIYE.toInt < 0) 0 else {
      if(!isRecordBox(item)) item.BAKIYE.toInt else getBoxQuantityFromRecord(item)
    }
    val fiyatDegisimTarihi = new Meta_data3("date_1",item.FIYAT_DEGISIM_TARIHI)
    val uretimYeri = new Meta_data3("text_2",item.URETIM_YERI_NO)
    val yerliUretim = new Meta_data3("checkbox_3", if (item.URETIM_YERI_NO == "Türkiye") "1" else "0")
    val metaDataList:List[Meta_data3] = List[Meta_data3](fiyatDegisimTarihi,uretimYeri,yerliUretim)
    val woo = new WooItem(None, None, None, None, price, None, None, None, None, quantity, None, None,Some(metaDataList))
    //woo.asJson
    woo
  }

  def responseToJSON(response:HttpResponse[String]): Json = {
    val bytes: Array[Byte] = response.body.getBytes(StandardCharsets.UTF_8)
    val responseBody: String = response.body
    val json: Json = parse(responseBody).getOrElse(Json.Null)
    json
  }

  def rootJsonToList(rootJson:Json): List[Json] = {
    val items = root.each.json
    val itemListJSON:List[Json] = items.getAll(rootJson)
    itemListJSON
  }

  def toWooJsonObject(s:String) = {
    decode[WooJsonObject](s)
  }

  def toWooItem(woo:WooJsonObject):WooItem = {
    val item = new WooItem(None,None, None, None, woo.price, None, None, Some(woo.sku), None, woo.stock_quantity.getOrElse(0), None, None)
    item
  }

  def removeNullsFromJson(json:Json):String = {
    json.pretty(Printer.noSpaces.copy(dropNullValues = true))
  }

  def findCorrespondingWebItem(record:RECORD):Option[WooJsonObject] = {
    val webItems = SwingApp.woos
    val correspondingWooItem = webItems.filter(_.sku == record.STOK_KODU)

    if (correspondingWooItem.length > 0)
      return Some(correspondingWooItem.head)
    else
      return None
  }

  def findCorrespondingRecord(webItem:WooJsonObject):Option[RECORD] = {
    val recordList = SwingApp.recordList
    val correspondingRecord = recordList.filter(_.STOK_KODU == webItem.sku)

    if (correspondingRecord.length > 0)
      return Some(correspondingRecord.head)
    else
      return None
  }



  def priceThatMustBeOnWeb(woo:WooJsonObject) : String = {
    val wooMetaData1:List[Meta_data3] = woo.meta_data
      .filter{p => p.isInstanceOf[Meta_data1]}.map{_.asInstanceOf[Meta_data1]}
      .map{i=>new Meta_data3(i.key,i.value)}

    val record = findCorrespondingRecord(woo).get

    //Eger prostore_satis_birimi adli bir meta_data varsa
    if (wooMetaData1.filter{_.key == "prostore_satis_birimi"}.length > 0 ) {
      val prostoreSatisBirimiMetaData : Meta_data3 = wooMetaData1.filter{_.key == "prostore_satis_birimi"}.head
      val value = prostoreSatisBirimiMetaData.value
      //TODO hata cikabilir getden dolayi

      val priceMatch = value match {
        case "1" => record.SATISFIYATI1
        case "2" => record.BIRIM2SATISFIYATI
        case "3" => record.BIRIM3SATISFIYATI
        case _ => record.SATISFIYATI1
      }
      return priceMatch.toString
    }
    //Eger prostore_satis_birimi adli bir meta_data yoksa
    else {
      if(!isRecordBox(record)) record.SATISFIYATI1 toString
      else record.BIRIM2SATISFIYATI toString
    }
  }

  def quantityThatMustBeOnWeb(woo:WooJsonObject) : Int = {
    val wooMetaData1:List[Meta_data3] = woo.meta_data
      .filter{p => p.isInstanceOf[Meta_data1]}.map{_.asInstanceOf[Meta_data1]}
      .map{i=>new Meta_data3(i.key,i.value)}

    //TODO hata cikabilir getden dolayi
    val record = findCorrespondingRecord(woo).get

    //Eger prostore_satis_birimi adli bir meta_data varsa
    if (wooMetaData1.filter{_.key == "prostore_satis_birimi"}.length > 0 ) {
      val prostoreSatisBirimiMetaData : Meta_data3 = wooMetaData1.filter{_.key == "prostore_satis_birimi"}.head
      val value = prostoreSatisBirimiMetaData.value


      val quantityMatch = value match {
        case "1" => record.BAKIYE
        case "2" => record.BAKIYE / record.KAT2.toInt
        case "3" => record.BAKIYE / record.KAT3.toInt
        case _ => record.BAKIYE
      }
      return if(quantityMatch > 0) quantityMatch.toInt else 0
    }
    //Eger prostore_satis_birimi adli bir meta_data yoksa
    else {
      if (record.BAKIYE.toInt < 0) 0 else {
        if(!isRecordBox(record)) record.BAKIYE.toInt else comp.getBoxQuantityFromRecord(record)
      }
    }
  }

  val jsonstr = ""
  val jsonstr2 = ""
  val jsonstr3 = ""
  val default_img = ""

}


class ProStoreRecordList {
  var recordList:List[RECORD] = List[RECORD]()
  def this(xmlPath : String) = {
    this()
    this.recordList = xmlToRecords(xmlPath)
  }

  def this(list:List[RECORD]) = {
    this()
    this.recordList = list
  }


  def xmlToRecords(path:String):List[RECORD] = {
    val xml:Elem = XML.load(path)
    val parsedAddress:LIST = scalaxb.fromXML[LIST](xml)
    val recordList:List[RECORD] = parsedAddress.RECORD.toList
    recordList
  }

  def recordInStock:List[RECORD] = {
    val recordInStock:List[RECORD] = recordList.filter(p=> p.BAKIYE > 0)
    recordInStock
  }

  def recordBoxesInStock :List[RECORD] = {
    recordInStock.filter(p=> isRecordBox(p))
  }

  def recordBoxesInStock2WithPrices :List[RECORD] = {
    recordBoxesInStock.filter(p=> p.BIRIM2SATISFIYATI.length > 0)
  }

  def recordRetailInStock :List[RECORD] = recordInStock diff recordBoxesInStock2WithPrices
}