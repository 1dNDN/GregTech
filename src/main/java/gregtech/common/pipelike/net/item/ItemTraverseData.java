package gregtech.common.pipelike.net.item;

import gregtech.api.graphnet.IGraphNet;
import gregtech.api.graphnet.edge.SimulatorKey;
import gregtech.api.graphnet.pipenet.FlowWorldPipeNetPath;
import gregtech.api.graphnet.pipenet.WorldPipeNetNode;
import gregtech.api.graphnet.predicate.test.ItemTestObject;
import gregtech.api.graphnet.traverse.AbstractTraverseData;
import gregtech.api.graphnet.traverse.util.ReversibleLossOperator;
import gregtech.api.util.GTUtility;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

public class ItemTraverseData extends AbstractTraverseData<WorldPipeNetNode, FlowWorldPipeNetPath> {

    private final BlockPos sourcePos;
    private final EnumFacing inputFacing;

    public ItemTraverseData(IGraphNet net, ItemTestObject testObject, SimulatorKey simulator, long queryTick,
                            BlockPos sourcePos, EnumFacing inputFacing) {
        super(net, testObject, simulator, queryTick);
        this.sourcePos = sourcePos;
        this.inputFacing = inputFacing;
    }

    @Override
    public ItemTestObject getTestObject() {
        return (ItemTestObject) super.getTestObject();
    }

    @Override
    public boolean prepareForPathWalk(FlowWorldPipeNetPath path, long flow) {
        return flow <= 0;
    }

    @Override
    public ReversibleLossOperator traverseToNode(WorldPipeNetNode node, long flowReachingNode) {
        return ReversibleLossOperator.IDENTITY;
    }

    @Override
    public long finalizeAtDestination(WorldPipeNetNode destination, long flowReachingDestination) {
        long availableFlow = flowReachingDestination;
        for (var capability : destination.getTileEntity().getTargetsWithCapabilities(destination).entrySet()) {
            if (GTUtility.arePosEqual(destination.getEquivalencyData(), sourcePos) &&
                    capability.getKey() == inputFacing)
                continue; // anti insert-to-our-source logic

            IItemHandler container = capability.getValue()
                    .getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, capability.getKey().getOpposite());
            if (container != null) {
                availableFlow = IItemTransferController.CONTROL.get(destination.getTileEntity().getCoverHolder()
                                .getCoverAtSide(capability.getKey())).insertToHandler(getTestObject(),
                        (int) Math.min(Integer.MAX_VALUE, availableFlow), container, getSimulatorKey() != null);
            }
        }
        return flowReachingDestination - availableFlow;
    }
}
