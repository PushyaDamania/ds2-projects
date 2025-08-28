
//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller
 *  @version 2.0
 *  @date    Mon Sep  2 14:37:55 EDT 2024
 *  @see     LICENSE (MIT style license file).
 *
 *  @note    Model Framework: Abstract Class for Vector Forecasters that utilize Regression
 *           Extending classes include VAR, VARX, ...
 *
 *  @see     phdinds-aim.github.io/time_series_handbook/03_VectorAutoregressiveModels/03_VectorAutoregressiveMethods.html
 *           www.lem.sssup.it/phd/documents/Lesson17.pdf
 *           Parameter/coefficient estimation: Multi-variate Ordinary Least Squares (OLS) or
 *                                             Generalized Least Squares (GLS)
 */

package scalation
package modeling
package forecasting
package multivar

import scala.annotation.unused

import scalation.mathstat._
import scalation.modeling.neuralnet.{RegressionMV => REGRESSION}
//import scalation.modeling.neuralnet.{RidgeRegressionMV => REGRESSION}

import MakeMatrix4TS.hp

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `Forecaster_RegV` abstract class provides multi-variate time series analysis capabilities
 *  for Forecaster_RegV models.  Forecaster_RegV models are similar to `ARX` models, except
 *  that some exogenous variables are treated as endogenous variables and are themselves forecasted.
 *  Potentially having more up-to-date forecasted values feeding into multi-horizon forecasting
 *  can improve accuracy, but may also lead to compounding of forecast errors.
 *  @param y        the response/output matrix (multi-variate time series data)
 *  @param x        the input lagged time series data
 *  @param hh       the maximum forecasting horizon (h = 1 to hh)
 *  @param fname    the feature/variable names
 *  @param tRng     the time range, if relevant (time index may suffice)
 *  @param hparam   the hyper-parameters (defaults to `MakeMatrix4TS.hp`)
 *  @param bakcast  whether a backcasted value is prepended to the time series (defaults to false)
 */
abstract class Forecaster_RegV (x: MatrixD, y: MatrixD, hh: Int, fname: Array [String] = null,
                                tRng: Range = null, hparam: HyperParameter = hp,
                                bakcast: Boolean = false)                 // backcasted values only used in `buildMatrix4TS`
//    extends Forecaster_D (x, y, hh, tRng, hparam, bakcast):             // no automatic backcasting, @see `Forecaster_RegV.apply`
      extends Diagnoser (dfm = hparam("p").toInt, df = y.dim - hparam("p").toInt)
         with ForecastTensor (y, hh, tRng)
//       with FeatureSelection                                            // FIX -- add feature selection
         with Model:

    private val debug = debugf ("Forecaster_RegV", true)                  // debug function
    private val flaw  = flawf ("Forecaster_RegV")                         // flaw function

    protected val nneg = hparam("nneg").toInt == 1                        // 0 => unrestricted, 1 => predictions must be non-negative
    protected var bb: MatrixD = null                                      // matrix of parameter values
    protected val yf   = makeForecastTensor (y, hh)                       // make the forecast tensor
    protected val reg  = new REGRESSION (x, y, fname, hparam)             // delegate training to multi-variate regression

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Get the data/input matrix built from lagged y vector (and optionally xe) values.
     */
    def getX: MatrixD = x

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the used response vector y (first colum in matrix).
     */
    def getY: VectorD = y(?, 0)

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the used response matrix y.  Mainly for derived classes where y is
     *  transformed.
     */
    override def getYY: MatrixD = y

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the used FORECAST TENSOR yf.
     */
    def getYf: TensorD = yf

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the feature/variable names.
     */
    def getFname: Array [String] = fname

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Train/fit an `Forecaster_RegV` model to the times-series data y_ = f(x).
     *  Estimate the coefficient matrix bb for a `Forecaster_RegV` model.
     *  Uses OLS Matrix Factorization to determine the coefficients, i.e., the bb matrix.
     *  @param x_  the data/input matrix (e.g., full x)
     *  @param y_  the training/full response matrix (e.g., full y)
     */
    def train (x_ : MatrixD, y_ : MatrixD): Unit =
        debug ("train", s"$modelName, x_.dim = ${x_.dim}, y_.dim = ${y_.dim}")
        reg.train (x_, y_)                                                // train the multi-variate regression model
        bb = reg.parameter                                                // coefficients from regression
        debug ("train", s"parameter matrix bb = $bb")
    end train

    def train (x_ : MatrixD, y_ : VectorD): Unit =
        throw new UnsupportedOperationException ("train (MatrixD, VectorD) use the alternative train")
    end train

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Train and test the forecasting model y_ = f(x_) + e and report its QoF
     *  and plot its predictions.  Return the predictions and QoF.
     *  NOTE: must use `trainNtest_x` when an x matrix is used, such as in `VAR`.
     *  @param x_  the training/full data/input matrix (defaults to full x)
     *  @param y_  the training/full response/output vector (defaults to full y)
     *  @param xx  the testing/full data/input matrix (defaults to full x)
     *  @param yy  the testing/full response/output vector (defaults to full y)
     */
    def trainNtest_x (x_ : MatrixD = x, y_ : MatrixD = y)
                     (xx: MatrixD = x, yy: MatrixD = y): (MatrixD, MatrixD) =
        train (x_, y_)                                                    // train the model on training set
        val (yp, qof) = test (xx, yy)                                     // test the model on testing set
        for j <- qof.indices do
            banner (s"report for feature ${fname(j)}")
            println (report (qof(j)))                                     // report on Quality of Fit (QoF)
        (yp, qof)
    end trainNtest_x

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Test PREDICTIONS of a forecasting model y_ = f(lags (y_)) + e
     *  and return its predictions and  QoF vector.  Testing may be in-sample
     *  (on the training set) or out-of-sample (on the testing set) as determined
     *  by the parameters passed in.  Note, must call train before test.
     *  Must override to get Quality of Fit (QoF).
     *  @param x_  the data/input matrix (ignored, pass null)
     *  @param y_  the actual testing/full response/output matrix
     */
    def test (@unused x_ : MatrixD, y_ : MatrixD): (MatrixD, MatrixD) =
        val yp = predictAll (y_)                                          // make all predictions
        val yy = if bakcast then y_(1 until y_.dim)                       // align the actual values
                 else y_
        println (s"yy.dim = ${yy.dim}, yp.dim = ${yp.dim}")
//      Forecaster.differ (yy, yfh)                                       // uncomment for debugging
        assert (yy.dim == yp.dim)                                         // make sure the vector sizes agree

        Forecaster_RegV.plotAll (yy, yp, s"test: $modelName")
        mod_resetDF (yy.dim)                                              // reset the degrees of freedom
        (yp, diagnose (yy, yp))                                           // return predicted and QoF vectors
    end test

    def test (x_ : MatrixD, y_ : VectorD): (VectorD, VectorD) =
        throw new UnsupportedOperationException ("test (MatrixD, VectorD): use the alternative test")
    end test

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the parameters.
     */
    def parameter: VectorD | MatrixD = bb

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the hyper-parameters.
     */
    def hparameter: HyperParameter = hparam

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Diagnose the quality of the model for each variable.
     *  @param yy  the matrix of actual values
     *  @param yp  the matrix of predicted values
     */
    def diagnose (yy: MatrixD, yp: MatrixD): MatrixD =
        MatrixD (for j <- yy.indices2 yield diagnose (yy(?, j), yp(?, j)))
    end diagnose

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Predict a value for y_t using the 1-step ahead forecast.
     *  @see `modeling.rectify` define in `Predictor.scala`
     *  @param t   the time point being predicted
     *  @param y_  the actual values to use in making predictions
     *  FIX -- `Forecaster_Reg` uses x(t) while x(t-1) is used here
     */
    def predict (t: Int, y_ : MatrixD): VectorD =
        val yp = rectify (reg.predict (x(t-1)), nneg)
        if t < y_.dim then
            debug ("predict", s"@t = $t, x(t-1) = ${x(t-1)}, yp = $yp vs. y_ = ${y_(t)}")
        yp
    end predict

    def predict (z: VectorD): Double | VectorD =
        throw new UnsupportedOperationException ("predict (VectorD): use the alternative predict")
    end predict

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Predict all values corresponding to the given time series vector y_.
     *  Update FORECAST TENSOR yf and return PREDICTION MATRIX yp as second (1) column
     *  of yf with last value removed.
     *  Note, yf(t, h, j) if the forecast to time t, horizon h, variable j
     *  @see `forecastAll` to forecast beyond horizon h = 1.
     *  @see `Forecaster.predictAll` for template implementation for vectors
     *  @param y_  the actual time series values to use in making predictions
     */
    def predictAll (y_ : MatrixD): MatrixD =
        if bakcast then
            for t <- 1 until y_.dim do yf(t-1, 1) = predict (t, y_)       // use model to make predictions
            yf(?, 1)(0 until y_.dim-1)                                    // return yp: first horizon only
        else
//          debug ("predictAll", s"y_.dim = ${y_.dim}, yf.dims = ${yf.dims}")
            for t <- 1 until yf.dim+1 do yf(t-1, 1) = predict (t, y_)     // skip t = 0
            yf(?, 1)
    end predictAll

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Forge a new vector from the first spec values of x, the last p-h+1 values
     *  of x (past values), values 1 to h-1 from the forecasts, and available values
     *  from exogenous variables.
     *  @param xx  the t-th row of the input matrix (lagged actual values)
     *  @param yy  the t-th row of the forecast tensor (forecasted future values)
     *  @param h   the forecasting horizon, number of steps ahead to produce forecasts
     */
    def forge (xx: VectorD, yy: MatrixD, h: Int): VectorD 

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Produce a vector of size hh, h = 1 to hh-steps ahead forecasts for the model,
     *  i.e., forecast the following time points:  t+1, ..., t+h.
     *  Intended to work with rolling validation (analog of predict method).
     *  @param t   the time point from which to make forecasts
     *  @param y_  the actual values to use in making predictions
     */
    def forecast (t: Int, y_ : MatrixD): MatrixD = ???

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Forecast values for all y_.dim time points at horizon h (h-steps ahead).
     *  Assign into FORECAST TENSOR and return the h-steps ahead forecast.
     *  Note, yf(t, h, j) if the forecast to time t, horizon h, variable j
     *  Note, `predictAll` provides predictions for h = 1.
     *  @see `forecastAll` method in `Forecaster` trait.
     *  @param h   the forecasting horizon, number of steps ahead to produce forecasts
     *  @param y_  the actual values to use in making forecasts
     */
    def forecastAt (h: Int, y_ : MatrixD = y): MatrixD =
        if h < 2 then flaw ("forecastAt", s"horizon h = $h must be at least 2")

        for t <- y_.indices do                                            // make forecasts over all time points for horizon h
            val xy   = forge (x(t), yf(t), h)                             // yf(t) = time t, all horizons, all variables
            val pred = rectify (reg.predict (xy), nneg)                   // slide in prior forecasted values
//          debug ("forecastAt", s"h = $h, @t = $t, xy = $xy, yp = $pred, y_ = ${y_(t)}")
            yf(t, h) = pred                                               // record in forecast tensor
        yf(?, h)                                                          // return the h-step ahead forecast vector
    end forecastAt

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Forecast values for all y_.dim time points and all horizons (1 through hh-steps ahead).
     *  Record these in the FORECAST TENSOR yf, where
     *
     *      yf(t, h) = h-steps ahead forecast for y_t
     *
     *  @param y_  the actual values to use in making forecasts
     */
    def forecastAll (y_ : MatrixD): TensorD =
        for h <- 2 to hh do forecastAt (h, y_)                            // forecast k-steps into the future
        yf                                                                // return tensor of forecasted values
    end forecastAll

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Align the actual response matrix for comparison with the predicted/forecasted
     *  response matrix, returning a time vector and sliced response matrix.
     *  @param tr_size  the size of the intial training set
     *  @param y        the actual response for the full dataset (to be sliced)
     */
    def align (tr_size: Int, y: MatrixD): (VectorD, MatrixD) =
        (VectorD.range (tr_size, y.dim), y(tr_size until y.dim))
    end align

    def crossValidate (k: Int, rando: Boolean): Array [Statistic] =
        throw new UnsupportedOperationException ("Use `rollValidate` instead of `crossValidate`")
    end crossValidate

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Use rolling-validation to compute test Quality of Fit (QoF) measures
     *  by dividing the dataset into a TRAINING SET (tr) and a TESTING SET (te).
     *  as follows:  [ <-- tr_size --> | <-- te_size --> ]
     *  Calls forecast for h-steps ahead out-of-sample forecasts.
     *  Return the FORECAST TENSOR.
     *  @param rc       the retraining cycle (number of forecasts until retraining occurs)
     *  @param growing  whether the training grows as it roll or kepps a fixed size
     *  FIX - copied from `Forecaster` change it to work for VAR, VARX
     */
    def rollValidate (rc: Int = 2, growing: Boolean = false): TensorD =
        val ftMat   = new MatrixD (hh, Fit.N_QoF)
        banner (s"rollValidate: Evaluate ${modelName}'s QoF for horizons 1 to $hh:")

        val x       = getX                                                // get internal/expanded data/input matrix
        val y       = getYY                                               // get internal/expanded response/output matrix
        val yf      = getYf                                               // get the full in-sample forecast tensor
        val te_size = Forecaster.teSize (y.dim)                           // size of testing set
        val tr_size = Forecaster.trSize (y.dim)                           // size of initial training set
        debug ("rollValidate", s"y.dim = ${y.dim}, train: tr_size = $tr_size; test: te_size = $te_size, rc = $rc")

        val yp = new MatrixD (te_size, y.dim2)                            // y-predicted over testing set (only for h=1)
        for i <- 0 until te_size do                                       // iterate through testing set
            val is = if growing then 0 else i
            val t = tr_size + i                                           // next time point to forecast
            if i % rc == 0 then
                val x_ = if x != null then x(is until t) else null
                train (x_, y(is until t))                                 // retrain on sliding training set
//          yp(i)  = predict (min (t+1, y.dim-1), y)                      // predict the next value (only for h=1)
            yp(i)  = predict (t, y)                                       // predict the next value (only for h=1)
            val yd = forecast (t, y)                                      // forecast the next hh-values, yf is updated
            println (s"yf(t, 0) = ${yf(t, 0)}, yp(i) = ${yp(i)}, yd = $yd")
//          assert (yp(i) =~ yd(0))                                       // make sure h=1 forecasts agree with predictions
        end for

        val (t, yy) = align (tr_size, y)                                  // align vectors
        new Plot (t, yy(0), yp(0), s"rollValidate: Plot yy(0), yp(0) vs. t for $modelName", lines = true)

        val yf_ = yf(tr_size until y.dim)                                 // forecast tensor for test-set
        for h <- 1 to hh do
            val yy_ = yy(h-1 until yy.dim)                                // trim the actual values
            val yfh = yf_(?, h)(0 until yy.dim-h+1)                       // column h of the forecast tensor

            new Plot (t, yy_(0), yfh(0), s"rollValidate: Plot yy_(0), yfh(0) vs. t for $modelName @h = $h", lines = true)
            mod_resetDF (te_size - h)                                     // reset degrees of freedom
            val qof    = diagnose (yy_, yfh)
            ftMat(h-1) = qof(0)                                           // FIX -- need for all endo, not just the first
//          println (FitM.fitMap (qof, qoF_names))
        end for
        println ("fitMap     qof = ")
        println (FitM.showFitMap (ftMat.transpose, QoF.values.map (_.toString)))
        yf
    end rollValidate

end Forecaster_RegV


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `Forecaster_RegV` object supports regression for Multivariate Time Series data.
 *  Given a response matrix y, a predictor matrix x is built that consists of
 *  lagged y vectors.   Additional future response vectors are built for training.
 *      y_t = b dot x
 *  where x = [y_{t-1}, y_{t-2}, ... y_{t-lag}].
 */
object Forecaster_RegV:

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Use rolling-validation to compute test Quality of Fit (QoF) measures
     *  by dividing the dataset into a TRAINING SET (tr) and a TESTING SET (te)
     *  as follows:  [ <-- tr_size --> | <-- te_size --> ]
     *  This version calls predict for one-step ahead out-of-sample forecasts.
     *  @see `RollingValidation`
     *  @param mod  the forecasting model being used (e.g., `Forecaster_RegV`)
     *  @param rc   the retraining cycle (number of forecasts until retraining occurs)
     *
    def rollValidate (mod: PredictorMV & Fit, rc: Int): Unit =
        val x       = mod.getX                                                 // get data/input matrix
        val y       = mod.getY                                                 // get response/output vector
        val te_size = RollingValidation.teSize (y.dim)                         // size of testing set
        val tr_size = y.dim - te_size                                          // size of initial training set
        debug ("rollValidate", s"train: tr_size = $tr_size; test: te_size = $te_size, rc = $rc")

        val yp = new MatrixD (te_size, y.dim2)                                 // y-predicted over testing set
        for i <- 0 until te_size do                                            // iterate through testing set
            val t = tr_size + i                                                // next time point to forecast
//          if i % rc == 0 then mod.train (x(0 until t), y(0 until t))         // retrain on sliding training set (growing set)
            if i % rc == 0 then mod.train (x(i until t), y(i until t))         // retrain on sliding training set (fixed size set)
            yp(i) = mod.predict (x(t-1))                                       // predict the next value
        end for

        val df = max (0, mod.parameter(0).dim - 1)                             // degrees of freedom for model
        mod.resetDF (df, te_size - df)                                         // reset degrees of freedom
        for k <- y.indices2 do
            val (t, yk) = RollingValidation.align (tr_size, y(?, k))           // align vectors
            val ypk = yp(?, k)
            banner (s"QoF for horizon ${k+1} with yk.dim = ${yk.dim}, ypk.dim = ${ypk.dim}")
            new Plot (t, yk, ypk, s"Plot yy, yp vs. t for horizon ${k+1}", lines = true)
            println (FitM.fitMap (mod.diagnose (yk, ypk), qoF_names))
        end for
    end rollValidate
     */

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Plot actual vs. predicted values for all variables (columns of the matrices).
     *  @param y     the original un-expanded output/response matrix
     *  @param yp    the predicted values (one-step ahead forecasts) matrix
     *  @param name  the name of the model run to produce yp
     */
    def plotAll (y: MatrixD, yp: MatrixD, name: String): Unit =
        for j <- y.indices2  do
            new Plot (null, y(?, j).drop (1), yp(?, j), s"$name, y vs. yp @ var j = $j", lines = true)
    end plotAll

end Forecaster_RegV

