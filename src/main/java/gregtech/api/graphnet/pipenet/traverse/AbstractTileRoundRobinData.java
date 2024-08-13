package gregtech.api.graphnet.pipenet.traverse;

import gregtech.api.graphnet.edge.SimulatorKey;
import gregtech.api.graphnet.pipenet.WorldPipeNetNode;
import gregtech.api.graphnet.pipenet.physical.tile.PipeCapabilityWrapper;
import gregtech.api.graphnet.traverse.IRoundRobinData;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import org.apache.commons.lang3.mutable.MutableByte;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

public abstract class AbstractTileRoundRobinData implements IRoundRobinData<WorldPipeNetNode> {

    private final MutableByte pointer = new MutableByte();

    // I'm sorry but a weak Object2Byte map doesn't exist
    private final Map<SimulatorKey, MutableByte> simulatorMap = new WeakHashMap<>();

    @Override
    @MustBeInvokedByOverriders
    public void resetIfFinished(WorldPipeNetNode node, @Nullable SimulatorKey simulator) {
        if (!hasNextInternalDestination(node, simulator)) getPointer(simulator).setValue(0);
    }

    public @NotNull MutableByte getPointer(@Nullable SimulatorKey simulator) {
        if (simulator == null) return pointer;
        MutableByte value = simulatorMap.get(simulator);
        if (value == null) {
            value = new MutableByte();
            simulatorMap.put(simulator, value);
        }
        return value;
    }

    public final boolean pointerFinished(@Nullable SimulatorKey simulator) {
        return pointerFinished(getPointer(simulator));
    }

    public final boolean pointerFinished(@NotNull MutableByte pointer) {
        return pointer.byteValue() >= EnumFacing.VALUES.length;
    }

    public final EnumFacing getPointerFacing(SimulatorKey simulator) {
        MutableByte pointer = getPointer(simulator);
        if (pointerFinished(pointer)) throw new IllegalStateException("Pointer is finished!");
        return EnumFacing.VALUES[pointer.byteValue()];
    }

    public boolean hasCapabilityAtPointer(@NotNull Capability<?> capability, WorldPipeNetNode node,
                                          @Nullable SimulatorKey simulator) {
        return getCapabilityAtPointer(capability, node, simulator) != null;
    }

    @Nullable
    public <E> E getCapabilityAtPointer(@NotNull Capability<E> capability, WorldPipeNetNode node,
                                        @Nullable SimulatorKey simulator) {
        if (pointerFinished(simulator)) return null;
        PipeCapabilityWrapper wrapper = node.getTileEntity().getWrapperForNode(node);
        EnumFacing pointer = getPointerFacing(simulator);

        if (!wrapper.isActive(pointer) || !wrapper.supports(capability)) return null;
        TileEntity target = node.getTileEntity().getTargetWithCapabilities(node, pointer);
        return target == null ? null : target.getCapability(capability, pointer.getOpposite());
    }
}
