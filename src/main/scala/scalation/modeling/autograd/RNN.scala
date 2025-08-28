
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  Praveen Rangavajhula
 *  @version 2.0
 *  @date    Fri April 25 19:48:13 EDT 2025
 *  @see     LICENSE (MIT style license file).
 *
 *  @note    Autograd: Recurrent Neural Networks
 */

package scalation
package modeling
package autograd

import scalation.mathstat.TensorD

import TensorInitializers._

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `RNN` class ...
 */
class RNN (inputSize: Int, hiddenSize: Int, numLayers: Int = 1, activation: String = "tanh")
          (using ops: AutogradOps)
      extends BaseModule:

    // One RNNBase ( = one UNROLLED layer ) per level
    private val layers: IndexedSeq [RNNBase] =
        (0 until numLayers).map { layerIdx =>
            val inDim = if layerIdx == 0 then inputSize else hiddenSize
            RNNBase ("rnn", inDim, hiddenSize, activation = activation)
        }

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the parameters.
     */
    override def parameters: IndexedSeq [Variabl] = layers.flatMap (_.parameters)

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Forward through all layers.
     * `inputSeq`  – Seq [Variabl]  (length = seqLen)
     *               shape of each element:  (inputDim, 1, batch)
     * `h0`        – optional initial hidden states for every
     *               layer (Seq with length = numLayers).
     * RETURNS:
     *   outputs   – Seq [Variabl] (seqLen) – from top layer
     *   h_n       – Seq [Variabl] (numLayers) final hidden states
     */
    def forward (inputSeq: IndexedSeq [Variabl], h0: Option [IndexedSeq [Variabl]] = None,
                 tbptt: Int = 0):                                                            // 0 ⇒ no truncation
                (IndexedSeq [Variabl], IndexedSeq [Variabl]) =

        require (inputSeq.nonEmpty, "Input sequence cannot be empty")

        val batchSize = inputSeq.head.shape.last

        // One hidden Variabl per layer
        val hidden = h0.getOrElse { layers.map (_.cell.initialTrackingStates (batchSize).head) }

        var layerInput: IndexedSeq [Variabl] = inputSeq                                     // sequence flowing upward
        val finalHidden = collection.mutable.ArrayBuffer.empty [Variabl]

        // ----- pass sequence through each RNN layer -----
        for (layer, hInit) <- layers.zip (hidden) do                                         // forward this layer through all time steps
            val (layerOutput, hLast) = layer.forward (layerInput, Some (hInit), tbptt)
            layerInput = layerOutput                                                         // becomes input to next layer
            finalHidden.append (hLast)                                                       // store final hidden of this layer

        val outputsTop = layerInput                                                          // after last layer
        (outputsTop, finalHidden.toIndexedSeq)
    end forward

end RNN


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `RNN` object ...
 */
object RNN:

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Factory method for creating a standard RNN instance.
     *  @param inputSize   number of features in the input at each time step
     *  @param hiddenSize  number of features in the hidden state
     *  @param numLayers   number of stacked RNN layers (default = 1)
     *  @param activation  nonlinearity to apply ("tanh" or "relu", default = "tanh")
     *  @return an instance of RNN
     */
    def apply ( inputSize: Int, hiddenSize: Int, numLayers: Int = 1, activation: String = "tanh")
              (using ops: AutogradOps): RNN =
        new RNN (inputSize, hiddenSize, numLayers, activation)

end RNN


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `RNNBase` class ...
 */
class RNNBase (val cell: RNNCell) // (using ops: AutogradOps)
      extends BaseModule (IndexedSeq.empty):

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    override def parameters: IndexedSeq[Variabl] = cell.parameters

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    def forward (xs: IndexedSeq [Variabl], h0: Option [Variabl] = None, tbptt: Int = 0):
                (IndexedSeq [Variabl], Variabl) =

        require (xs.nonEmpty, "Input sequence must be non‑empty")
        val batchSize = xs.head.shape.head
        var h = h0.getOrElse (cell.initialTrackingStates (batchSize).head)

        // Helper: detach `h` if TBPTT is active and step is a multiple of k
        inline def maybeDetach(step: Int): Unit =
            if tbptt > 0 && step % tbptt == 0 then h = h.detach()

        val outputs = xs.zipWithIndex.map { case (x, t) =>
            maybeDetach (t)                                                 // TBPTT gate
            val next = cell (IndexedSeq(x, h)).head                         // cell returns Seq(h_t)
            h = next                                                        // update hidden
            next
        }

        (outputs, h)
    end forward

end RNNBase


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `RNNBase` object ...
 */
object RNNBase:

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create an RNNBase using the specified cell type and dimensions.
     *  @param cellType    "rnn", "gru", or "lstm"
     *  @param inputSize   number of input features (nx)
     *  @param hiddenSize  number of hidden units (na)
     *  @return an RNNBase instance using the specified cell
     */
    def apply (cellType: String, inputSize: Int, hiddenSize: Int, activation: String = "tanh")
              (using ops: AutogradOps): RNNBase =
        val cell = cellType match
            case "rnn"  => RNNCell (inputSize, hiddenSize, activation)
//          case "gru"  => GRUCell (inputSize, hiddenSize)
//          case "lstm" => LSTMCell (inputSize, hiddenSize)
            case other  => throw new IllegalArgumentException (s"Unsupported cell type: $other")

        new RNNBase (cell)
    end apply

end RNNBase


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `RNNCell` class supports a simple RNN cell that updates the hidden state:
 *  h' = activation(W_ih * x + b_ih + W_hh * h + b_hh) using two biases instead of one.
 *  @param inputSize   number of input features
 *  @param hiddenSize  number of hidden units
 *  @param activation  activation function to use: "tanh" (default) or "relu"
 */
class RNNCell (inputSize: Int, hiddenSize: Int, activation: String = "tanh")
              (using ops: AutogradOps)
      extends RNNCellBase (inputSize, hiddenSize):

    override protected def weightInit: (Int, Int, Int) => TensorD =
        if activation == "relu" then heInit else xavierInit

    override def numTrackingStates: Int = 1

    override def forward (inputs: IndexedSeq [Variabl]): IndexedSeq [Variabl] =
        inputs match
        case IndexedSeq (input, hPrev) =>
            val xProj = W_ih.bmm(input) + b_ih
            val hProj = W_hh.bmm(hPrev) + b_hh
            val preAct = xProj + hProj

            val hNext = activation match
                case "tanh" => tanh (preAct)
                case "relu" => relu (preAct)
                case other => throw new IllegalArgumentException (s"Unsupported activation: $other")

            IndexedSeq (hNext)

        case _ =>
            throw new IllegalArgumentException (s"RNNCell expects exactly 2 inputs (input, hPrev), got ${inputs.length}")

    end forward

end RNNCell


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `RNNCell` object ...
 */
object RNNCell:

    def apply (inputSize: Int, hiddenSize: Int, activation: String = "tanh")
              (using ops: AutogradOps): RNNCell =
        new RNNCell (inputSize, hiddenSize, activation)

end RNNCell


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `RNNCellBase` abstract class ...
 */
abstract class RNNCellBase (val inputSize: Int, val hiddenSize: Int)(using ops: AutogradOps)
         extends SeqModule (IndexedSeq.empty):

    protected def weightInit: (Int, Int, Int) => TensorD = xavierInit

    val W_ih: Variabl = Variabl (weightInit (1, hiddenSize, inputSize), name = Some ("W_ih"))
    val W_hh: Variabl = Variabl (weightInit (1, hiddenSize, hiddenSize), name = Some ("W_hh"))
    val b_ih: Variabl = Variabl (zeros(1, hiddenSize, 1), name = Some ("b_ih"))
    val b_hh: Variabl = Variabl (zeros(1, hiddenSize, 1), name = Some ("b_hh"))

    def numTrackingStates: Int

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create a batch of zero-initialized tracking states.
     *  You pass in the batch size to get properly shaped tensors: (hiddenSize, 1, batchSize)
     */
    def initialTrackingStates (batchSize: Int): IndexedSeq [Variabl] =
        IndexedSeq.fill (numTrackingStates) { Variabl(TensorD.fill (batchSize, hiddenSize, 1, 0.0)) }

    override def parameters: IndexedSeq [Variabl] = IndexedSeq (W_ih, W_hh, b_ih, b_hh)

end RNNCellBase

