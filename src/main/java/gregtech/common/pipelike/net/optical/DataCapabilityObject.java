package gregtech.common.pipelike.net.optical;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.data.IDataAccess;
import gregtech.api.capability.data.query.DataAccessFormat;
import gregtech.api.capability.data.query.DataQueryObject;
import gregtech.api.graphnet.pipenet.BasicWorldPipeNetPath;
import gregtech.api.graphnet.pipenet.WorldPipeNet;
import gregtech.api.graphnet.pipenet.WorldPipeNetNode;
import gregtech.api.graphnet.pipenet.physical.IPipeCapabilityObject;
import gregtech.api.graphnet.pipenet.physical.tile.PipeTileEntity;
import gregtech.api.graphnet.predicate.test.IPredicateTestObject;
import gregtech.api.util.GTUtility;
import gregtech.common.pipelike.net.SlowActiveWalker;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Set;

public class DataCapabilityObject implements IPipeCapabilityObject, IDataAccess {

    private final WorldPipeNet net;

    private @Nullable PipeTileEntity tile;

    private final Set<DataQueryObject> recentQueries = GTUtility.createWeakHashSet();

    public <N extends WorldPipeNet & BasicWorldPipeNetPath.Provider> DataCapabilityObject(@NotNull N net) {
        this.net = net;
    }

    private BasicWorldPipeNetPath.Provider getProvider() {
        return (BasicWorldPipeNetPath.Provider) net;
    }

    @Override
    public void setTile(@Nullable PipeTileEntity tile) {
        this.tile = tile;
    }

    @Override
    public boolean accessData(@NotNull DataQueryObject queryObject) {
        if (tile == null) return false;
        // if the add call fails (because the object already exists in the set) then do not recurse
        if (!recentQueries.add(queryObject)) return false;

        for (Iterator<BasicWorldPipeNetPath> it = getPaths(); it.hasNext();) {
            BasicWorldPipeNetPath path = it.next();
            WorldPipeNetNode destination = path.getTargetNode();
            for (var capability : destination.getTileEntity().getTargetsWithCapabilities(destination).entrySet()) {
                IDataAccess access = capability.getValue()
                        .getCapability(GregtechTileCapabilities.CAPABILITY_DATA_ACCESS,
                                capability.getKey().getOpposite());
                if (access != null) {
                    queryObject.setShouldTriggerWalker(false);
                    boolean cancelled = access.accessData(queryObject);
                    if (queryObject.shouldTriggerWalker()) {
                        SlowActiveWalker.dispatch(tile.getWorld(), path, 1);
                    }
                    if (cancelled) return true;
                }
            }
        }
        return false;
    }

    @Override
    public DataAccessFormat getFormat() {
        return DataAccessFormat.UNIVERSAL;
    }

    private Iterator<BasicWorldPipeNetPath> getPaths() {
        assert tile != null;
        long tick = FMLCommonHandler.instance().getMinecraftServerInstance().getTickCounter();
        return getProvider().getPaths(net.getNode(tile.getPos()), IPredicateTestObject.INSTANCE, null, tick);
    }

    @Override
    public Capability<?>[] getCapabilities() {
        return WorldOpticalNet.CAPABILITIES;
    }

    @Override
    public <T> T getCapabilityForSide(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == GregtechTileCapabilities.CAPABILITY_DATA_ACCESS) {
            return GregtechTileCapabilities.CAPABILITY_DATA_ACCESS.cast(this);
        }
        return null;
    }
}
