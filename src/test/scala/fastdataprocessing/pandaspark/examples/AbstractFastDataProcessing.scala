package fastdataprocessing.pandaspark.examples

import org.apache.spark.examples.AbstractSparkExample

/**
 * AbstractFastDataProcessing
 * @author sunghyouk.bae@gmail.com at 15. 3. 5.
 */
abstract class AbstractFastDataProcessing extends AbstractSparkExample {

  val FAST_DATA_ROOT_DIR = "data/fastdataprocessing/"
  val EXAMPLE_CSV = FAST_DATA_ROOT_DIR + "example_partially_invalid.csv"
  val GEO_LITE_CITY_DAT = FAST_DATA_ROOT_DIR + "GeoLiteCity.dat"
  val IP_DELAY_CSV = FAST_DATA_ROOT_DIR + "ip_delay.csv"
  val NUMBERS_CSV = FAST_DATA_ROOT_DIR + "numbers.csv"

}
