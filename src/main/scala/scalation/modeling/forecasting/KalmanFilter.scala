
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller, Hao Peng
 *  @version 2.0
 *  @date    Sun Sep 13 20:37:41 EDT 2015
 *  @see     LICENSE (MIT style license file).
 *
 *  @note    Model: Kalman Filter
 *
 *  @see web.mit.edu/kirtley/kirtley/binlustuff/literature/control/Kalman%20filter.pdf
 *  @see en.wikipedia.org/wiki/Kalman_filter
 */

package scalation
package modeling
package forecasting

import scala.annotation.unused

import scalation.mathstat._
import scalation.random.NormalVec

// FIX: needs more thorough testing and estimation for matrices

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `KalmanFilter` class is used to fit state-space models.
 *     x_t = F x_t-1 + G u_t + w_t   (State Equation)
 *     z_t = H x_t + v_t             (Observation/Measurement Equation)
 *  @param x0  the initial state vector
 *  @param ff  the state transition matrix (F)
 *  @param hh  the observation matrix (H)
 *  @param qq  the process noise covariance matrix (Q)
 *  @param rr  the observation noise covariance matrix (R)
 *  @param gg  the optional control-input matrix (G)
 *  @param u   the optional control vector
 */
class KalmanFilter (x0: VectorD, ff: MatrixD, hh: MatrixD, qq: MatrixD, rr: MatrixD,
                    gg: MatrixD = null, u: VectorD = null):

    private val MAX_ITER = 20                                        // maximum number of iterations
    private val doPlot   = true                                      // flag for drawing plot
    private val n        = ff.dim                                    // dimension of the state vector
    private val _0       = VectorD (n)                               // vector of 0's
    private val ii       = MatrixD.eye (n, n)                        // identity matrix
    private val fft      = ff.transpose                              // transpose of ff
    private val hht      = hh.transpose                              // transpose of hh
    private var x        = x0                                        // the state estimate
    private var pp       = new MatrixD (n, n)                        // the covariance estimate

    val traj = if doPlot then new MatrixD (MAX_ITER, n+1) else new MatrixD (0, 0)

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Predict the state of the process at the next time point.
     */
    def predict (): Unit =
        x  = ff * x                                                  // new predicted state
        if u != null && gg != null then x += gg * u                  // if using control
        pp = ff * pp * fft + qq                                      // new predicted covariance
    end predict

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Update the state and covariance estimates with the current and possibly
     *  noisy measurements
     *  @param z  current measurement/observation of the state
     */
    def update (z: VectorD): Unit =
        val y  = z - hh * x                                          // measurement residual
        val ss = hh * pp * hht + rr                                  // residual covariance
        val kk = pp * hht * ss.inverse                               // optimal Kalman gain
        x  = x + kk * y                                              // updated state estimate
        pp = (ii - kk * hh) * pp                                     // updated covariance estimate
    end update

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Iteratively solve for x using predict and update phases.
     *  @param dt  the time increment (delta t)
     *  @param u   the control vector
     */
    def solve (dt: Double, @unused u: VectorD = null): VectorD =
        var t  = 0.0                                                 // initial time

        for k <- 0 until MAX_ITER do

            t += dt                                                  // advance time
            if doPlot then traj(k) = x :+ t                          // add current time t, state x to trajectory

            // predict
            predict ()                                               // estimate new state x and covariance pp

            // update
            val v  = NormalVec (_0, rr).gen                          // observation noise - FIX - should work in trait
            val z  = hh * x + v                                      // new observation

            update (z)
        end for
        x
    end solve

end KalmanFilter

 
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `KalmanFilter` object provide factory methods for special types of Kalman Filters.
 */
object KalmanFilter:

    private val debug = debugf ("KalmanFilter", true)                // debug function

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create a `KalmanFilter` for p-th order Auto-Regressive AR(p) time series models.
     *     x_t = F x_t-1 + w_t           (State Equation)
     *     z_t = H x_t + v_t             (Observation/Measurement Equation)
     *  @see Kalman Filter section in "Introduction to Computational Data Science"
     *  @param y       the time series vector
     *  @param hparam  the hyperparameters
     */
    def apply (y: VectorD, hparam: HyperParameter = AR.hp): KalmanFilter =
        val p  = hparam("p").toInt                                   // order of AR model
        val yy = y.standardize                                       // standardize the input (y-mu)/sigma
        val ar = new AR (yy, 1)                                      // use simple MoM for initial estimtes
        ar.train (null, yy)                                          // train the MoM model
        val b  = ar.parameter.drop (1)                               // get the parameter vector b (φ); skip intercept
        val yp = ar.predictAll ()                                    // make predictions
        val e  = yy - yp                                             // get the residual/error vector
        val x0 = yy(0 until p)                                       // initial state vector
        val ff = buildMatrix (p, b)                                  // state transition matrix (F)
        val hh = new MatrixD (1, p); hh(0, 0) = 1                    // observation matrix (H)
        val qq = new MatrixD (p, p); qq(?, ?) = e.variance           // process noise covariance matrix (Q = var(e_t))
        val rr = new MatrixD (1, 1)                                  // observation noise covariance matrix (R = [0])
        debug ("apply", s"p = $p, x0 = $x0, ff = $ff, hh = $hh, qq = $qq, rr = $rr")
        new KalmanFilter (x0, ff, hh, qq, rr)
    end apply

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Build the state transition matrix F for a p-th order Auto-Regressive AR(p) model.
     *  @param p  the order of the AR model
     *  @param b  the coefficient vector b (φ) for a p-th order Auto-Regressive AR(p) model
     */
    def buildMatrix (p: Int, b: VectorD): MatrixD =
        val f = new MatrixD (p, p)
        f(0)  = b                                                    // first row is vector b (φ)
        for i <- 1 until p do f(i, i-1) = 1                          // diagonal submatrix
        f
    end buildMatrix

end KalmanFilter


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `kalmanFilterTest` main function is used to test the `KalmanFilter` class.
 *  @see en.wikipedia.org/wiki/Kalman_filter
 *  > runMain scalation.modeling.forecasting.kalmanFilterTest
 */
@main def kalmanFilterTest (): Unit =

    banner ("KalmanFilterTest")

    val dt    = 0.1                                                  // time increment (delta t)
    val var_a = 0.5                                                  // variance of uncontrolled acceleration a
    val var_z = 0.5                                                  // variance from observation noise

    val ff = MatrixD ((2, 2), 1.0, dt,                               // transition matrix
                              0.0, 1.0)

    val hh = MatrixD ((1, 2), 1.0, 0.0)

    val qq = MatrixD ((2, 2), dt~^4/4, dt~^3/2,
                              dt~^3/2, dt~^2) * var_a

    val rr = MatrixD ((1, 1), var_z)

    val x0 = VectorD (0.0, 0.0)

    val kf = new KalmanFilter (x0, ff, hh, qq, rr)

    println ("solve = " + kf.solve (dt))
    println ("traj  = " + kf.traj)

    new Plot (kf.traj(?, 2), kf.traj(?, 0), kf.traj(?, 1))

end kalmanFilterTest


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `kalmanFilterTest2` main function is used to test the `KalmanFilter` class.
 *  Test the Kalman Filter on the COVID-19 dataset.
 *  > runMain scalation.modeling.forecasting.kalmanFilterTest2
 */
@main def kalmanFilterTest2 (): Unit =

    import Example_Covid.loadData_y

    banner ("KalmanFilterTest2: COVID-19")

    val yy = loadData_y ()
//  val y  = yy                                                      // full
    val y  = yy(0 until 116)                                         // clip the flat end
//  val hh = 6                                                       // maximum forecasting horizon

    AR.hp("p") = 3
    val kf = KalmanFilter (y)                                        // create a Kalman Filter 
    
    println ("solve = " + kf.solve (1.0))
    println ("traj  = " + kf.traj)

    new Plot (kf.traj(?, 2), kf.traj(?, 0), kf.traj(?, 1))

end kalmanFilterTest2


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `kalmanFilterTest3` main function is used to test the `KalmanFilter` class.
 *  @see https://faculty.washington.edu/ezivot/econ584/notes/statespacemodels.pdf
 *  > runMain scalation.modeling.forecasting.kalmanFilterTest2
 *
@main def kalmanFilterTest3 (): Unit =

    banner ("KalmanFilterTest: AR(2)")

    val dt    = 0.1                                                  // time increment (delta t)
    val var_a = 0.5                                                  // variance of uncontrolled acceleration a
    val var_z = 0.5                                                  // variance from observation noise

    val ff = MatrixD ((2, 2), phi1, phi2,                            // transition matrix
                              1.0,  0.0)

    val hh = MatrixD ((1, 2), 1.0, 0.0)

    val qq = MatrixD ((2, 2), dt~^4/4, dt~^3/2,
                              dt~^3/2, dt~^2) * var_a

    val rr = MatrixD ((1, 1), var_z)

    val x0 = VectorD (0.0, 0.0)

    val kf = new KalmanFilter (x0, ff, hh, qq, rr)

    println ("solve = " + kf.solve (dt))
    println ("traj  = " + kf.traj)

    new Plot (kf.traj(?, 2), kf.traj(?, 0), kf.traj(?, 1))

end kalmanFilterTest3
 */

