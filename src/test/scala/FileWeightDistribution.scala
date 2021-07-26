import org.slf4j.LoggerFactory
import spray.json._

import scala.util.Random

case class FileWeight(path: String, weight: Int)
case class FileWeightListHolder(items: List[FileWeight])

object FileWeightProtocol extends DefaultJsonProtocol {
  implicit val fileWeightFormat: JsonFormat[FileWeight] = jsonFormat2(FileWeight)
  implicit object fileWeightListHolderFormat extends RootJsonFormat[FileWeightListHolder] {
    def read(value: JsValue) = FileWeightListHolder(value.convertTo[List[FileWeight]])
    def write(r: FileWeightListHolder) = null // not used
  }
}

import FileWeightProtocol._

/**
 * FileWeightDistribution
 *
 * @param fileWeightJson Expected format is an array of 1 or more elements where each
 *                       element has a path and a non-negative integer weight.  Example:
 *                       [
 *                           {
 *                               "path": "bodies/model-sim/1/sample.input.json",
 *                               "weight": 90
 *                           },
 *                           {
 *                               "path": "bodies/model-sim/1/sleep-1.input.json",
 *                               "weight": 10
 *                           }
 *                      ]
 */
class FileWeightDistribution(fileWeightJson: String) {
  private val logger = LoggerFactory.getLogger(getClass)

  logger.info("fileWeightJson: " + fileWeightJson)
  private val fileWeightList = fileWeightJson.parseJson.convertTo[FileWeightListHolder].items
  logger.info("fileWeightList: " + fileWeightList)

  if (fileWeightList == null || fileWeightList.isEmpty) {
    throw new IllegalArgumentException("fileWeightJson must contain at least one element")
  }

  // Check weights are non-negative and sum to 100.
  private var sumOfWeights = 0
  for (fileWeight <- fileWeightList) {
    if (fileWeight.weight < 0) {
      throw new IllegalArgumentException("fileWeight.weight cannot be negative, got: " + fileWeight.weight)
    }
    sumOfWeights = sumOfWeights + fileWeight.weight
  }
  if (sumOfWeights != 100) {
    throw new IllegalArgumentException("Request payload weights must total 100, got: " + sumOfWeights)
  }

  // Check each file exists, and read file into memory.
  var fileContentArray = new Array[String](fileWeightList.size)
  var i = 0
  for (fileWeight <- fileWeightList) {
    fileContentArray(i) = FileHelper.getFileAsString(fileWeight.path)
    logger.info("Verified file is accessible: " + fileWeight.path)
    i = i + 1
  }

  // Return nextFileContent based on defined probability.
  def nextFileContent : String = {
    // Optimize special case where only one file given by skipping random number generation
    if (fileContentArray.length == 1) {
      fileContentArray(0)
    } else {
      fileContentArray(nextFileIndex)
    }
  }

  private def nextFileIndex : Int = {
    val randomNumber = Random.nextInt(100)
    var fileIndex = 0
    var weightIndex = 0
    for (fileWeight <- fileWeightList) {
      weightIndex = weightIndex + fileWeight.weight
      if (randomNumber < weightIndex) {
        logger.debug(s"Random number $randomNumber is under current weightIndex $weightIndex so return fileIndex $fileIndex")
        return fileIndex
      }
      fileIndex = fileIndex + 1
    }
    throw new RuntimeException("Did not find file index for random number " + randomNumber)
  }

}
