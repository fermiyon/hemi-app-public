package com.selmank

import java.awt.datatransfer.DataFlavor
import java.awt.dnd.{DnDConstants, DropTarget, DropTargetDragEvent, DropTargetDropEvent, DropTargetEvent, DropTargetListener}

import scala.swing.FileChooser.{Result, SelectionMode}
import swing._
import swing.event._
import java.io.File
import java.util.Arrays.ArrayList

import com.selmank.SwingApp.{proStoreRecords, publishedItems, recordInStock, recordList, stockZeroItems, trashedItems, woos, xmlpath}
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.{JComponent, JFrame, TransferHandler, UIManager}

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import scala.concurrent.duration._
import com.selmank._
import io.circe.Json
import gnieh.diffson.circe._
import scalaj.http.{Http, HttpOptions, HttpResponse}

import scala.swing.GridBagPanel.{Anchor, Fill}
import io.circe.{Json, Printer, parser}
import io.circe.optics.JsonPath.root
import io.circe.parser.parse
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.jawn.decode
import View._
import Actions._
import Controller._
import com.bulenkov.darcula.DarculaLaf
import scala.collection.JavaConversions._

object SwingApp extends SimpleSwingApplication {
  var xmlpath: String = ""
  var proStoreRecords: ProStoreRecordList = null
  var recordList: List[RECORD] = List[RECORD]()
  var recordInStock: List[RECORD] = List[RECORD]()
  var woos: List[WooJsonObject] = List[WooJsonObject]()

  val darcula = new DarculaLaf()
  UIManager.setLookAndFeel(darcula)


  //UI arrangements

  def top = new MainFrame {
    title = s"Hemi Gıda - Prostore Entegrasyonu ${AppProperties.APP_VERSION}"
    preferredSize = new Dimension(AppProperties.FRAME_WIDTH, AppProperties.FRAME_HEIGHT)
    contents = ui
  }

  def labelString(s: String) = {
    val max = AppProperties.MAX_LABEL_SIZE
    if (s.length > max) s.take(max) + "..."
    else s
  }

  Controller.init

  var trashedItems: List[WooJsonObject] = List[WooJsonObject]()
  var stockZeroItems: List[WooJsonObject] = List[WooJsonObject]()
  var publishedItems: List[WooJsonObject] = List[WooJsonObject]()

}

object Actions {
  lazy val updateButtonClicked = {
    //Analyze Records
    proStoreRecords = new ProStoreRecordList(xmlpath)
    recordList = proStoreRecords.recordList
    recordInStock = proStoreRecords.recordInStock
    progressBar.visible = true
    //Remove Digits from the title of records
    //val recordsProcessed = recordInStock.map(ProstoreUtils.addBoxQuantityRemoveFirstDigits(_))
    val recordsProcessed = recordList.map(ProstoreUtils.addBoxQuantityRemoveFirstDigits(_))
    var recordsToBeSent : List[RECORD] = null
    label.text = "Eşitleniyor - Prostore ile Site'yi karşılaştırmak için Site'den ürünler indiriliyor..."
    val woosFuture: Future[List[WooJsonObject]] = Future {
      comp.getAllProducts()
    }

    val totalProductCount = recordsProcessed.length
    progressBar.max = totalProductCount
    progressBar.value = 0
    var completed = 0
    var failed = 0
    var notNeedToBeUpdated = 0

    woosFuture.onComplete {
      case Success(value) => {
        val p = woos.map{i=>ProstoreUtils.skus_trashed_list.contains(i.sku)}.filter{_ == true}
        println(s"Trashed : $p")
        woos = value
        recordsToBeSent = recordsProcessed.filter(i => isRecordNeedsToBeSentToWeb(i))
        notNeedToBeUpdated = recordsProcessed.length - recordsToBeSent.length
        //Esitlenecek bir urun yoksa
        if(recordsProcessed.length == notNeedToBeUpdated) {
          label.text = s"Prostore ile Internet Sitesi Eşit. - Toplam ürün sayısı: ${notNeedToBeUpdated}"
          completed += notNeedToBeUpdated
          print(s"Not need to be updated $notNeedToBeUpdated")
          progressBar.value = completed
        }
        else {
          label.text = s"Eşitleniyor - ${notNeedToBeUpdated} ürün prostore ile aynı. ${recordsToBeSent.length} ürün güncellenecek."
          completed += notNeedToBeUpdated
          print(s"Not need to be updated $notNeedToBeUpdated")
          //Start futures
          val k = futures
        }

      }
    }


    def futures: IndexedSeq[Future[String]] =
      (0 until recordsToBeSent.length) map (i => {
        val myFuture = Future {
          comp.sendRecordToWeb(recordsToBeSent(i))
        }
        myFuture.onComplete {
          case Success(value) => {
            completed += 1
            println(completed)
            Swing.onEDT {
              label.text = if (completed != totalProductCount) s"Updating ${completed} " else "Tamamlandı"
              progressLabel.text = s"$completed/$totalProductCount"
              progressBar.value = completed
              //Eger urun esitlemesi tamamlandiysa
              if (completed == totalProductCount) {
                updateiOSJson
              }
            }
          }
          case Failure(e) => {
            failed += 1
            progressLabel.text = s"$failed"
            e.printStackTrace
          }
        }
        myFuture
      })

    fileButton.enabled = false
    button.enabled = false

    fileButton.visible = false
    button.visible = false
  }

  lazy val updateiOSJson = {
    val responseFuture = Future {
      Http("Cloud App urlsi")
        .timeout(200000, 500000)
        .auth("", "")
    }
    progressLabel.text = ""
    progressBar.indeterminate = true
    label.text = "Hemi iOS app güncelleniyor..."

    responseFuture.onComplete{
      case Success(value) => {
        val responseCode = value.asString.code
        progressBar.indeterminate = false
        //Eger basarili ise
        if (responseCode == 200) {
          label.text = "Tamamlandı."
        } else {
          label.text = "Hemi iOS App güncellenemedi. Lütfen tekrar deneyiniz."
        }
      }
      case Failure(e) => {
        progressBar.indeterminate = false
        e.printStackTrace()
      }
    }
  }

  def isRecordNeedsToBeSentToWeb(record: RECORD): Boolean = {
    val recordSKU = record.STOK_KODU
    val isRecordBox = comp.isRecordBox(record)

    //Find WooItem correspoding to record if it exist
    val correspondingWooItem = woos.filter(_.sku == recordSKU)
    if(correspondingWooItem.length > 0) {
      val woo = correspondingWooItem.head

      /** Meta data list of the web item
      Get the list of meta data of corresponding web item and convert the list to Meta data 3 format **/

      val wooMetaData1:List[Meta_data3] = woo.meta_data
        .filter{p => p.isInstanceOf[Meta_data1]}.map{_.asInstanceOf[Meta_data1]}
        .map{i=>new Meta_data3(i.key,i.value)}


      val priceThatMustBe = comp.priceThatMustBeOnWeb(woo)
      val priceEquality = (woo.price == priceThatMustBe)

      val quantityThatMustBe = comp.quantityThatMustBeOnWeb(woo)
      val quantityEquality = woo.stock_quantity.getOrElse(0) == quantityThatMustBe

      val fiyatDegisimTarihi = new Meta_data3("date_1",record.FIYAT_DEGISIM_TARIHI)
      val uretimYeri = new Meta_data3("text_2",record.URETIM_YERI_NO)
      val yerliUretim = new Meta_data3("checkbox_3", if (record.URETIM_YERI_NO == "Türkiye") "1" else "0")
      val gtin = new Meta_data3("gtin_hemi", if(!isRecordBox) record.BARKOD toString else record.BIRIM2BARKODU toString)
      //val prostoreStockName = new Meta_data3("prostore_stok_ismi",record.STOK_ADI)
      //Can be added -> prostoreStockName
      val metaDataList:List[Meta_data3] = List[Meta_data3](fiyatDegisimTarihi,uretimYeri,yerliUretim,gtin)
      val metaDataEquality = {
        val wooMetaData = woo.meta_data
        print(wooMetaData)

        //TODO CHECK META DATA EQUALITY
        if(wooMetaData1.length > 0) {
          //checking is All true
          val bool:Boolean = metaDataList.map { i => wooMetaData1.contains(i) }.reduceLeft(_ && _)
          bool
        }
        else {false}
      }

      def nameEquality:Boolean = {
        //Eger urun internette yayinlanmis bir urunse isimleri esit kabul et(karisma)
        if (woo.status == "publish") {return true}
        //Eger urun internette yayinlanmamissa
        else {
          //isimler prostore la esitse guncelleme yapma
          if(woo.name == record.STOK_ADI.trim) {return true}
          //esit degilse guncelleme yap
          else {return false}
        }
      }

      // If all parameters is equal than return false(not to be updated) else retirn true(needs to be updated)
      println(s"${record.STOK_ADI} $priceEquality,$quantityEquality,$metaDataEquality")
      return !List(nameEquality,priceEquality,quantityEquality,metaDataEquality).reduceLeft(_ && _)
    }

    else {
      // Yes it needs to be updated
      return true
    }


  }
}

object Controller {
  def init: Unit = {
    val dt = new DropTarget()
    ui.peer.setDropTarget(dt)
    val dtListener = new DropTargetListener {
      override def dragEnter(dtde: DropTargetDragEvent): Unit = {

      }

      override def dragOver(dtde: DropTargetDragEvent): Unit = {

      }

      override def dropActionChanged(dtde: DropTargetDragEvent): Unit = {

      }

      override def dragExit(dte: DropTargetEvent): Unit = {

      }

      override def drop(dtde: DropTargetDropEvent): Unit = {
        dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE)
        val t = dtde.getTransferable()
        val fileList = t.getTransferData(DataFlavor.javaFileListFlavor).asInstanceOf[java.util.List[File]].toList
        val file = fileList.head
        //Check if the file is XML or not
        isXML(file) match {
          case true => {
            button.enabled = true
            button.opaque = true
            button.contentAreaFilled = true
            button.foreground = Colors.BLUE
            button.requestFocus()
            selectedFile = file
            val path: String = selectedFile.getPath.trim.replaceAll("\\u0020", "%20")
            label.text = "Eşitlemek için hazır"
            xmlpath = path
            //Remove drop Target
            ui.peer.setDropTarget(null)
          }
          case false => {
            label.text = "Eşitlemek için bir XML dosyasını sürukleyip bırakın veya açın."
          }
        }

      }
    }
    dt.addDropTargetListener(dtListener)
  }

  def isXML(file:File):Boolean = {
    val path: String = file.getPath.trim.replaceAll("\\u0020", "%20")
    val list = path.split("\\.")
    list.last match {
      case "xml" => return true
      case _ => return false
    }

  }
}

object View {
  val ui = new GridBagPanel {
    val c = new Constraints
    val shouldFill = true
    if (shouldFill) {
      c.fill = Fill.Horizontal
    }
    var numclicks = 0


    button

    c.weightx = 0.9
    c.fill = Fill.Horizontal
    c.gridx = 0
    c.gridy = 0
    c.anchor = Anchor.PageStart
    c.insets = new Insets(10, 0, 0, 0) //top padding
    layout(button) = c


    fileButton

    c.weightx = 0.1
    c.fill = Fill.Horizontal
    c.gridx = 1
    c.gridy = 0
    layout(fileButton) = c

    progressBar

    c.weightx = 0.0
    c.fill = Fill.Horizontal
    c.gridwidth = 3
    c.insets = new Insets(3, 5, 0, 5) //top padding
    c.gridx = 0
    c.gridy = 1
    layout(progressBar) = c




    label

    dLabel = label
    c.weightx = 0.9
    c.fill = Fill.Horizontal
    c.gridx = 0
    c.gridy = 2
    layout(label) = c

    progressLabel



    dLabel = label
    c.weightx = 0.07
    c.fill = Fill.Horizontal
    c.gridx = 1
    c.gridy = 2
    layout(progressLabel) = c

    failLabel

    c.weightx = 0.03
    c.fill = Fill.Horizontal
    c.gridx = 2
    c.gridy = 2
    layout(failLabel) = c
  }

  object button extends Button {
    text = "Prostore  ⇄  İnternet sitesi Eşitle"
    opaque = false
    contentAreaFilled = false
    enabled = false
    reactions += {
      case ButtonClicked(_) =>
        updateButtonClicked
    }
  }

  object fileButton extends Button {
    text = "Aç"
    reactions += {
      case ButtonClicked(fileButton) =>
        label.text = "File Open"
        fileChooser.showOpenDialog(this) match {
          case Result.Cancel => label.text = "Canceled"
          case Result.Approve => {
            button.enabled = true
            button.opaque = true
            button.contentAreaFilled = true
            button.foreground = Colors.BLUE
            button.requestFocus()
            selectedFile = fileChooser.selectedFile
            val path: String = selectedFile.getPath.trim.replaceAll("\\u0020", "%20")
            label.text = "Eşitlemek için hazır"
            xmlpath = path
          }
        }
      //N11Productsv2.init()
    }
  }


  object progressBar extends ProgressBar {
    min = 0
    max = 10
    value = 0
    visible = false
  }

  object progressLabel extends Label {
    val prefix = ""
    text = prefix
  }

  object label extends Label {
    val prefix = "Hemi Gıda"
    text = prefix

  }

  object failLabel extends Label {
    val prefix = ""
    text = prefix
    foreground = Colors.RED
  }

  object fileChooser extends FileChooser {
    fileSelectionMode = SelectionMode.FilesOnly
    fileFilter = new FileNameExtensionFilter("XML Dosyası", "xml")
  }


  var dLabel: Label = null
  var selectedFile: File = null
}

object DeprecatedActions {
  lazy val updateButtonClicked2 = {
    //Analyze Records
    //Swap draft and pending

    label.text = "Eşitleniyor - Web'den ürünler indiriliyor..."
    val woosFuture: Future[List[WooJsonObject]] = Future {
      comp.getAllProducts()
    }

    var totalProductCount = 0
    progressBar.max = totalProductCount
    progressBar.value = 0
    var completed = 0
    var failed = 0

    woosFuture.onComplete {
      case Success(value) => {
        woos = value
        totalProductCount = woos.length
        progressBar.max = totalProductCount
        progressBar.value = 0

        label.text = s"Eşitleniyor -  ${woos.length} ürün güncellenecek."
        //Start futures
        val k = futures2


      }
    }


    def futures2: IndexedSeq[Future[String]] =
      (0 until woos.length) map (i => {
        val item = woos(i)
        val status = item.status


        val newStatus =
          if(status == Woo.STATUS_PENDING) {
            Woo.STATUS_DRAFT
          } else if (status == Woo.STATUS_DRAFT) {Woo.STATUS_PENDING}
          else {status}

        val woo = new WooItem(None, None, None, Some(newStatus), item.regular_price, None, None, None, None, item.stock_quantity.getOrElse(0), None, None, None)

        val myFuture = Future {
          val json:Json = woo.asJson
          val id:Int = item.id toInt
          val post_url = "" + id.toString
          val jsonStringWithoutNulls:String = comp.removeNullsFromJson(json)
          val post = Http(post_url)
            .postData(jsonStringWithoutNulls)
            .header("content-type", "application/json")
            .header("Charset", "UTF-8")
            .option(HttpOptions.connTimeout(10000))
            .option(HttpOptions.readTimeout(50000))
            .auth(Woo.CONSUMER_KEY, Woo.CONSUMER_SECRET).asString
          post
          s"Sending $completed"
        }
        myFuture.onComplete {
          case Success(value) => {
            completed += 1
            println(completed)
            Swing.onEDT {
              label.text = if (completed != totalProductCount) s"Updating $completed" else "Tamamlandı"
              progressLabel.text = s"$completed/$totalProductCount"
              progressBar.value = completed
            }
          }
          case Failure(e) => {
            failed += 1
            progressLabel.text = s"$failed"
            e.printStackTrace
          }
        }
        myFuture
      })

    fileButton.enabled = false
    button.enabled = false
  }

  lazy val updateButtonClicked3 = {
    //Analyze Records
    //Swap draft and pending

    label.text = "Eşitleniyor - Web'den ürünler indiriliyor..."
    val woosFuture: Future[List[WooJsonObject]] = Future {
      comp.getAllProducts()
    }

    var totalProductCount = 0
    progressBar.max = totalProductCount
    progressBar.value = 0
    var completed = 0
    var failed = 0

    woosFuture.onComplete {
      case Success(value) => {
        woos = value
        val trashed = ProstoreUtils.skus_trashed_list
        val list_1 = woos.filter(i => i.stock_quantity.getOrElse(0) < 1)
        val list_2 = woos.filter(i=> trashed.contains(i.sku))
        val list_3 = woos.filter(i=> i.status == Woo.STATUS_PUBLISH)
        trashedItems = list_2
        stockZeroItems = list_1
        publishedItems = list_3

        woos = woos diff (trashedItems ++ stockZeroItems ++ publishedItems)
        totalProductCount = woos.length
        progressBar.max = totalProductCount
        progressBar.value = 0

        label.text = s"Eşitleniyor -  ${woos.length} ürün güncellenecek."
        //Start futures
        val k = futures3


      }
    }


    def futures3: IndexedSeq[Future[String]] =
      (0 until woos.length) map (i => {
        val item = woos(i)
        val status = item.status
        val newStatus = Woo.STATUS_PENDING

        val woo = new WooItem(None, None, None, Some(newStatus), item.regular_price, None, None, None, None, item.stock_quantity.getOrElse(0), None, None, None)

        val myFuture = Future {
          val json:Json = woo.asJson
          val id:Int = item.id toInt
          val post_url = "" + id.toString
          val jsonStringWithoutNulls:String = comp.removeNullsFromJson(json)
          val post = Http(post_url)
            .postData(jsonStringWithoutNulls)
            .header("content-type", "application/json")
            .header("Charset", "UTF-8")
            .option(HttpOptions.connTimeout(10000))
            .option(HttpOptions.readTimeout(50000))
            .auth(Woo.CONSUMER_KEY, Woo.CONSUMER_SECRET).asString
          post
          s"Sending $completed"
        }
        myFuture.onComplete {
          case Success(value) => {
            completed += 1
            println(completed)
            Swing.onEDT {
              label.text = if (completed != totalProductCount) s"Updating $completed" else "Tamamlandı"
              progressLabel.text = s"$completed/$totalProductCount"
              progressBar.value = completed
            }
          }
          case Failure(e) => {
            failed += 1
            progressLabel.text = s"$failed"
            e.printStackTrace
          }
        }
        myFuture
      })

    fileButton.enabled = false
    button.enabled = false
  }
}


object Utils {
  def removeDigits(str: String) = {
    //val t = "4333 500 GR.İÇİM BEYAZ PEYNİR"
    val list = str.split(" ")
    val arrangedText = if (isAllDigit(list(0))) list.drop(1).mkString(" ") else list.mkString(" ")
    arrangedText
  }

  def isAllDigit(te: String) = te.filterNot(_.isDigit).length == 0

}

object ProstoreUtils {
  def addBoxQuantity(record: RECORD) = {
    val name = if (comp.isRecordBox(record)) s"${record.STOK_ADI} (${record.KAT2} adet)" else record.STOK_ADI
    record.copy(STOK_ADI = name)
  }

  def addBoxQuantityRemoveFirstDigits(record: RECORD) = {
    val digitsRemoved = Utils.removeDigits(record.STOK_ADI)
    val name = if (comp.isRecordBox(record)) s"${digitsRemoved} (${record.KAT2} adet)" else digitsRemoved
    record.copy(STOK_ADI = name)
  }

  val skus_trashed = ""
  val skus_trashed_list = skus_trashed.split(" ")
}

object AppProperties {
  val MAX_LABEL_SIZE = 100
  val TYPE = "batch"
  val BATCH_CHUNK_SIZE = 20
  val APP_VERSION = "v1.3.2"
  val FRAME_WIDTH = 550
  val FRAME_HEIGHT = 130
}