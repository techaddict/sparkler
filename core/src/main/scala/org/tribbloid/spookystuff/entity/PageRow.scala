package org.tribbloid.spookystuff.entity

import java.util

import org.tribbloid.spookystuff.entity.client.{Action, Visit, Wget}
import org.tribbloid.spookystuff.factory.PageBuilder
import org.tribbloid.spookystuff.operator.{JoinType, LeftOuter, Merge, Replace}
import org.tribbloid.spookystuff.{Const, SpookyContext}

import scala.collection.mutable.ArrayBuffer

/**
 * Created by peng on 8/29/14.
 */
//TODO: verify this! document is really scarce
//The precedence of an inﬁx operator is determined by the operator’s ﬁrst character.
//Characters are listed below in increasing order of precedence, with characters on
//the same line having the same precedence.
//(all letters)
//|
//^
//&
//= !.................................................(new doc)
//< >
//= !.................................................(old doc)
//:
//+ -
//* / %
//(all other special characters)
//now using immutable pattern to increase maintainability
//put all narrow transformation closures here
case class PageRow(
                    cells: Map[String, Any] = Map(),
                    pages: Seq[Page] = Seq(),
                    actions: Seq[Action] = Seq(),
                    dead: Boolean = false
                    )
  extends Serializable {

  def +>(a: Action): PageRow = {
    if (!this.dead) {
      this.copy(actions = this.actions :+ a.interpolateFromMap(cells))
    }
    else {
      this
    }
  }

  def +>(as: Seq[Action]): PageRow = {
    if (!this.dead) {
      this.copy(actions = this.actions ++ as.map(_.interpolateFromMap(cells)))
    }
    else {
      this
    }
  }

  def +>(pr: PageRow): PageRow = {
    if (!this.dead) {
      this.copy(
        cells = this.cells ++ pr.cells,
        pages = this.pages ++ pr.pages,
        actions = this.actions ++ pr.actions.map(_.interpolateFromMap(cells)),
        dead = pr.dead
      )
    }
    else {
      this
    }
  }

  def die(): PageRow = {
    this.copy(dead = true)
  }

  def +*>(actions: Seq[_]): Array[PageRow] = {
    val results: ArrayBuffer[PageRow] = ArrayBuffer()

    for (action <- actions) {
      action match {
        case a: Action => results += (this +> a)
        case sa: Seq[_] => results += (this +> sa.filter(_.isInstanceOf[Action]).asInstanceOf[Seq[Action]])
        case pr: PageRow => results += (this +> pr)
        //        case am: (ClientAction, Map[String, Any]) => results += (this +> am._1).copy(cells = this.cells ++ am._2)
        //        case sam: (Seq[ClientAction], Map[String, Any]) => results += (this +> sam._1).copy(cells = this.cells ++ sam._2)
        case _ => throw new UnsupportedOperationException("Can only append Seq[ClientAction], Seq[Seq[ClientAction]] or Seq[PageRow]")
      }
    }

    results.toArray
  }

  def dropActions(): PageRow = {
    this.copy(actions = Seq(), dead = false)
  }

  //  def discardPage(): Unit = {
  //    this.page = None
  //  }

  //    def slice(
  //               selector: String,
  //               limit: Int = Const.fetchLimit,
  //               expand: Boolean = false,
  //               indexKey: String = null
  //               ): Array[PageRow] = {
  //
  //      val pages = this.page.get.slice(selector, limit, expand)
  //
  //      pages.zipWithIndex.map {
  //        tuple => {
  //
  //          PageRow(this.cells + (indexKey -> tuple._2) ,Some(tuple._1))
  //        }
  //      }
  //    }

  def asJson(): String = {

    import scala.collection.JavaConversions._

    val jsonCompatible: util.Map[String, _] = this.cells

    Const.jsonMapper.writeValueAsString(jsonCompatible)
  }

  def flatten(
               left: Boolean = false,
               indexKey: String = null
               ): Array[PageRow] = {
    val result = if (indexKey == null) {
      this.pages.map{
        page => this.copy(cells = this.cells, pages = Seq(page))
      }
    }
    else {
      this.pages.zipWithIndex.map{
        tuple => this.copy(cells = this.cells + (indexKey -> tuple._2), pages = Seq(tuple._1))
      }
    }

    if (left && result.isEmpty) {
      Array(this.copy(pages = Seq()))
    }
    else {
      result.toArray
    }
  }

  //only apply to last page
  def select(keyAndF: (String, Page => Any)*): PageRow = {

    this.pages.lastOption match {
      case None => this
      case Some(page) =>
        val map = page.extractAsMap(keyAndF: _*)

        this.copy(cells = this.cells ++ map)
    }
  }

  def unselect(keys: String*): PageRow = {
    this.copy(cells = this.cells -- keys)
  }

  def +%>(
           actionAndF: (Action, Page => Any)
           ): PageRow = {

    this.pages.lastOption match {
      case None => this.die()
      case Some(page) => this +> this.pages.last.crawl1(actionAndF._1, actionAndF._2)
    }
  }

  //only apply to last page
  def +*%>(
            actionAndF: (Action, Page => Array[_])
            )(
            distinct: Boolean = true,
            limit: Int = Const.fetchLimit, //applied after distinct
            indexKey: String = null
            ): Array[PageRow] = {

    this.pages.lastOption match {
      case None => Array(this.die())
      case Some(page) => this +*> page.crawl(actionAndF._1, actionAndF._2)(distinct, limit, indexKey)
    }
  }

  //TODO: preserve action?
  def slice(
             selector: String,
             expand: Int = 0
             )(
             limit: Int = Const.fetchLimit, //applied after distinct
             indexKey: String = null,
             joinType: JoinType = Const.defaultJoinType,
             flatten: Boolean = true
             ): Array[PageRow] = {

    val sliced = this.pages.lastOption match {
      case None => Array[Page]()
      case Some(page) => page.slice(selector, expand)(limit)
    }

    val pages: Seq[Page] = joinType match {
      case Replace if sliced.isEmpty =>
        this.pages
      case Merge =>
        this.pages ++ sliced
      case _ =>
        sliced
    }

    if (flatten) this.copy(pages = pages).flatten(joinType == LeftOuter, indexKey = indexKey)
    else Array(this.copy(pages = pages))
  }

  def !=!(
           joinType: JoinType = Const.defaultJoinType,
           flatten: Boolean = true,
           indexKey: String = null
           )(
           implicit spooky: SpookyContext
           ): Array[PageRow] = {

    val pages: Seq[Page] = joinType match {
      case Replace if this.actions.isEmpty =>
        this.pages
      case Merge =>
        this.pages ++ PageBuilder.resolve(this.actions, this.dead)
      case _ =>
        PageBuilder.resolve(this.actions, this.dead)
    }

    if (flatten) PageRow(cells = this.cells, pages = pages).flatten(joinType == LeftOuter, indexKey)
    else Array(PageRow(cells = this.cells, pages = pages))
  }

  //affect last page
  def paginate(
              selector: String,
              attr: String = "abs:href",
              wget: Boolean = true
              )(
              limit: Int = Const.fetchLimit,
              indexKey: String = null,
              flatten: Boolean = true
              )(
              implicit spooky: SpookyContext
              ): Array[PageRow] = {

    var oldRow = this.dropActions()

    while (oldRow.pages.size <= limit && oldRow.pages.last.attrExist(selector, attr)) {

      val actionRow = if (!wget) oldRow +%> (Visit("#{~}") -> (_.attr1(selector, attr)))
      else oldRow +%> (Wget("#{~}") -> (_.attr1(selector, attr)))

      oldRow = actionRow.!=!(joinType = Merge, flatten = false).head
    }

    if (flatten) oldRow.flatten(indexKey = indexKey)
    else Array(oldRow)
  }

}

object DeadRow extends PageRow(dead = true)