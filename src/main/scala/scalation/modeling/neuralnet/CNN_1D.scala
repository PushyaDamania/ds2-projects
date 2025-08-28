
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller, Riya Bangia
 *  @version 2.0
 *  @date    Wed Oct 28 20:43:47 EDT 2020
 *  @see     LICENSE (MIT style license file).
 *
 *  @note    Model: 1D Convolutional Neural Network (CNN)
 *
 *  @see Hands-On Fundamentals of 1D Convolutional Neural Networks—A Tutorial for Beginner Users
 *       https://www.mdpi.com/2076-3417/14/18/8500
 */

// U N D E R   D E V E L O P M E N T

// FIX - extend training to handle multiple cofilters, pooling and multiple convolutional layers

package scalation
package modeling
package neuralnet

import scalation.mathstat._
import scalation.modeling.forecasting.MakeMatrix4TS

import ActivationFun._
import Initializer._

import CoFilter_1D.conv

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `CNN_1D` class implements a Convolutionsl Network model.
 *  The model is trained using a data matrix x and response matrix y.
 *  @param x       the input/data matrix with instances stored in rows
 *  @param y       the output/response matrix, where y_i = response for row i of matrix x
 *  @param fname_  the feature/variable names (defaults to null)
 *  @param nf      the number of filters for this convolutional layer
 *  @param nc      the width of the filters (size of cofilters)
 *  @param pool    the pooling window size (if pool > 1, pooling is applied)
 *  @param poolFun the pooling function to apply (e.g., CoFilter_1D.pool for max pooling, or
 *                                                      CoFilter_1D.pool_a for average pooling)
 *  @param hparam  the hyper-parameters for the model/network
 *  @param f       the activation function family for layers 1->2 (input to hidden)
 *  @param f1      the activation function family for layers 2->3 (hidden to output)
 *  @param itran   the inverse transformation function returns responses to original scale
 */
class CNN_1D (x: MatrixD, y: MatrixD, fname_ : Array [String] = null,
              nf: Int = 1, nc: Int = 3,
              pool: Int = 1, poolFun: (VectorD, Int) => VectorD = CoFilter_1D.pool_a,
              hparam: HyperParameter = Optimizer.hp ++ MakeMatrix4TS.hp,
              f: AFF = f_reLU, f1: AFF = f_reLU,
              val itran: FunctionM2M = null)
      extends PredictorMV (x, y, fname_, hparam)
         with Fit (dfm = x.dim2 - 1, df = x.dim - x.dim2):

    private val debug     = debugf ("CNN_1D", true)                       // debug function
    private val flaw      = flawf ("CNN_1D")                              // flaw function
    private val eta       = hparam("eta").toDouble                        // learning rate
//  private val bSize     = hparam("bSize").toInt                         // batch size
    private val maxEpochs = hparam("maxEpochs").toInt                     // maximum number of training epochs/iterations
    private val (n, ny)   = (x.dim2, y.dim2)
    private val nz        = n - nc + 1                                    // size without padding
    private val pooled_nz = if pool > 1 then nz / pool else nz            // after pooling
    private val fcDim     = nf * pooled_nz

    if nz < 2 then flaw ("init", s"the size of the hidden layer nz = $nz is too small") 

    private val filt = Array.fill (nf)(new CoFilter_1D (nc))              // array of filters
//  private val c = weightVec (nc)                                        // parameters (weights & biases) in to hid
//  private val b = NetParam (weightMat (nz, ny), new VectorD (ny))
    private val b = NetParam (weightMat (fcDim, ny), new VectorD (ny))    // parameters (weights & biases) hid to out

    modelName = s"CNN_1D_${f.name}_${f1.name}"

    println (s"Create a CNN_1D with $n input, $nf filters and $ny output nodes")

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Compute the feature maps using all filters and concatenate them.
     *  The result is a matrix with dimensions (number of instances) × (nf * nz).
     *  @parm x_  input matrix
     */
    private def computeFeatureMaps (x_ : MatrixD): MatrixD =
        val maps = for i <- 0 until nf yield                              // compute a feature map for each filter
            val filterVec = filt(i).coef                                  // vector for filter i
            val convResult = CoFilter_1D.conv (filterVec, x_)             // valid convolution on x_
            val activated  = f.fM (convResult)                            // apply activation function on convolution result
            if pool >  1 then activated.mmap (row => poolFun(row, pool)) else activated

        // Concatenate horizontally all feature maps (assumes same number of rows).
        maps.reduce ((a, b) => a ++^ b)
    end computeFeatureMaps

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Filter the i-th input vector with the f-th filter.
     *  @param i  the index of the i-th row of the matrix
     *  @param f  the index of the f-th filter
     */
    def filter (i: Int, f: Int): VectorD =
        val xi = x(i)
        val ft = filt(f)      
        debug ("filter", s"ft = $ft")                           // delete once it works
        val xf = new VectorD (xi.dim - nc + 1)
//      for j <- xf.indices do xf(j) = ft.dot (xi, j)           // FIX -- dot
        xf
    end filter

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Update filter f's parameters.
     *  @param f     the index for the filter
     *  @param vec2  the new paramters for the filter's vector
     */
    def updateFilterParams (f: Int, vec2: VectorD): Unit = filt(f).update (vec2)

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the parameters c and b.
     */
    override def parameters: NetParams = 
        val cMatrices = filt.map (filter => MatrixD.fromVector (filter.coef))   // create array of matrices, each representing one filter's coefs.
        val cMat = cMatrices.reduce ((a, b) => a ++^ b)                         // concatenate them horizontally into one matrix.
        Array (NetParam (cMat), b)
    end parameters

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Given training data x_ and y_, fit the parametera c and b.
     *  This is a simple algorithm that iterates over several epochs using gradient descent.
     *  It does not use batching nor a sufficient stopping rule.
     *  In practice, use the train2 method that uses a better optimizer.
     *  @param x_  the training/full data/input matrix
     *  @param y_  the training/full response/output matrix
     */
    def train (x_ : MatrixD = x, y_ : MatrixD = y): Unit =
        println (s"train: eta = $eta")
        var sse0 = Double.MaxValue                                        // hold prior value of sse

        var (go, epoch) = (true, 1)
        cfor (go && epoch <= maxEpochs, epoch += 1) {  
//          val φ  = f.fM (conv (c, x_))                                  // φ  = f(conv (c, X))
            val φ = computeFeatureMaps (x_)                               // compute concatenated feature maps from all filters
            val yp = f1.fM (b * φ)                                        // Yp = f1(ZB)
            val ε  = yp - y                                               // negative error E  = Yp - Y
            val δ1 = f1.dM (yp) ⊙ ε                                       // delta matrix for y
            val δ0 = f.dM (φ) ⊙ (δ1 * b.w.𝐓)                              // delta matrix for φ (transpose (𝐓))
//          CNN_1D.updateParam (x_, φ, δ0, δ1, eta, c, b)
            CNN_1D.updateParam (x_, φ, δ0, δ1, eta, filt, b, nz, pool)

            val sse = (y_ - yp).normFSq                                   // loss = sum of squared errors
            debug ("train", s"sse for $epoch th epoch: sse = $sse")
            if sse >= sse0 then go = false                                // return early if moving up
            sse0 = sse                                                    // save prior sse
        } // cfor
    end train

//          val yp_ = f1.fM (f.fM (b * conv (c, x_)))                     // updated predictions

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Given training data x_ and y_, fit the parameters c and b.
     *  Iterate over several epochs, where each epoch divides the training set into
     *  batches.  Each batch is used to update the weights.       
     *  FIX - to be implemented
     *  @param x_  the training/full data/input matrix
     *  @param y_  the training/full response/output matrix
     */
    override def train2 (x_ : MatrixD = x, y_ : MatrixD = y): Unit =
        val epochs = 0 // optimize3 (x_, y_, c, b, eta, bSize, maxEpochs, f, f1)    // FIX: optimize parameters c, b
        println (s"ending epoch = $epochs")
//      estat.tally (epochs._2)
    end train2

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Test a predictive model y_ = f(x_) + e and return its QoF vector.
     *  Testing may be be in-sample (on the training set) or out-of-sample
     *  (on the testing set) as determined by the parameters passed in.
     *  Note: must call train before test.
     *  @param x_  the testing/full data/input matrix (defaults to full x)
     *  @param y_  the testing/full response/output matrix (defaults to full y)
     */
    def test (x_ : MatrixD = x, y_ : MatrixD = y): (MatrixD, MatrixD) =
        val yp = predict (x_)                                             // make predictions
        val yy = if itran == null then y_ else itran (y_)                 // undo scaling, if used
        e = yy - yp                                                       // RECORD the residuals/errors (@see `Predictor`)
        val qof = MatrixD (for k <- yy.indices2 yield diagnose (yy(?, k), yp(?, k))).𝐓   // transpose (𝐓)
        (yp, qof)                                                         // return predictions and QoF vector
    end test

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Given a new input vector z, predict the output/response vector f(z).
     *  Formula: f1.f_ (b dot f.f_ (c *+ z))
     *  With multiple filters, first convert z to a 1-row matrix, compute its feature map
     *  (which will have one row and (nf*nz) columns), and then apply the fully connected layer.
     *  @param z  the new input vector
     */
    def predict (z: VectorD): VectorD = 
        val zMatrix = MatrixD.fromVector (z)                              // convert vector to one-row matrix.
        val phi     = computeFeatureMaps (zMatrix)                        // matrix with 1 row and nf*nz columns, compute feature maps from all filters
        val phiVec  = phi(0)                                              // extract first (and only) row as a vector
        f1.f_ (b dot phiVec)                                              // compute prediction using the fully-connected layer
    end predict

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Given an input matrix z, predict the output/response matrix f(z).
     *  Formula: f1.fM (b * f.fM (conv (c, z)))
     *  @param z  the input matrix
     */
    override def predict (z: MatrixD = x): MatrixD =
        val phi = computeFeatureMaps (z)                                  // matrix with dimensions (instances x (nf * nz)),
                                                                          // compute concatenated feature maps from all filters
        f1.fM (b * phi)                                                   // apply fully-connected layer to produce the predictions
    end predict

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Build a sub-model that is restricted to the given columns of the data matrix.
     *  @param x_cols  the columns that the new model is restricted to
     *  @param fname2  the variable/feature names for the new model (defaults to null)
     */
    def buildModel (x_cols: MatrixD, fname2: Array [String] = null): CNN_1D =
        new CNN_1D (x_cols, y, null, nf, nc, pool, poolFun, hparam, f, f1, itran)
    end buildModel

end CNN_1D


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `CNN_1D` companion object provides factory methods for creating 1D
 *  convolutional neural networks.
 */
object CNN_1D extends Scaling:

    def apply (xy: MatrixD): CNN_1D = ???

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create a `CNN_1D` with automatic rescaling from a data matrix and response matrix.
     *  @param x       the input/data matrix with instances stored in rows
     *  @param y       the output/response matrix, where y_i = response for row i of matrix x
     *  @param fname   the feature/variable names (defaults to null)
     *  @param nf      the number of filters for this convolutional layer
     *  @param nc      the width of the filters (size of cofilters)
     *  @param pool    the pooling window size (if pool > 1, pooling is applied)
     *  @param poolFun the pooling function to apply (e.g., CoFilter_1D.pool for max pooling, or
     *                                                      CoFilter_1D.pool_a for average pooling)
     *  @param hparam  the hyper-parameters for the model/network
     *  @param f       the activation function family for layers 1->2 (input to hidden)
     *  @param f1      the activation function family for layers 2->3 (hidden to output)
     *  @param itran   the inverse transformation function returns responses to original scale
     */
    def rescale (x: MatrixD, y: MatrixD, fname: Array [String] = null,
                 nf: Int = 1, nc: Int = 3,
                 pool: Int = 1, poolFun: (VectorD, Int) => VectorD = CoFilter_1D.pool,
                 hparam: HyperParameter = Optimizer.hp ++ MakeMatrix4TS.hp,
                 f: AFF = f_reLU, f1: AFF = f_reLU): CNN_1D =
        var itran: FunctionM2M = null                                     // inverse transform -> original scale

        val x_s = if scale then rescaleX (x, f)
                  else x
        val y_s = if f1.bounds != null then { val y_i = rescaleY (y, f1); itran = y_i._2; y_i._1 }
                  else y
//      val y_s = { val y_i = rescaleY (y, f_sigmoid); itran = y_i._2; y_i._1 }

        println (s" scaled: x = $x_s \n scaled y = $y_s")
        new CNN_1D (x_s, y_s, fname, nf, nc, pool, poolFun, hparam, f, f1, itran)
    end rescale

    def buildNrescale (xe: MatrixD, y: VectorD, fname: Array [String] = null,
                       nf: Int = 1, nc: Int = 3,
                       pool: Int = 1, poolFun: (VectorD, Int) => VectorD = CoFilter_1D.pool,
                       hparam: HyperParameter = Optimizer.hp ++ MakeMatrix4TS.hp,
                       f: AFF = f_reLU, f1: AFF = f_reLU): CNN_1D =
           val (xy, _) = forecasting.ARX.buildMatrix (xe, y, hparam, false)
           println (s" buildNrescale: xy.dims = ${xy.dims}, y.dim = ${y.dim}")
           rescale (xy, MatrixD.fromVector (y), fname, nf, nc, pool, poolFun, hparam, f, f1)
    end buildNrescale

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Update the parameters:  the weights in the convolutional filter c and
     *  the weights biases in the fully-connected layer b.
     *  @param x_    the training/full data/input matrix
     *  @param φ     the training/full response/output matrix
     *  @param δ0    the convolutional layer delta
     *  @param δ1    the fully-connectd layer delta
     *  @param η     the learning rate
     *  @param filt  the array of convolutional filters
     *  @param b     the fully-connectd layer parameters
     *  @param nz    the width of the feature map per filter (from valid convolution)
     *  @param pool  the pooling window size (if pool > 1, pooling is applied
     */
    def updateParam (x_ : MatrixD, φ: MatrixD, δ0: MatrixD, δ1: MatrixD, η: Double,
                     filt: Array [CoFilter_1D], b: NetParam, nz: Int, pool: Int = 1): Unit =

        val pooledWidth = if pool > 1 then nz / pool else nz
        for i <- filt.indices do
            val startCol = i * pooledWidth                                // determine column range for filter's feature map in φ and δ0
            val endCol   = startCol + pooledWidth - 1
            val δ0_i     = δ0(0 until δ0.dim, startCol to endCol)         // extract the portion of δ0 corresponding to filter i
            val filtVec  = filt(i).coef                                   // get current filter coef vector (using `coef`)
            val updatVec = filtVec.copy                                   // update each coefficient in the filter

            for j <- filtVec.indices do
                var sum = 0.0
                for row <- x_.indices; h <- 0 until pooledWidth do
                    sum += x_(row, h + j) * δ0_i(row, h)                  // x_(row, h+j) input value for h-th convolution output of filter
                updatVec(j) -= (sum / (x_.dim * pooledWidth)) * η         // update rule: gradient descent step
            end for
            filt(i).update (updatVec)                                     // update i-th filter with the new weights
        end for
        b -= (φ.𝐓 * δ1 * η, δ1.mean * η)                                  // update fully-connected layer parameters
    end updateParam

end CNN_1D


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `cNN_1DTest` main function is used to test the `CNN_1D` class.
 *  Test using the simple example from section 11.10 of ScalaTion textbook.
 *  Perform four training steps.
 *  > runMain scalation.modeling.neuralnet.cNN_1DTest
 */
@main def cNN_1DTest (): Unit =

    val x = MatrixD ((2, 5), 1, 2, 3, 4, 5,
                             6, 7, 8, 9, 10)
    val y = MatrixD ((2, 2),  6,  9,
                             16, 24)
    val c = VectorD (0.5, 1, 0.5)
    val b = NetParam (MatrixD ((3, 2), 0.1, 0.2,
                                       0.3, 0.4,
                                       0.5, 0.6))

    val nc = 3
    val nz = x.dim2 - nc + 1
    val cfilter = new CoFilter_1D (c.dim)
    cfilter.update (c)

    val sst0 = (y(?, 0) - y(?, 0).mean).normSq                            // sum of squares total for y_:0
    val sst1 = (y(?, 1) - y(?, 1).mean).normSq                            // sum of squares total for y_:1
    println (s"sst0 = $sst0")
    println (s"sst1 = $sst1")

    val η = 0.001                                                         // learning rate

    val f  = f_reLU                                                       // first activation function
    val f1 = f_reLU                                                       // second activation function

    println (s"input x = $x")                                             // input/data matrix
    println (s"input y = $y")                                             // output/response matrix
    println (s"η       = $η")

    for epoch <- 1 to 4 do
        banner (s"Start of epoch $epoch")
        println (s"filter  c = $c")                                       // values for cofilter
        println (s"weights b = $b")                                       // values for fully-connected layer

        val φ  = f.fM (conv (c, x))                                       // φ  = f(conv (c, X))
        val yp = f1.fM (φ *: b)                                           // Yp = f1(φB)  -- use *: as b is NetParam
        val ε  = yp - y                                                   // negative error E  = Yp - Y
        val δ1 = f1.dM (yp) ⊙ ε                                           // delta matrix for y
        val δ0 = f.dM (φ) ⊙ (δ1 * b.w.𝐓)                                  // delta matrix for φ (transpose (𝐓))

        println (s"feature map φ  = $φ")
        println (s"response    yp = $yp")
        println (s"- error     ε  = $ε")
        println (s"delta 1     δ1 = $δ1")
        println (s"delta 0     V0 = $δ0")

        CNN_1D.updateParam (x, φ, δ0, δ1, η, Array (cfilter), b, nz, 1)
        val sse = ε.normFSq
        println (s"sse for $epoch th epoch: sse = $sse")

        val sse0 = ε(?, 0).normSq                                         // sum of squared errors for column 0
        val sse1 = ε(?, 1).normSq                                         // sum of squared errors for column 1
        banner ("metrics")
        println (s"sse0  = $sse0")
        println (s"sse1  = $sse1")
        println (s"R^2_0 = ${1 - sse0/sst0}")
        println (s"R^2_1 = ${1 - sse1/sst1}")
    end for

//      val yp_ = f1.fM (f.fM (conv (c, x)) *: b)                         // updated predictions

end cNN_1DTest


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `cNN_1DTest` main function is used to test the `CNN_1D` class.
 *  Test using the simple example from section 11.10 of ScalaTion textbook.
 *  Perform four training steps.
 *  > runMain scalation.modeling.neuralnet.cNN_1DTest2
 */
@main def cNN_1DTest2 (): Unit =

    val hp = Optimizer.hp ++ MakeMatrix4TS.hp

    val x = MatrixD ((2, 5), 1, 2, 3, 4, 5,
                             6, 7, 8, 9, 10)
    val y = MatrixD ((2, 2),  6,  9,
                             16, 24)
/*
    val c = VectorD (0.5, 1, 0.5)
    val b = NetParam (MatrixD ((3, 2), 0.1, 0.2,
                                       0.3, 0.4,
                                       0.5, 0.6))
*/

    val sst0 = (y(?, 0) - y(?, 0).mean).normSq                            // sum of squares total for y_:0
    val sst1 = (y(?, 1) - y(?, 1).mean).normSq                            // sum of squares total for y_:1
    println (s"sst0 = $sst0")
    println (s"sst1 = $sst1")

    val η = 0.001                                                         // learning rate

    println (s"input x = $x")                                             // input/data matrix
    println (s"input y = $y")                                             // output/response matrix
    println (s"η       = $η")

    banner ("CNN_1D")
    hp("eta") = η
    val cnn   = new CNN_1D (x, y)
    cnn.trainNtest ()()

end cNN_1DTest2


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `cNN_1DTest3` main function is used to test the `CNN_1D` class
 *  using the AutoMPG dataset.
 *  > runMain scalation.modeling.neuralnet.cNN_1DTest3
 */
@main def cNN_1DTest3 (): Unit =

    import Example_AutoMPG._
    banner ("CNN_1D vs. Regession - Example_AutoMPG")

    val hp = Optimizer.hp ++ MakeMatrix4TS.hp

    banner ("Regression")
    val reg = Regression (oxy)()
    reg.trainNtest ()()

    banner ("CNN_1D")
    hp("eta") = 0.00013
    hp("maxEpochs") = 1000
    val cnn = CNN_1D.rescale (
        ox, MatrixD.fromVector (y),
        nc       = 6,                      // filter width of 6
        nf       = 2,                      // 2 distinct filters
        hparam   = hp,
        f        = ActivationFun.f_lreLU, // ReLU hidden
        f1       = ActivationFun.f_id     // linear output
    )
    cnn.trainNtest ()()

end cNN_1DTest3


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `cNN_1DTest4 main function is used to test the `CNN_1D` class
 *  using the Covid dataset.
 *  > runMain scalation.modeling.neuralnet.cNN_1DTest4
 */
@main def cNN_1DTest4 (): Unit =

    import forecasting.Example_Covid._
    banner ("CNN_1D Example_Covid")

    val hp = Optimizer.hp ++ MakeMatrix4TS.hp

//  val exo_vars  = NO_EXO
    val exo_vars  = Array ("icu_patients")
//  val exo_vars  = Array ("icu_patients", "hosp_patients", "new_tests", "people_vaccinated")
    val (xxe, yy) = loadData (exo_vars, response)
    println (s"xxe.dims = ${xxe.dims}, yy.dim = ${yy.dim}")

//  val xe = xxe                                                        // full
    val xe = xxe(0 until 116)                                           // clip the flat end
//  val y  = yy                                                         // full
    val y  = yy(0 until 116)                                            // clip the flat end
//  val hh = 6                                                          // maximum forecasting horizon
    hp("eta")       = 0.000005                                          // learning rate
    hp("maxEpochs") = 1000                                              // max epoch

    new Plot (null, y, null, s"y (new_deaths) vs. t", lines = true)
    for j <- exo_vars.indices do
        new Plot (null, xe(?, j), null, s"x_$j (${exo_vars(j)}) vs. t", lines = true)

    for p <- 6 to 6; q <- 4 to 4; s <- 1 to 1 do                        // number of endo lags; exo lags; trend
        hp("p")    = p                                                  // number of endo lags
        hp("q")    = q                                                  // number of exo lags
        hp("spec") = s                                                  // trend specification: 0, 1, 2, 3, 5
//      val mod    = ARX (xe, y, hh)                                    // create model for time series data
        val mod    = CNN_1D.buildNrescale (xe, y, nc = 3, nf = 10, hparam = hp, f1 = ActivationFun.f_reLU)
        mod.trainNtest ()()
    end for

end cNN_1DTest4

