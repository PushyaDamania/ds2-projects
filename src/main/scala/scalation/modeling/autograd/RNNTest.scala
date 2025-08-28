
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  Praveen Rangavajhula
 *  @version 2.0
 *  @date    Fri April 25 19:40:13 EDT 2025
 *  @see     LICENSE (MIT style license file).
 *
 *  @note    Autograd: Unit Tests for Autograd Functionality
 */

package scalation
package modeling
package autograd

import scalation.mathstat.{MatrixD, TensorD, VectorD}

import forecasting.MakeMatrix4TS.makeMatrix4L

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `RNNTest` object contains various @main tests for autograd RNN functionality.
 */
object RNNTest:

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Creates sequences for the RNN model from the input and output matrices.
     *  This function takes the input and output matrices and creates sequences of a specified length.
     *  Each sequence is a slice of the original matrices, and the function returns tensors containing
     *  these sequences.
     *  @param x                the input matrix of shape [n_samples, n_features]
     *  @param yy               the output matrix of shape [n_samples, n_output_features]
     *  @param sequence_length  the length of each sequence
     *  @return A tuple containing two tensors:
     *         - x_sequences: The input sequences tensor of shape [sequence_length, n_features, n_sequences]
     *         - y_sequences: The output sequences tensor of shape [sequence_length, n_output_features, n_sequences]
     */
    def create_sequences (x: MatrixD, yy: MatrixD, sequence_length: Int): (TensorD, TensorD) =
        val n_samples   = x.dim
        val n_sequences = n_samples - sequence_length + 1
        val x_sequences: TensorD = new TensorD (sequence_length, x.dim2, n_sequences)
        val y_sequences: TensorD = new TensorD (sequence_length, yy.dim2, n_sequences)

        for seq <- 0 until n_sequences do
            val sequence = seq until (seq + sequence_length)
            x_sequences(?, ?, seq) = x(sequence)
            y_sequences(?, ?, seq) = yy(sequence)
        end for

        (x_sequences, y_sequences)
    end create_sequences

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Creates an output matrix for the RNN model from the target vector.
     *  This function takes a target vector and creates a matrix where each row represents
     *  a shifted version of the target vector. The number of columns in the matrix is equal
     *  to the forecasting horizon (hh). If the shifted index exceeds the length of the target
     *  vector, the value is set to -0.0.
     *  @param y   the target vector of shape [n_samples]
     *  @param hh  the forecasting horizon (number of future steps to predict)
     *  @return A matrix of shape [n_samples - 1, hh] where each row is a shifted version of the target vector
     */
    private def makeOutputMatrix (y: VectorD, hh: Int): MatrixD =
        val yy = new MatrixD(y.dim - 1, hh)
        for t <- 0 until yy.dim; j <- 0 until hh do
            yy(t, j) = if t + 1 + j >= y.dim then -0.0 else y(t + 1 + j)
        yy
    end makeOutputMatrix

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Builds the input and output matrices for time series forecasting.
     *  This function takes a target vector and creates input and output matrices for time series forecasting.
     *  The input matrix is created using the specified number of lags, and the output matrix is created using
     *  the specified forecasting horizon. The function prints the dimensions of the input and output matrices
     *  and the value of the last element in the target vector.
     *  @param y         the target vector of shape [n_samples]
     *  @param lags      the number of lags to use for creating the input matrix
     *  @param hh        the forecasting horizon (number of future steps to predict)
     *  @param backcast  a boolean flag indicating whether to include backcasting (default is true)
     *  @return A tuple containing two matrices:
     *         - The input matrix of shape [n_samples - lags, lags]
     *         - The output matrix of shape [n_samples - 1, hh]
     */
    def buildMatrix4TS (y: VectorD, lags: Int, hh: Int, backcast: Boolean = true): (MatrixD, MatrixD) =
        val x  = makeMatrix4L (y, lags, backcast)
        val yy = makeOutputMatrix (y, hh)
        println (s"dims of x = ${x.dims}")
        println (s"dims of yy = ${yy.dims}")
        println (s"last element in y = ${y(y.dim - 1)}")
        (x, yy)
    end buildMatrix4TS

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Approximate the gradient of the loss function using a finite difference.
     *  @param param        the model parameters
     *  @param computeLoss  the function to compute the loss
     *  @param epsilon      the size of the finite difference
     */
    def finiteDiffGrad (param: Variabl, computeLoss: () => Double, epsilon: Double = 1e-5): TensorD =
        val (d1, d2, d3) = param.data.dims
        val gradApprox   = TensorD.fill (d1, d2, d3, 0.0)

        for i <- 0 until d1; j <- 0 until d2; k <- 0 until d3 do
            val orig = param.data(i, j, k)
            param.data(i, j, k) = orig + epsilon               // Perturb +epsilon
            val lossPlus = computeLoss ()
            param.data(i, j, k) = orig - epsilon               // Perturb -epsilon
            val lossMinus = computeLoss ()
            param.data(i, j, k) = orig                         // Restore

            // Central difference
            gradApprox(i, j, k) = (lossPlus - lossMinus) / (2 * epsilon)
        end for

        gradApprox
    end finiteDiffGrad

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Check that the analytical and numerical gradients match within tolerance.
     *  @param analytical  the analytical gradient
     *  @param numerical   the numerical gradient
     *  @param atol        the absolute tolerance
     *  @param rtol        the relative tolerance
     */
    def assertGradientsClose (analytical: TensorD, numerical: TensorD,
                              atol: Double = 1e-4, rtol: Double = 1e-3): Unit =
        val (d1, d2, d3) = analytical.dims

        for i <- 0 until d1; j <- 0 until d2; k <- 0 until d3 do
            val a    = analytical(i, j, k)
            val n    = numerical(i, j, k)
            val diff = math.abs(a - n)
            val tol  = atol + rtol * math.abs (n)
            assert (diff <= tol, s"Gradient mismatch at ($i,$j,$k): autograd=$a, numerical=$n, diff=$diff > tol=$tol")
        end for

        println ("✅ Gradients match within tolerance.")
    end assertGradientsClose

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** The `rnnTest1` main function tests the `RRNCell` class. 
     *  > runMain scalation.modeling.autodiff.rnnTest1
     */
    @main def rnnTest1 (): Unit =

        banner ("RNNCell - Forward + Backward Test 1 (1 timesteps × 2 sequences × 4 feature)")

        given ops : AutogradOps = AutogradOps.default

        val inputSize  = 4
        val hiddenSize = 3
        val batchSize  = 2                                       // 2 sequences (batch size)
        val seqLen     = 1                                       // just one time step for now

        println (s"Input size: $inputSize, Hidden size: $hiddenSize, Batch size: $batchSize, Sequence length: $seqLen")

        // Step 1: Create 2 sequences with 4 time steps each and 1 feature
        // Tensor shape: (inputSize, seqLen, batchSize) = (1, 4, 2)
        val inputData = TensorD ((batchSize, inputSize, 1),
                                 1.0,  2.0,  3.0,  4.0,          // batch 0 -> four features
                                 5.0,  6.0,  7.0,  8.0)          // batch 1 -> four features
        val input     = Variabl (inputData, name = Some("input"))
        val hPrevData = TensorD ((batchSize, hiddenSize, 1),
                                 0.1, 0.2, 0.3,                  // batch 0
                                 0.4, 0.5, 0.6)                  // batch 1

        val hPrev = Variabl (hPrevData, name = Some ("hPrev"))

        println (s"Input: $input")
        println (s"Previous hidden state (hPrev): $hPrev")

        // Step 4: Construct RNNCell
        val cell = RNNCell(inputSize, hiddenSize, activation = "tanh")

        println (s"RNNCell: $cell")
        println (s"input.shape = ${input.shape}")
        println (s"hPrev.shape = ${hPrev.shape}")
        println (s"W_ih.shape  = ${cell.W_ih.shape}")
        println (s"W_hh.shape  = ${cell.W_hh.shape}")
        println (s"b_ih.shape  = ${cell.b_ih.shape}")
        println (s"b_hh.shape  = ${cell.b_hh.shape}")
        println (s"W_ih.data   = ${cell.W_ih.data}")
        println (s"W_hh.data   = ${cell.W_hh.data}")
        println (s"b_ih.data   = ${cell.b_ih.data}")
        println (s"b_hh.data   = ${cell.b_hh.data}")

        // Step 5: Forward pass through one step
        val hNext = cell (IndexedSeq (input, hPrev)).head
        println (s"Forward output (hNext): $hNext")

        // Step 6: Loss and backward
        val loss = hNext.mean
        println (s"Loss (mean of hNext): $loss")

        loss.backward ()

        println (s"Gradient of input: ${input.grad}")
        println (s"Gradient of hPrev: ${hPrev.grad}")
        println (s"Gradient of W_ih: ${cell.W_ih.grad}")
        println (s"Gradient of W_hh: ${cell.W_hh.grad}")
        println (s"Gradient of b_ih: ${cell.b_ih.grad}")
        println (s"Gradient of b_hh: ${cell.b_hh.grad}")

        // Step 7: Gradient check
        val gradApprox = finiteDiffGrad (cell.W_ih, () =>
            val xDet = input.detach ()
            val hDet = hPrev.detach ()
            val hNext = cell (IndexedSeq(xDet, hDet)).head
            hNext.data.mean)

        println (s"Approximate grad (W_ih): $gradApprox")
        println (s"Autograd grad (W_ih): ${cell.W_ih.grad}")
        assertGradientsClose(cell.W_ih.grad, gradApprox)

        println ("\n✅ RNNCell test complete with 1 time step, 2 sequences and 4 features.")

    end rnnTest1

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** The `rnnTest2` main function tests the `RRNBase` class. 
     *  > runMain scalation.modeling.autodiff.rnnTest2
     */
    @main def rnnTest2 (): Unit =

        banner ("RNNBase − Forward + Backward Test 2  (2 time steps * 2 sequences * 4 features)")

        given ops: AutogradOps = AutogradOps.default

        // ---------------------------------------------------------------------------
        //  Config
        // ---------------------------------------------------------------------------
        val inputSize  = 4      // F
        val hiddenSize = 3      // H
        val batchSize  = 2      // B
        val seqLen     = 2      // T

        println (s"inputSize=$inputSize  hiddenSize=$hiddenSize  batchSize=$batchSize  seqLen=$seqLen")

        // ---------------------------------------------------------------------------
        // 1) Build full mini‑batch sequence : shape (B, T, F) = (2, 2, 4)
        //    batch‑0 :  [1,2,3,4]  then  [9,10,11,12]
        //    batch‑1 :  [5,6,7,8]  then  [13,14,15,16]
        // ---------------------------------------------------------------------------
        val seqData = TensorD ((batchSize, seqLen, inputSize),
                               1.0,   2.0,  3.0,  4.0,
                               9.0,  10.0, 11.0, 12.0,
                               5.0,   6.0,  7.0,  8.0,
                               13.0, 14.0, 15.0, 16.0)

        // ---------------------------------------------------------------------------
        // 2) Initial hidden state  (B, H, 1) = (2, 3, 1)
        // ---------------------------------------------------------------------------
        val h0Data = TensorD ((batchSize, hiddenSize, 1),
                              0.1, 0.2, 0.3,                // batch‑0
                              0.4, 0.5, 0.6)                // batch‑1
        val hPrev  = Variabl (h0Data, name = Some ("hPrev"))

        // Helper: wrap a (B × F) MatrixD slice into (B, F, 1)
        def stepTensor (m: MatrixD): TensorD =
            val base = TensorD.fromMatrix (m)
            base.permute (Seq (1, 2, 0))

        // ---------------------------------------------------------------------------
        // 3) Convert each time‑step slice into a Variabl (B, F, 1)
        // ---------------------------------------------------------------------------
        val inputSeq: IndexedSeq [Variabl] =
            (0 until seqLen).map { t =>
                val mat = seqData(?, t) // (2 × 4)
                val ten =  stepTensor(mat)
                Variabl (ten, name = Some(s"input_t$t"))
            }

        // ---------------------------------------------------------------------------
        // 4) Build RNNBase (simple tanh RNN cell)
        // ---------------------------------------------------------------------------
        val rnn  = RNNBase ("rnn", inputSize, hiddenSize, "tanh")
        val cell = rnn.cell
        println (s"RNN cell weights  W_ih.shape=${cell.W_ih.shape}  W_hh.shape=${cell.W_hh.shape}")

        // ---------------------------------------------------------------------------
        // 5) Forward through both time‑steps
        // ---------------------------------------------------------------------------
        val (outputs, hLast) = rnn.forward (inputSeq, Some(hPrev))

        outputs.zipWithIndex.foreach { case (h_t, t) =>
            println (s"h_t[$t] = ${h_t.data}")
        }
        println (s"Final hidden h_last = ${hLast.data}")

        // ---------------------------------------------------------------------------
        // 6) Dummy loss = mean of final hidden, backward
        // ---------------------------------------------------------------------------
        val loss = hLast.mean
        println (s"Loss = $loss")
        loss.backward ()

        inputSeq.foreach (x => println (s"${x.name.get}.grad = ${x.grad}"))
        println (s"hPrev.grad = ${hPrev.grad}")
        println (s"W_ih.grad  = ${cell.W_ih.grad}")
        println (s"W_hh.grad  = ${cell.W_hh.grad}")

        // ---------------------------------------------------------------------------
        // 7) Finite‑difference check on W_ih
        // ---------------------------------------------------------------------------
        val gradFD = finiteDiffGrad (cell.W_ih, () =>
            val freshIn = inputSeq.map (_.detach())
            val freshH0 = hPrev.detach ()
            val (_, hL) = rnn.forward (freshIn, Some (freshH0))
            hL.data.mean)

        println (s"Finite‑diff W_ih.grad = $gradFD")
        println (s"Autograd W_ih.grad = ${cell.W_ih.grad}")
        assertGradientsClose(cell.W_ih.grad, gradFD)

        println ("\n✅ rnnTest2 finished: batch‑first, 2‑step sequence.")

    end rnnTest2

end RNNTest

