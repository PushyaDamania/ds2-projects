
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

import scala.language.implicitConversions
import scala.math.ceil

import scalation.mathstat.{MatrixD, TensorD, TnT_Split, VectorD}
import scalation.modeling.neuralnet._

import AutogradOps.given
import Example_AutoMPG.{x, y}

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `AutogradTest` object contains various @main tests for autograd functionality.
 *  The tests validate basic arithmetic, complex expressions, activation functions,
 *  loss functions, and neural network layers with backpropagation.
 */
object AutogradTest:

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Basic operations test (Addition, Subtraction, Multiplication, Division).
     *  > runMain scalation.modeling.autograd.AutogradTest.autogradTest
     */
    @main def autogradTest1 (): Unit =
        banner (" Autograd Basic Operations - Test 1")
        // Initial setup with two tensors and wrapping in Variabls.
        val data1 = TensorD ((2, 2, 1), 1.0, 2.0, 3.0, 4.0)
        val data2 = TensorD ((2, 2, 1), 5.0, 6.0, 7.0, 8.0)
        val x = Variabl (data1, name = Some("x"))
        val y = Variabl (data2, name = Some("y"))

        // Test Addition: z = x + y.
        println ("Testing Addition")
        val addOp = Add (x, y)
        val z     = addOp.forward ()
        println (s"z (x + y): $z")
        z.backward ()
        println (s"x.grad after addition: ${x.grad}")
        println (s"y.grad after addition: ${y.grad}")

        // Test Addition with constant.
        println ("\nTesting Addition with Constant")
        val addConstOp = AddConstant (x, 10.0)
        val zConst     = addConstOp.forward ()
        println (s"z (x + 10.0): $zConst")
        zConst.backward ()
        println (s"x.grad after constant addition: ${x.grad}")

        // Reset gradients for subtraction tests.
        x.grad = TensorD.zerosLike (x.data)
        y.grad = TensorD.zerosLike (y.data)

        // Test Subtraction: z = x - y.
        println ("\nTesting Subtraction")
        val subOp = Sub (x, y)
        val zSub  = subOp.forward ()
        println (s"z (x - y): $zSub")
        zSub.backward ()
        println (s"x.grad after subtraction: ${x.grad}")
        println (s"y.grad after subtraction: ${y.grad}")

        // Test Subtraction with constant.
        println ("\nTesting Subtraction with Constant")
        val subConstOp = SubConstant (x, 5.0)
        val zSubConst  = subConstOp.forward ()
        println (s"z (x - 5.0): $zSubConst")
        zSubConst.backward ()
        println (s"x.grad after constant subtraction: ${x.grad}")

        // Reset gradients for multiplication tests.
        x.grad = TensorD.zerosLike (x.data)
        y.grad = TensorD.zerosLike (y.data)

        // Test Multiplication: z = x * y.
        println ("\nTesting Multiplication")
        val mulOp = Mul (x, y)
        val zMul  = mulOp.forward ()
        println (s"z (x * y): $zMul")
        zMul.backward ()
        println (s"x.grad after multiplication: ${x.grad}")
        println (s"y.grad after multiplication: ${y.grad}")

        // Test Multiplication with constant.
        println ("\nTesting Multiplication with Constant")
        val mulConstOp = MulConstant (x, 2.0)
        val zMulConst  = mulConstOp.forward ()
        println (s"z (x * 2.0): $zMulConst")
        zMulConst.backward ()
        println (s"x.grad after constant multiplication: ${x.grad}")

        // Reset gradients for division tests.
        x.grad = TensorD.zerosLike (x.data)
        y.grad = TensorD.zerosLike (y.data)

        // Test Division: z = x / y.
        println ("\nTesting Division")
        val divOp = Div (x, y)
        val zDiv  = divOp.forward ()
        println (s"z (x / y): $zDiv")
        zDiv.backward ()
        println (s"x.grad after division: ${x.grad}")
        println (s"y.grad after division: ${y.grad}")

        // Test Division with constant.
        println ("\nTesting Division with Constant")
        val divConstOp = DivConstant (x, 2.0)
        val zDivConst  = divConstOp.forward ()
        println (s"z (x / 2.0): $zDivConst")
        zDivConst.backward ()
        println (s"x.grad after constant division: ${x.grad}")

        println ("\nAll tests completed.")
    end autogradTest1

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Complex expression test combining multiple operations.
     *  > runMain scalation.modeling.autograd.AutogradTest.autogradTest2
     */
    @main def autogradTest2 (): Unit =
        banner (" Autograd Complex Operations - Test 2")
        val data1 = TensorD ((2, 2, 1), 1.0, 2.0, 3.0, 4.0)
        val data2 = TensorD ((2, 2, 1), 5.0, 6.0, 7.0, 8.0)
        val x = Variabl (data1, name = Some ("x"))
        val y = Variabl (data2, name = Some ("y"))

        banner (" Autograd Complex Expression Test ")
        // Complex operation: z = (x * y) + (x / y) - y
        println ("\nTesting Complex Expression: z = (x * y) + (x / y) - y")
        val zFinal = (x * y) + (x / y) - y
        println (s"zFinal ((x * y) + (x / y) - y): $zFinal")
        zFinal.backward ()
        println (s"x.grad after complex operation: ${x.grad}")
        println (s"y.grad after complex operation: ${y.grad}")
    end autogradTest2

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Test math operations: square root, logarithm, reciprocal, and mean.
     *  > runMain scalation.modeling.autograd.AutogradTest.autogradTest3
     */
    @main def autogradTest3 (): Unit =
        banner (" Autograd Math Operations - Test 3")
        val data = TensorD ((2, 2, 1), 1.0, 2.0, 4.0, 8.0)
        val x = Variabl (data, name = Some("x"))

        println ("\nTesting Exponentiation and Logarithms")
        // Test Sqrt.
        println ("Testing Sqrt")
        val sqrtOp = Sqrt (x)
        val zSqrt  = sqrtOp.forward ()
        println (s"z (sqrt(x)): $zSqrt")
        zSqrt.backward ()
        println (s"x.grad after sqrt: ${x.grad}")

        // Reset gradient.
        x.grad = TensorD.zerosLike (x.data)

        // Test Log.
        println ("\nTesting Logarithm")
        val logOp = Log (x)
        val zLog  = logOp.forward ()
        println (s"z (log(x)): $zLog")
        zLog.backward ()
        println (s"x.grad after log: ${x.grad}")

        // Reset gradient.
        x.grad = TensorD.zerosLike (x.data)

        // Test Reciprocal.
        println ("\nTesting Reciprocal")
        val reciprocalOp = Reciprocal (x)
        val zReciprocal  = reciprocalOp.forward ()
        println (s"z (1/x): $zReciprocal")
        zReciprocal.backward ()
        println (s"x.grad after reciprocal: ${x.grad}")

        // Reset gradient.
        x.grad = TensorD.zerosLike (x.data)

        // Test Mean.
        println ("\nTesting Mean")
        val meanOp = Mean (x)
        val zMean  = meanOp.forward ()
        println (s"z (mean(x)): $zMean")
        zMean.backward ()
        println (s"x.grad after mean: ${x.grad}")

        println ("\nAll tests in Test 3 completed.")
    end autogradTest3

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Test activation functions, including ReLU, Sigmoid, Tanh, GeLU, Softmax, Identity,
     *  LeakyReLU, and ELU.
     *  > runMain scalation.modeling.autograd.AutogradTest.autogradTest4
     */
    @main def autogradTest4 (): Unit =
        banner (" Autograd Activation Functions - Test 4")
        val t = VectorD.range (-50, 50) / 10.0
        val data = TensorD.fromVector (t)
        val x = Variabl (data, name = Some ("x"))

        println ("\nTesting Activation Functions")
        // Test ReLU.
        println ("Testing ReLU")
        val reluOp = ReLU (x)
        val zReLU  = reluOp.forward ()
        println (s"z (ReLU(x)): $zReLU")
        zReLU.backward ()
        println (s"x.grad after ReLU: ${x.grad}")

        // Reset gradient.
        x.grad = TensorD.zerosLike (x.data)

        // Test Sigmoid.
        println ("\nTesting Sigmoid")
        val sigmoidOp = Sigmoid (x)
        val zSigmoid  = sigmoidOp.forward ()
        println (s"z (sigmoid(x)): $zSigmoid")
        zSigmoid.backward ()
        println (s"x.grad after sigmoid: ${x.grad}")

        // Reset gradient.
        x.grad = TensorD.zerosLike (x.data)

        // Test Tanh.
        println ("\nTesting Tanh")
        val tanhOp = Tanh (x)
        val zTanh  = tanhOp.forward ()
        println (s"z (tanh(x)): $zTanh")
        zTanh.backward ()
        println (s"x.grad after tanh: ${x.grad}")

        // Reset gradient.
        x.grad = TensorD.zerosLike (x.data)

        // Test GeLU.
        println ("\nTesting GeLU")
        val geluOp = GeLU (x)
        val zGeLU  = geluOp.forward ()
        println (s"z (GeLU(x)): $zGeLU")
        zGeLU.backward ()
        println (s"x.grad after GeLU: ${x.grad}")

        // Reset gradient.
        x.grad = TensorD.zerosLike (x.data)

        // Test Softmax.
        println ("\nTesting Softmax")
        val softmaxOp = Softmax (x)
        val zSoftmax  = softmaxOp.forward ()
        println (s"z (Softmax(x)): $zSoftmax")
        zSoftmax.backward ()
        println (s"x.grad after Softmax: ${x.grad}")

        // Reset gradient.
        x.grad = TensorD.zerosLike (x.data)

        // Test Identity.
        println ("\nTesting Identity")
        val identityOp = Identity (x)
        val zIdentity  = identityOp.forward ()
        println (s"z (Identity(x)): $zIdentity")
        zIdentity.backward ()
        println (s"x.grad after Identity: ${x.grad}")

        // Reset gradient.
        x.grad = TensorD.zerosLike (x.data)

        // Test LeakyReLU.
        println ("\nTesting LeakyReLU")
        val leakyReLUOp = LeakyReLU (x)
        val zLeakyReLU  = leakyReLUOp.forward ()
        println (s"z (LeakyReLU(x)): $zLeakyReLU")
        zLeakyReLU.backward ()
        println (s"x.grad after LeakyReLU: ${x.grad}")

        // Reset gradient.
        x.grad = TensorD.zerosLike (x.data)

        // Test ELU.
        println ("\nTesting ELU")
        val eluOp = ELU (x)
        val zELU  = eluOp.forward ()
        println (s"z (ELU(x)): $zELU")
        zELU.backward ()
        println (s"x.grad after ELU: ${x.grad}")

        println ("\nAll tests in Test 4 completed.")
    end autogradTest4

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Tests the loss functions: SSE, MSE, and MAE.
     *  > runMain scalation.modeling.autograd.AutogradTest.autogradTest5
     */
    @main def autogradTest5 (): Unit =
        banner (" Autograd Loss Functions - Test 5")
        val predData   = TensorD ((2, 2, 1), 0.9, 0.1, 0.8, 0.2)
        val targetData = TensorD ((2, 2, 1), 1.0, 0.0, 1.0, 0.0)
        val pred   = Variabl (predData, name = Some ("pred"))
        val target = Variabl (targetData, name = Some ("target"))

        println ("\nTesting Loss Functions")
        // Test SSE Loss.
        println ("Testing SSE Loss")
        val sseLossOp = SSELoss (pred, target)
        val zSSE      = sseLossOp.forward ()
        println (s"z (SSE Loss): $zSSE")
        zSSE.backward ()
        println (s"pred.grad after SSE Loss: ${pred.grad}")

        // Reset gradients.
        pred.grad = TensorD.zerosLike (pred.data)

        // Test MSE Loss.
        println ("\nTesting MSE Loss")
        val mseLossOp = MSELoss (pred, target)
        val zMSE      = mseLossOp.forward ()
        println (s"z (MSE Loss): $zMSE")
        zMSE.backward ()
        println (s"pred.grad after MSE Loss: ${pred.grad}")

        // Reset gradients.
        pred.grad = TensorD.zerosLike (pred.data)

        // Test MAE Loss.
        println ("\nTesting MAE Loss")
        val maeLossOp = MAELoss (pred, target)
        val zMAE      = maeLossOp.forward ()
        println (s"z (MAE Loss): $zMAE")
        zMAE.backward ()
        println (s"pred.grad after MAE Loss: ${pred.grad}")

        println ("\nAll tests in Test 5 completed.")
    end autogradTest5

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Tests tensor operations including dot product, matrix multiplication, and batched
     *  matrix multiplication.
     *  > runMain scalation.modeling.autograd.AutogradTest.autogradTest6
     */
    @main def autogradTest6 (): Unit =
        banner ("Autograd Tensor Operations - Test 6")
        // Dot product test.
        banner ("Testing Dot Product")
        val vectorA   = TensorD.fromVector (VectorD(1, 2, 3))
        val vectorB   = TensorD.fromVector (VectorD(4, 5, 6))
        val varVecA   = Variabl (vectorA, name = Some ("vecA"))
        val varVecB   = Variabl (vectorB, name = Some ("vecB"))
        val dotResult = varVecA.dot (varVecB)
        println (s"Dot product result: $dotResult.data")
        dotResult.backward ()
        println (s"vecA.grad after dot product: ${varVecA.grad}")
        println (s"vecB.grad after dot product: ${varVecB.grad}")
        println ("✅ Dot product forward & backward test passed!")

        // Matrix multiplication test.
        banner ("Testing Matrix Multiplication (batch‑first, depth=1)")
        val C = MatrixD ((2, 3), 1, 2, 3, 4, 5, 6)
        val D = MatrixD ((3, 2), 7, 8, 9, 10, 11, 12)
        println (s"C :\n$C")
        println (s"D :\n$D")
        val matA = TensorD.fromMatrix (C, Some ((1, 2, 3)))
        val matB = TensorD.fromMatrix (D, Some ((1, 3, 2)))
        println (s"matA shape: ${matA.shape}")
        println (s"matB shape: ${matB.shape}")
        val varA = Variabl (matA, name = Some ("matA"))
        val varB = Variabl (matB, name = Some ("matB"))
        val out  = varA.matmul (varB)
        println (s"Raw TensorD result:\n${out.data}")
        val matRes = out.data(0)
        println (s"As MatrixD:\n$matRes")
        val expected = TensorD.fromMatrix (MatrixD((2, 2), 58, 64, 139, 154))
        assert (out.data == expected, s"Forward failed, expected\n$expected\nbut got\n$out")
        out.backward ()
        println (s"A.grad:\n${varA.grad}")
        println (s"B.grad:\n${varB.grad}")
        println ("✅ MatMul forward & backward test passed!")

        // Batched Matrix Multiplication test.
        banner ("Testing Batched Matrix Multiplication")
        val A0 = MatrixD ((3, 1), 1.0, 2.0, 3.0)
        val A1 = MatrixD ((3, 1), 4.0, 5.0, 6.0)
        val batchedA = TensorD (A0, A1)
        val B0 = MatrixD((1, 2), 10.0, 20.0)
        val B1 = MatrixD((1, 2), 30.0, 40.0)
        val batchedB = TensorD (B0, B1)
        val varBmmA = Variabl (batchedA, name = Some ("bmmA"))
        val varBmmB = Variabl (batchedB, name = Some ("bmmB"))
        println (s"BMM A shape: ${varBmmA.shape}")
        println (s"BMM B shape: ${varBmmB.shape}")
        val bmmResult = varBmmA.bmm (varBmmB)
        println (s"BMM result:\n${bmmResult.data}")
        bmmResult.backward ()
        println (s"bmmA.grad after BMM:\n${varBmmA.grad}")
        println (s"bmmB.grad after BMM:\n${varBmmB.grad}")
        println ("✅ BMM forward & backward test passed!")
    end autogradTest6

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Tests the Linear layer with autograd.
     *  > runMain scalation.modeling.autograd.AutogradTest.autogradTest7
     */
    @main def autogradTest7 (): Unit =
        banner ("Autograd Linear Layer - Test 7")
        val inFeatures  = 4
        val outFeatures = 3
        val linear    = Linear (inFeatures, outFeatures)
        val inputData = TensorD ((2, 4, 1), 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0)
        val inputVar  = Variabl (inputData, name = Some ("input"))
        val output    = linear (inputVar)
        println (s"Linear layer output: $output")
        println (s"Weight before: ${linear.weight.data}")
        println (s"Bias before: ${linear.bias.data}")
        output.backward ()
        println (s"Input gradient: ${inputVar.grad}")
        println (s"Weight gradient: ${linear.weight.grad}")
        println (s"Bias gradient: ${linear.bias.grad}")
    end autogradTest7

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Tests a single-layer network on the AutoMPG regression dataset.
     *  > runMain scalation.modeling.autograd.AutogradTest.autogradTest8
     */
    @main def autogradTest8 (): Unit =
        banner ("Autograd 1 Layer Net with AutoMPG (Testing against Regression) - Test 8")
        println (s"x.shape: ${x.dims}")
        println (s"y.shape: ${y.dim}")
        val tensorX = TensorD.fromMatrix (x).permute (Seq(1, 2, 0))
        val tensorY = TensorD.fromVector (y)
//      val meanX  = TensorD.meanAlongAxis (tensorX, axis=0)
//      val stdX   = TensorD.stdAlongAxis (tensorX, axis=0)
        val meanY  = TensorD.meanAlongAxis (tensorY, axis=0)
        val stdY   = TensorD.stdAlongAxis (tensorY, axis=0)
        val norm_x = TensorD.standardize (tensorX, axis=0)
        val norm_y = TensorD.standardize (tensorY, axis=0)
        println (s"norm_x.shape: ${norm_x.shape}")
        println (s"norm_y.shape: ${norm_y.shape}")
        val input    = Variabl (norm_x)
        var y_actual = Variabl (norm_y)

        case class Net() extends Module with Fit(x.dim2 - 1, x.dim2 - x.dim):
            val nf1: Int = tensorX.dim2
            val outputNodes: Int = tensorY.dim2
            val fc1: Linear = Linear (nf1, outputNodes)
            override def forward (x: Variabl): Variabl = x ~> fc1 ~> identity
        end Net

        object Net:
            def apply (): Net = new Net ()
        end Net

        val net = Net ()
        val optimizer = SGD (parameters = net.parameters, lr = 0.01, momentum = 0.9)
        val permGen   = new Optimizer_SGDM {}.permGenerator (norm_x.shape(0))
        val batchSize = 64
        val nB = norm_x.shape(0) / batchSize

        for j <- 0 to 1000 do
            optimizer.zeroGrad ()
            val batches    = permGen.igen.chop (nB)
            var totalLoss  = 0.0
            var batchCount = 0
            for ib <- batches do
                val inputBatch = Variabl (norm_x(ib))
                val yBatch     = Variabl (norm_y(ib))
                val output     = net (inputBatch)
                val loss       = mseLoss (output, yBatch)
                totalLoss     += loss.data(0)(0)(0)
                batchCount    += 1
                loss.backward ()
                optimizer.step ()
            end for
            val avgLoss = totalLoss / batchCount
            if j % 100 == 0 then println (s"Epoch $j: Loss = $avgLoss")
        end for

        println (s"Model structure: $net")
        println (s"Weight shape: ${net.fc1.weight.shape}")
        println (s"Bias shape: ${net.fc1.bias.shape}")

        val outputFinal = net (input)
        val y_pred = outputFinal * Variabl (stdY) + Variabl (meanY)
        y_actual   = y_actual * Variabl (stdY) + Variabl (meanY)
        println (s"y_pred shape: ${y_pred.shape}")
        val qof = net.diagnose (y_actual.data.flattenToVector, y_pred.data.flattenToVector)
        println (FitM.fitMap (qof, qoF_names))
        println (s"grad after convergence: ${net.fc1.weight.grad}")
        println (s"weights after convergence: ${net.fc1.weight.data}")
    end autogradTest8

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Tests a two-layer network.
     *  > runMain scalation.modeling.autograd.AutogradTest.autogradTest9
     */
    @main def autogradTest9 (): Unit =
        banner ("Autograd 2 Layer Net with AutoMPG (Testing against NeuralNet_3L - Test 9")
        println (s"x.shape: ${x.shape}")
        println (s"y.shape: ${y.dim}")
        val tensorX = TensorD.fromMatrix (x).permute (Seq(1, 2, 0))
        val tensorY = TensorD.fromVector (y)
//      val meanX  = TensorD.meanAlongAxis (tensorX, axis = 0)
//      val stdX   = TensorD.stdAlongAxis (tensorX, axis = 0)
        val meanY  = TensorD.meanAlongAxis (tensorY, axis = 0)
        val stdY   = TensorD.stdAlongAxis (tensorY, axis = 0)
        val norm_x = TensorD.standardize (tensorX, axis = 0)
        val norm_y = TensorD.standardize (tensorY, axis = 0)
        println (s"norm_x.shape: ${norm_x.shape}")
        println (s"norm_y.shape: ${norm_y.shape}")
        val input    = Variabl (norm_x)
        var y_actual = Variabl (norm_y)

        case class Net () extends Module with Fit (x.dim2 - 1, x.dim - x.dim2):
            val nf1: Int = tensorX.dim2
            val hiddenNodes: Int = 2 * nf1 + 1
            val outputNodes: Int = tensorY.dim2
            val fc1: Linear = Linear (nf1, hiddenNodes)
            val fc2: Linear = Linear (hiddenNodes, outputNodes)
            override def forward (x: Variabl): Variabl = x ~> fc1 ~> tanh ~> fc2 ~> identity
        end Net

        object Net:
            def apply (): Net = new Net ()
        end Net

        val net = Net ()
        val optimizer = Adam (parameters = net.parameters, lr = 0.002, beta1 = 0.9, beta2 = 0.999)
        val batchSize = 20
        val nB = norm_x.shape(0) / batchSize

        for j <- 0 to 400 do
            val permGen = new Optimizer_SGDM {}.permGenerator (norm_x.shape(0))
            val batches = permGen.igen.chop (nB)
            var totalLoss  = 0.0
            var batchCount = 0
            for ib <- batches do
                optimizer.zeroGrad ()
                val inputBatch = Variabl (norm_x(ib))
                val yBatch  = Variabl (norm_y(ib))
                val output  = net (inputBatch)
                val loss    = mseLoss (output, yBatch)
                totalLoss  += loss.data(0)(0)(0)
                batchCount += 1
                loss.backward ()
                optimizer.step ()
            end for
            val avgLoss = totalLoss / batchCount
            if j % 100 == 0 then println (s"Epoch $j: Loss = $avgLoss")
        end for

        println (s"Model structure: $net")
        println (s"Weight shape: ${net.fc1.weight.shape}")
        println (s"Bias shape: ${net.fc1.bias.shape}")

        val outputFinal = net(input)
        val y_pred = outputFinal * Variabl (stdY) + Variabl (meanY)
        y_actual   = y_actual * Variabl (stdY) + Variabl (meanY)
        println (s"y_pred shape: ${y_pred.shape}")
        val qof = net.diagnose (y_actual.data.flattenToVector, y_pred.data.flattenToVector)
        println (FitM.fitMap (qof, qoF_names))
        println (s"grad after convergence: ${net.fc1.weight.grad}")
        println (s"weights after convergence: ${net.fc1.weight.data}")
    end autogradTest9

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Tests a three-layer network with early stopping.
     *  > runMain scalation.modeling.autograd.AutogradTest.autogradTest10
     */
    @main def autogradTest10 (): Unit =
        banner ("Autograd 3 Layer Net with AutoMPG (Testing against NeuralNet_3L - Test 10")
        println (s"x.shape: ${x.shape}")
        println (s"y.shape: ${y.dim}")
        val tensorX = TensorD.fromMatrix (x).permute (Seq(1, 2, 0))
        val tensorY = TensorD.fromVector (y)
//      val meanX  = TensorD.meanAlongAxis (tensorX, axis = 0)
//      val stdX   = TensorD.stdAlongAxis (tensorX, axis = 0)
        val meanY  = TensorD.meanAlongAxis (tensorY, axis = 0)
        val stdY   = TensorD.stdAlongAxis (tensorY, axis = 0)
        val norm_x = TensorD.standardize (tensorX, axis = 0)
        val norm_y = TensorD.standardize (tensorY, axis = 0)
        println (s"norm_x.shape: ${norm_x.shape}")
        println (s"norm_y.shape: ${norm_y.shape}")
        val input = Variabl (norm_x)
        var y_actual = Variabl (norm_y)

        case class Net () extends Module with Fit(x.dim2 - 1, x.dim - x.dim2):
            val nf1: Int         = tensorX.dim2
            val hiddenNodes: Int = 2 * nf1 + 1
            val outputNodes: Int = tensorY.dim2
            val fc1: Linear = Linear (nf1, hiddenNodes)
            val fc2: Linear = Linear (hiddenNodes, hiddenNodes)
            val fc3: Linear = Linear (hiddenNodes, outputNodes)
            override def forward (x: Variabl): Variabl =
                val h1 = x ~> fc1 ~> tanh
                val h2 = h1 ~> fc2 ~> sigmoid
                val out = h2 ~> fc3 ~> identity
                out
        end Net

        object Net:
            def apply (): Net = new Net ()
        end Net

        val net = Net ()
        val optimizer = SGD (parameters = net.parameters, lr = 0.25, momentum = 0.90)
        val batchSize = 20
        val nB = ceil (norm_x.shape(0).toDouble / batchSize).toInt
        println (s"nB: $nB")

        object monitor extends MonitorLoss
        object opti extends Optimizer_SGDM
        object EarlyStopper extends StoppingRule
        val limit        = 15
        var stopTraining = false

        for j <- 0 to 400 if ! stopTraining do
            val permGen    = opti.permGenerator (norm_x.shape(0))
            val batches    = permGen.igen.chop (nB)
            var totalLoss  = 0.0
            var batchCount = 0
            for ib <- batches do
                optimizer.zeroGrad ()
                val inputBatch = Variabl (norm_x(ib))
                val yBatch     = Variabl (norm_y(ib))
                val output     = net (inputBatch)
                val loss       = mseLoss (output, yBatch)
                totalLoss     += loss.data(0)(0)(0)
                batchCount    += 1
                loss.backward ()
                optimizer.step ()
            end for
            val avgLoss = totalLoss / batchCount
            monitor.collectLoss (avgLoss)
            if j % 100 == 0 then println (s"Epoch $j: Loss = $avgLoss")
            val (stopParams, currentBestLoss) = EarlyStopper.stopWhenContinuous (net.parameters, avgLoss, limit)
            if stopParams != null then
                println (s"Early stopping triggered at epoch $j with best loss $currentBestLoss")
                net.setParameters (stopParams)
                stopTraining = true
            end if
        end for

        monitor.plotLoss ("NeuralNet4L")
        val varData  = y.variance
        val bestLoss = monitor.getBestLoss * varData
        println (s"Best Loss Unscaled: $bestLoss")
        println (s"Best Loss Scaled: ${monitor.getBestLoss}")
        println (s"Model structure: $net")
        println (s"Weight shape: ${net.fc1.weight.shape}")
        println (s"Bias shape: ${net.fc1.bias.shape}")

        val outputFinal = net(input)
        val y_pred = outputFinal * Variabl (stdY) + Variabl (meanY)
        y_actual   = y_actual * Variabl (stdY) + Variabl (meanY)
        println (s"y_pred shape: ${y_pred.shape}")
        val qof = net.diagnose(y_actual.data.flattenToVector, y_pred.data.flattenToVector)
        println (FitM.fitMap (qof, qoF_names))
        println (s"grad after convergence: ${net.fc1.weight.grad}")
        println (s"weights after convergence: ${net.fc1.weight.data}")
    end autogradTest10

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Tests a three-layer network with early stopping on train/test split.
     *  > runMain scalation.modeling.autograd.AutogradTest.autogradTest11
     */
    @main def autogradTest11 (): Unit =
        banner ("Autograd 3 Layer Net with AutoMPG (Testing against NeuralNet_XL - Test 11")
        val permGenSplit = TnT_Split.makePermGen (x.dim)
        val n_test = (0.4 * x.dim).toInt
        val splitIdx = TnT_Split.testIndices (permGenSplit, n_test)
        val (x_test, x_train, y_test, y_train) = TnT_Split (x, y, splitIdx)
        println (s"x.shape: ${x.shape}")
        println (s"y.shape: ${y.dim}")
        val tensorX = TensorD.fromMatrix (x_train).permute(Seq(1, 2, 0))
        val tensorY = TensorD.fromVector (y_train)
        val meanX  = TensorD.meanAlongAxis (tensorX, axis = 0)
        val stdX   = TensorD.stdAlongAxis (tensorX, axis = 0)
        val meanY  = TensorD.meanAlongAxis (tensorY, axis = 0)
        val stdY   = TensorD.stdAlongAxis (tensorY, axis = 0)
        val norm_x = TensorD.standardize (tensorX, axis = 0)
        val norm_y = TensorD.standardize (tensorY, axis = 0)
        println (s"norm_x.shape: ${norm_x.shape}")
        println (s"norm_y.shape: ${norm_y.shape}")
        val input    = Variabl (norm_x)
        var y_actual = Variabl (norm_y)

        case class Net () extends Module with Fit (x.dim2 - 1, x.dim - x.dim2):
            val nf1: Int = tensorX.dim2
            val hiddenNodes: Int = 2 * nf1 + 1
            val outputNodes: Int = tensorY.dim2
            val fc1: Linear = Linear (nf1, hiddenNodes)
            val fc2: Linear = Linear (hiddenNodes, hiddenNodes)
            val fc3: Linear = Linear (hiddenNodes, outputNodes)
            override def forward (x: Variabl): Variabl =
                val h1 = x ~> fc1 ~> tanh
                val h2 = h1 ~> fc2 ~> sigmoid
                val out = h2 ~> fc3 ~> identity
                out
        end Net

        object Net:
            def apply (): Net = new Net ()
        end Net

        val net = Net ()
        val optimizer = SGD (parameters = net.parameters, lr = 0.25, momentum = 0.90)
        val batchSize = 20
        val nB = ceil (norm_x.shape(0).toDouble / batchSize).toInt
        println (net.parameters.toString ())
        println (s"nB: $nB")

        object monitor extends MonitorLoss
        object opti extends Optimizer_SGDM
        object EarlyStopper extends StoppingRule
        val limit        = 20
        var stopTraining = false

        for j <- 0 to 400 if ! stopTraining do
            val permGen    = opti.permGenerator (norm_x.shape(0))
            val batches    = permGen.igen.chop (nB)
            var totalLoss  = 0.0
            var batchCount = 0
            for ib <- batches do
                optimizer.zeroGrad ()
                val inputBatch = Variabl (norm_x(ib))
                val yBatch     = Variabl (norm_y(ib))
                val output     = net (inputBatch)
                val loss       = mseLoss (output, yBatch)
                totalLoss     += loss.data(0)(0)(0)
                batchCount    += 1
                loss.backward ()
                optimizer.step ()
            end for
            val avgLoss = totalLoss / batchCount
            monitor.collectLoss (avgLoss)
            if j % 100 == 0 then println (s"Epoch $j: Loss = $avgLoss")
            val (stopParams, currentBestLoss) = EarlyStopper.stopWhenPatience (net.parameters, avgLoss, limit)
            if stopParams != null then
                println (s"Early stopping triggered at epoch $j with best loss $currentBestLoss")
                net.setParameters (stopParams)
                stopTraining = true
            end if
        end for

        monitor.plotLoss("NeuralNet3L")
        val varData  = y.variance
        val bestLoss = monitor.getBestLoss * varData
        println (s"Best Loss Unscaled: $bestLoss")
        println (s"Best Loss Scaled: ${monitor.getBestLoss}")
        println (s"Model structure: $net")
        println (s"Weight shape: ${net.fc1.weight.shape}")
        println (s"Bias shape: ${net.fc1.bias.shape}")

        val outputFinal = net (input)
        val y_pred = outputFinal * Variabl (stdY) + Variabl (meanY)
        y_actual   = y_actual * Variabl (stdY) + Variabl (meanY)
        println (s"y_pred shape: ${y_pred.shape}")
        banner ("Final Train Statistics")
        val qof = net.diagnose (y_actual.data.flattenToVector, y_pred.data.flattenToVector)
        println (FitM.fitMap (qof, qoF_names))
        val testX = TensorD.fromMatrix (x_test).permute (Seq(1, 2, 0))
        val testY = TensorD.fromVector (y_test)
        val testNormX  = (testX - meanX)/stdX
        val testNormY  = (testY - meanY)/stdY
        val testInput  = Variabl (testNormX)
        val testActual = Variabl (testNormY)
        val testOutput = net (testInput)
        val testPred = testOutput * Variabl (stdY) + Variabl (meanY)
        val testActualRescaled = testActual * Variabl (stdY) + Variabl (meanY)
        println (s"test_pred shape: ${testPred.shape}")
        println (s"Final Test Loss: ${mseLoss (testOutput, testActual).data(0)(0)(0)}")
        banner ("Final Test Statistics")
        val testQoF = net.diagnose (testActualRescaled.data.flattenToVector, testPred.data.flattenToVector)
        println (FitM.fitMap (testQoF, qoF_names))
    end autogradTest11

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** The `autogradTestAll` Main method runs all autograd tests sequentially.
     */
    @main def autogradTestAll (): Unit =
        autogradTest1 ()
        autogradTest2 ()
        autogradTest3 ()
        autogradTest4 ()
        autogradTest5 ()
        autogradTest6 ()
        autogradTest7 ()
        autogradTest8 ()
        autogradTest9 ()
        autogradTest10 ()
        autogradTest11 ()
        banner ("All Autograd Tests Completed")
    end autogradTestAll

end AutogradTest

