package org.tribbloid.spookystuff.integration.forum

import org.tribbloid.spookystuff.entity.client._
import org.tribbloid.spookystuff.entity.client.Loop
import org.tribbloid.spookystuff.integration.SpookyTestCore
import org.tribbloid.spookystuff.operator.LeftOuter

/**
 * Created by peng on 04/07/14.
 */
object Youtube extends SpookyTestCore{

  override def doMain() = {

    import spooky._
    import scala.concurrent.duration._

    (((sc.parallelize(Seq("MetallicaTV")) +>
      Visit("http://www.youtube.com/user/#{_}/videos") +>
      Loop(
        Click("button.load-more-button span.load-more-text")
          :: DelayFor("button.load-more-button span.hid.load-more-loading").in(10.seconds)
          :: Nil,
        2
      ) !=!())
      .sliceJoin("li.channels-content-item")(limit=100)
      .extract("title" -> (_.text1("h3.yt-lockup-title")))
      .visit("h3.yt-lockup-title a.yt-uix-tile-link")(limit = 1)
      .repartition(400) +>
      ExeScript("window.scrollBy(0,500)") +>
      Try(DelayFor("iframe[title^=Comment]").in(50.seconds) :: Nil)
      !><()).extract(
        "description" -> (_.text1("div#watch-description-text")),
        "publish" -> (_.text1("p#watch-uploader-info")),
        "total_view" -> (_.text1("div#watch7-views-info span.watch-view-count")),
        "like_count" -> (_.text1("div#watch7-views-info span.likes-count")),
        "dislike_count" -> (_.text1("div#watch7-views-info span.dislikes-count"))
      )
      .visit("iframe[title^=Comment]", attr = "abs:src")(limit = 10) +>
      Loop(
        Click("span[title^=Load]")
          :: DelayFor("span.PA[style^=display]").in(10.seconds)
          :: Nil,
        2
      ) !=!(joinType = LeftOuter))
      .extract("num_comments" -> (_.text1("div.DJa")))
      .sliceJoin("div[id^=update]")()
      .extract(
        "comment1" -> (_.text1("h3.Mpa")),
        "comment2" -> (_.text1("div.Al"))
      )
      .asSchemaRDD()
  }
}
