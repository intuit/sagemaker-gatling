import java.io.InputStream
import java.nio.file.{Files, Paths}

import org.slf4j.LoggerFactory

import scala.io.Source

object FileHelper {
  val logger = LoggerFactory.getLogger(getClass)

  /**
   * Get file content as a string.
   *
   * filePath can be one of the following:
   * - Absolute path. Example:
   * /Users/rshiao/git/data-mlplatform/ml-gatling/src/test/resources/bodies/model-sim/1/sample.input.json
   * - Relative to current directory. Example:
   * src/test/resources/bodies/model-sim/1/sample.input.json
   * - Resource file. Example:
   * bodies/model-sim/1/sample.input.json
   *
   * @param filePath
   * @return file content as string
   */
  def getFileAsString(filePath: String): String = {
    // First check if file exists.  Path can be either absolute or relative to current directory.
    if (Files.isRegularFile(Paths.get(filePath))) {
      logger.info("Found file with path " + filePath)
      return Source.fromFile(filePath).getLines.mkString
    }
    logger.info("Unable to find file " + filePath + ", so try finding as resource.")

    // Otherwise, try looking for file as a resource.
    var resourcePath = filePath
    if (!filePath.startsWith("/")) {
      resourcePath = "/" + filePath
    }
    if (getClass.getResource(resourcePath) == null) {
      throw new RuntimeException("Unable to find resource " + resourcePath)
    }
    logger.info("Found file as resource " + resourcePath)
    val stream: InputStream = getClass.getResourceAsStream(resourcePath)
    val lines: Iterator[String] = Source.fromInputStream(stream).getLines
    val result = try lines.mkString finally stream.close()
    return result
  }

}
