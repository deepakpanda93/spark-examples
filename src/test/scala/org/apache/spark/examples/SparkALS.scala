package org.apache.spark.examples

import org.apache.commons.math3.linear._
import org.apache.spark.SparkContext._
import org.apache.spark.mllib.recommendation.{ALS, MatrixFactorizationModel, Rating}
import org.apache.spark.rdd.RDD


class SparkALS extends AbstractSparkExample {

  import org.apache.spark.examples.SparkALS._

  sparkTest("Alternating least squares matrix factorization") {
    val slices = 8
    M = 100
    U = 500
    F = 10

    val R = generateR()

    // Initialize m and u randomly
    var ms = Array.fill(M)(randomVector(F))
    var us = Array.fill(U)(randomVector(F))

    // Iteratively update movies then users
    val Rc = sc.broadcast(R)
    var msb = sc.broadcast(ms)
    var usb = sc.broadcast(us)

    var prevRmse = 100.0
    var diffRmse = 100.0
    var iter = 1

    while (diffRmse > 1E-4) {
      println(s"Iteration $iter:")
      ms = sc.parallelize(0 until M, slices)
           .map(i => update(i, msb.value(i), usb.value, Rc.value))
           .collect()
      msb = sc.broadcast(ms)

      us = sc.parallelize(0 until U, slices)
           .map(i => update(i, usb.value(i), msb.value, Rc.value.transpose()))
           .collect()
      usb = sc.broadcast(us)

      val newRmse = rmse(R, ms, us)
      println(s"RMSE = $newRmse")
      println()
      diffRmse = math.abs(newRmse - prevRmse)
      prevRmse = newRmse
      iter += 1
    }
  }

  sparkTest("recommendation test") {
    val data = sc.textFile("data/mllib/als/test.data")
    val ratings: RDD[Rating] = data.map(_.split(',') match {
      case Array(user, item, rate) => Rating(user.toInt, item.toInt, rate.toDouble)
    })

    // Build the recommendation model using ALS
    val rank = 10
    val numIterations = 20
    val model: MatrixFactorizationModel = ALS.train(ratings, rank, numIterations, 0.01)

    // Evaluate the model on rating data
    val usersProducts: RDD[(Int, Int)] = ratings.map {
      case Rating(user, product, rate) => (user, product)
    }
    val predictions = model.predict(usersProducts).map {
      case Rating(user, product, rate) =>
        println(s"Prodiction\tuser=$user, product=$product, rate=$rate")
        ((user, product), rate)
    }
    val ratesAndPreds = ratings.map {
      case Rating(user, product, rate) => ((user, product), rate)
    }.join(predictions)

    val MSE = ratesAndPreds.map {
      case ((user, product), (r1, r2)) =>
        val err = r1 - r2
        err * err
    }.mean()

    println(s"Mean squared error= $MSE")
  }
}

object SparkALS {

  // Parameters set through command line arguments
  var M = 0
  // Number of movies
  var U = 0
  // Number of users
  var F = 0
  // Number of features
  var ITERATIONS = 0
  val LAMBDA = 0.01 // Regularization coefficient

  def generateR(): RealMatrix = {
    val mh = randomMatrix(M, F)
    val uh = randomMatrix(U, F)
    mh.multiply(uh.transpose())
  }

  def rmse(targetR: RealMatrix, ms: Array[RealVector], us: Array[RealVector]): Double = {
    val r = new Array2DRowRealMatrix(M, U)
    for (i <- 0 until M; j <- 0 until U) {
      r.setEntry(i, j, ms(i).dotProduct(us(j)))
    }
    val diffs = r.subtract(targetR)
    var sumSqs = 0.0
    for (i <- 0 until M; j <- 0 until U) {
      val diff = diffs.getEntry(i, j)
      sumSqs += diff * diff
    }
    math.sqrt(sumSqs / (M.toDouble * U.toDouble))
  }

  def update(i: Int, m: RealVector, us: Array[RealVector], R: RealMatrix): RealVector = {

    val U = us.size
    val F = us(0).getDimension
    var XtX: RealMatrix = new Array2DRowRealMatrix(F, F)
    var Xty: RealVector = new ArrayRealVector(F)

    // For each movie that the user rated
    for (j <- 0 until M) {
      val u = us(j)
      // Add m * m^t to XtX
      XtX = XtX.add(u.outerProduct(u))
      // Add m * rating to Xty
      Xty = Xty.add(u.mapMultiply(R.getEntry(i, j)))
    }
    // Add regularization coeefficients to diagonal terms
    for (d <- 0 until F) {
      XtX.addToEntry(d, d, LAMBDA * U)
    }
    // Solve it with Cholesky
    new CholeskyDecomposition(XtX).getSolver.solve(Xty)
  }

  def randomVector(n: Int): RealVector =
    new ArrayRealVector(Array.fill(n)(math.random))

  def randomMatrix(rows: Int, cols: Int): RealMatrix =
    new Array2DRowRealMatrix(Array.fill(rows, cols)(math.random))

}
