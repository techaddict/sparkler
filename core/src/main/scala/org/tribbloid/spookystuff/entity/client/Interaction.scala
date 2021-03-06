package org.tribbloid.spookystuff.entity.client

import org.openqa.selenium.htmlunit.HtmlUnitDriver
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.events.EventFiringWebDriver
import org.openqa.selenium.support.ui.{ExpectedCondition, ExpectedConditions, Select, WebDriverWait}
import org.openqa.selenium.{By, WebDriver}
import org.tribbloid.spookystuff.entity.Page
import org.tribbloid.spookystuff.factory.PageBuilder
import org.tribbloid.spookystuff.{Const, Utils}

import scala.concurrent.duration.Duration

/**
 * Interact with the browser (e.g. click a button or type into a search box) to reach the data page.
 * these will be logged into target page's backtrace.
 * failed interactive will trigger an error dump by snapshot.
 * has an option to be delayed to
 */
abstract class Interaction extends Action {

  final override def mayExport() = false

  final override def trunk() = Some(this) //can't be ommitted

  final override def doExe(pb: PageBuilder): Seq[Page] = {

      exeWithoutPage(pb: PageBuilder)

      Seq()
  }

  def exeWithoutPage(pb: PageBuilder): Unit
}

/**
 * Type into browser's url bar and click "goto"
 * @param url support cell interpolation
 */
case class Visit(url: String) extends Interaction {
  override def exeWithoutPage(pb: PageBuilder) {
    pb.driver.get(url)
  }

  override def interpolateFromMap[T](map: Map[String,T]): this.type = {
    this.copy(url = Action.interpolateFromMap(this.url,map)).asInstanceOf[this.type]
  }
}

/**
 * Wait for some time
 * @param delay seconds to be wait for
 */
case class Delay(delay: Duration = Const.actionDelayMax) extends Interaction {
  //  override val timeout = Math.max(Const.driverCallTimeout, delay + 10)

  override def exeWithoutPage(pb: PageBuilder) {
    Thread.sleep(delay.toMillis)
  }
}

/**
 * Wait for some random time, add some unpredictability
 * @param min seconds to be wait for
 */
case class RandomDelay(
                        min: Duration = Const.actionDelayMin,
                        max: Duration = Const.actionDelayMax
                        ) extends Interaction {

  assert(max >= min)

  //  override val timeout = Math.max(Const.driverCallTimeout, delay + 10)

  override def exeWithoutPage(pb: PageBuilder) {
    Thread.sleep(min.toMillis + Utils.random.nextInt((max - min).toMillis.toInt) )
  }
}

/**
 * Wait until at least one particular element appears, otherwise throws an exception
 * @param selector css selector of the element
 *              after which it will throw an exception!
 */
case class DelayFor(selector: String) extends Interaction with Timed {
  //  override val timeout = Math.max(Const.driverCallTimeout, delay + 10)

  override def exeWithoutPage(pb: PageBuilder) {
    val wait = new WebDriverWait(pb.driver, delay.toSeconds)
    wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)))
  }
}

object DocumentReadyCondition extends ExpectedCondition[Boolean] {

  override def apply(input: WebDriver): Boolean = {

    val script = "return document.readyState"

    val result = input match {
      case d: HtmlUnitDriver => d.executeScript(script)
      //      case d: AndroidWebDriver => d.executeScript(script)
      case d: EventFiringWebDriver => d.executeScript(script)
      case d: RemoteWebDriver => d.executeScript(script)
      case _ => throw new UnsupportedOperationException("this web browser driver is not supported")
    }

    result == "complete"
  }
}

case object DelayForDocumentReady extends Interaction with Timed {

  override def exeWithoutPage(pb: PageBuilder): Unit = {
    val wait = new WebDriverWait(pb.driver, delay.toSeconds)

    wait.until(DocumentReadyCondition)
  }
}


//experimental
//case class DelayForAjaxReady() extends Interaction with Timed {
//
//  object AjaxReadyCondition extends ExpectedCondition[Boolean] {
//
//    override def apply(input: WebDriver): Boolean = {
//
//      val jQueryScript = "jQuery.active"
//      val prototypeScript = "Ajax.activeRequestCount"
//      val dojoScript = "dojo.io.XMLHTTPTransport.inFlight.length"
//
//      input match {
//        case d: HtmlUnitDriver => d.executeScript(jQueryScript) == 0||d.executeScript(prototypeScript) == 0||d.executeScript(dojoScript) == 0
//        //      case d: AndroidWebDriver => d.executeScript(jQueryScript) == 0||d.executeScript(prototypeScript) == 0||d.executeScript(dojoScript) == 0
//        case d: EventFiringWebDriver => d.executeScript(jQueryScript) == 0||d.executeScript(prototypeScript) == 0||d.executeScript(dojoScript) == 0
//        case d: RemoteWebDriver => d.executeScript(jQueryScript) == 0||d.executeScript(prototypeScript) == 0||d.executeScript(dojoScript) == 0
//        case _ => throw new UnsupportedOperationException("this web browser driver is not supported")
//      }
//    }
//  }
//
//  override def exeWithoutPage(pb: PageBuilder): Unit = {
//    val wait = new WebDriverWait(pb.driver, delay.toSeconds)
//
//    wait.until(AjaxReadyCondition)
//  }
//}

/**
 * Click an element with your mouse pointer.
 * @param selector css selector of the element, only the first element will be affected
 */
case class Click(selector: String)extends Interaction with Timed {
  override def exeWithoutPage(pb: PageBuilder) {
    val wait = new WebDriverWait(pb.driver, delay.toSeconds)
    val element = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(selector)))

    element.click()
  }
}

/**
 * Submit a form, wait until new content returned by the submission has finished loading
 * @param selector css selector of the element, only the first element will be affected
 */
case class Submit(selector: String) extends Interaction with Timed {
  override def exeWithoutPage(pb: PageBuilder) {
    val wait = new WebDriverWait(pb.driver, delay.toSeconds)
    val element = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)))

    element.submit()
  }
}

/**
 * Type into a textbox
 * @param selector css selector of the textbox, only the first element will be affected
 * @param text support cell interpolation
 */
case class TextInput(selector: String, text: String) extends Interaction with Timed {
  override def exeWithoutPage(pb: PageBuilder) {
    val wait = new WebDriverWait(pb.driver, delay.toSeconds)
    val element = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)))

    element.sendKeys(text)
  }

  override def interpolateFromMap[T](map: Map[String,T]): this.type = {
    this.copy(text = Action.interpolateFromMap(this.text,map)).in(this.delay).asInstanceOf[this.type]
  }
}

/**
 * Select an item from a drop down list
 * @param selector css selector of the drop down list, only the first element will be affected
 * @param text support cell interpolation
 */
case class DropDownSelect(selector: String, text: String) extends Interaction with Timed {
  override def exeWithoutPage(pb: PageBuilder) {
    val wait = new WebDriverWait(pb.driver, delay.toSeconds)
    val element = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)))

    val select = new Select(element)
    select.selectByValue(text)
  }

  override def interpolateFromMap[T](map: Map[String,T]): this.type = {
    this.copy(text = Action.interpolateFromMap(this.text,map)).in(this.delay).asInstanceOf[this.type]
  }
}

/**
 * Request browser to change focus to a frame/iframe embedded in the global page,
 * after which only elements inside the focused frame/iframe can be selected.
 * Can be used multiple times to switch focus back and forth
 * @param selector css selector of the frame/iframe, only the first element will be affected
 */
case class SwitchToFrame(selector: String)extends Interaction with Timed {
  override def exeWithoutPage(pb: PageBuilder) {
    val wait = new WebDriverWait(pb.driver, delay.toSeconds)
    val element = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)))

    pb.driver.switchTo().frame(element)
  }
}

/**
 * Execute a javascript snippet
 * @param script support cell interpolation
 */
case class ExeScript(script: String) extends Interaction {
  override def exeWithoutPage(pb: PageBuilder) {
    pb.driver match {
      case d: HtmlUnitDriver => d.executeScript(script)
      //      case d: AndroidWebDriver => d.executeScript(script)
      case d: EventFiringWebDriver => d.executeScript(script)
      case d: RemoteWebDriver => d.executeScript(script)
      case _ => throw new UnsupportedOperationException("this web browser driver is not supported")
    }
  }

  override def interpolateFromMap[T](map: Map[String,T]): this.type = {
    this.copy(script = Action.interpolateFromMap(this.script,map)).asInstanceOf[this.type]
  }
}

/**
 *
 * @param selector selector of the slide bar
 * @param percentage distance and direction of moving of the slider handle, positive number is up/right, negative number is down/left
 * @param handleSelector selector of the slider
 */
case class DragSlider(
                       selector: String,
                       percentage: Double,
                       handleSelector: String = "*"
                       )
  extends Interaction with Timed {

  override def exeWithoutPage(pb: PageBuilder): Unit = {

    val wait = new WebDriverWait(pb.driver, delay.toSeconds)
    val element = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)))

    val slider = element.findElement(By.cssSelector(handleSelector))

    val dim = element.getSize
    val height = dim.getHeight
    val width = dim.getWidth

    val move = new Actions(pb.driver)

    if (width > height) {
      move.dragAndDropBy(slider, (width * percentage).asInstanceOf[Int], 0).build().perform()
    }
    else {
      move.dragAndDropBy(slider, 0, (height * percentage).asInstanceOf[Int]).build().perform()
    }
  }
}