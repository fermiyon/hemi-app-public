package com.selmank
import io.circe.{Decoder, Json, parser}
import io.circe.optics.JsonPath.root
import io.circe.parser.parse
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.jawn.decode

case class Categories(
                       id: Double
                     )

case class Images(
                   id: Int
                 )

case class WooItem(id:Option[String],name: Option[String], `type`: Option[String], status: Option[String], regular_price: String, description: Option[String], short_description: Option[String], sku: Option[String], manage_stock: Option[Boolean], stock_quantity: Int, categories: Option[List[Categories]], images: Option[List[Images]], meta_data: Option[List[Meta_data3]] = None)



object Woo {
  val STATUS_PUBLISH = "publish"
  val STATUS_DRAFT = "draft"
  val STATUS_PENDING = "pending"
  val STATUS_PRIVATE = "private"
  val STOCK_MANAGEMENT_TRUE = true
  val STOCK_MANAGEMENT_FALSE = false
  val CONSUMER_KEY = ""
  val CONSUMER_SECRET = ""
  val HTTP_CODE_200 = "OK"
  val HTTP_CODE_201 = "CREATED"

}

case class Dimensions(
                       length: String,
                       width: String,
                       height: String
                     )
case class WooCategories(
                       id: Double,
                       name: String,
                       slug: String
                     )
case class WooImages(
                   id: Double,
                   date_created: String,
                   date_created_gmt: String,
                   date_modified: String,
                   date_modified_gmt: String,
                   src: String,
                   name: String,
                   alt: String
                 )
case class Value(
                  _bubble_new: String,
                  _bubble_text: String,
                  _custom_tab_title: String,
                  _custom_tab: String,
                  _product_video: String,
                  _product_video_size: String,
                  _product_video_placement: String,
                  _top_content: String,
                  _bottom_content: String
                )

import io.circe._, io.circe.generic.JsonCodec, io.circe.syntax._
sealed trait Meta_data

 case class Meta_data1(
                      id: Double,
                      key: String,
                      value: String
                    ) extends  Meta_data
case class Meta_data2(
                      id: Double,
                      key: String,
                      value: List[Value]
                    ) extends Meta_data

case class Meta_data3(
                       key: String,
                       value: String
                     )


object Meta_data {
  implicit val decodeA: Decoder[Meta_data] = Decoder[Meta_data1].map[Meta_data](identity).or(Decoder[Meta_data2].map[Meta_data](identity))
  implicit val encodeA: Encoder[Meta_data] = Encoder.instance {
    case b @ Meta_data1(id,key,string) => b.asJson
    case c @ Meta_data2(id,key,string) => c.asJson
  }
}

case class Self(
                 href: String
               )
case class _links(
                   self: List[Self],
                   collection: List[Self]
                 )

case class WooJsonObject(
                           id: Double,
                           name: String,
                           slug: String,
                           permalink: String,
                           date_created: String,
                           date_created_gmt: String,
                           date_modified: String,
                           date_modified_gmt: String,
                           `type`: String,
                           status: String,
                           featured: Boolean,
                           catalog_visibility: String,
                           description: String,
                           short_description: String,
                           sku: String,
                           price: String,
                           regular_price: String,
                           sale_price: String,
                           date_on_sale_from: Option[String],
                           date_on_sale_from_gmt: Option[String],
                           date_on_sale_to: Option[String],
                           date_on_sale_to_gmt: Option[String],
                           price_html: String,
                           on_sale: Boolean,
                           purchasable: Boolean,
                           total_sales: Double,
                           virtual: Boolean,
                           downloadable: Boolean,
                           downloads: Option[List[String]],
                           download_limit: Double,
                           download_expiry: Double,
                           external_url: String,
                           button_text: String,
                           tax_status: String,
                           tax_class: String,
                           manage_stock: Boolean,
                           stock_quantity: Option[Int],
                           stock_status: String,
                           backorders: String,
                           backorders_allowed: Boolean,
                           backordered: Boolean,
                           sold_individually: Boolean,
                           weight: String,
                           dimensions: Dimensions,
                           shipping_required: Boolean,
                           shipping_taxable: Boolean,
                           shipping_class: String,
                           shipping_class_id: Double,
                           reviews_allowed: Boolean,
                           average_rating: String,
                           rating_count: Double,
                           related_ids: List[Double],
                           upsell_ids: Option[List[Int]],
                           cross_sell_ids: Option[List[Int]],
                           parent_id: Double,
                           purchase_note: String,
                           categories: List[WooCategories],
                           tags: Option[List[String]],
                           images: List[WooImages],
                           attributes: Option[List[String]],
                           default_attributes: Option[List[String]],
                           variations: Option[List[Int]],
                           grouped_products: Option[List[Int]],
                           menu_order: Double,
                           meta_data: List[Meta_data],
                           _links: _links
                         )

case class UpdateJsonObject(
                           update: List[WooItem]
                         )