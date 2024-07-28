package gregtech.common.pipelikeold.itempipe.net;

import gregtech.api.cover.Cover;
import gregtech.api.graphnet.pipenetold.WorldPipeNetSimple;
import gregtech.api.graphnet.edge.NetEdge;
import gregtech.api.graphnet.pipenetold.tile.IPipeTile;
import gregtech.common.covers.CoverConveyor;
import gregtech.common.covers.CoverItemFilter;
import gregtech.common.covers.ItemFilterMode;
import gregtech.common.covers.ManualImportExportMode;
import gregtech.common.pipelikeold.itempipe.ItemPipeType;
import gregtech.common.pipelikeold.itempipe.tile.TileEntityItemPipe;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;

public class WorldItemPipeNet extends WorldPipeNetSimple<ItemPipeProperties, ItemPipeType> {

    private static final String DATA_ID = "gregtech.item_pipe_net";

    public static WorldItemPipeNet getWorldPipeNet(World world) {
        WorldItemPipeNet netWorldData = (WorldItemPipeNet) world.loadData(WorldItemPipeNet.class, DATA_ID);
        if (netWorldData == null) {
            netWorldData = new WorldItemPipeNet(DATA_ID);
            world.setData(DATA_ID, netWorldData);
        }
        netWorldData.setWorldAndInit(world);
        return netWorldData;
    }

    public WorldItemPipeNet(String name) {
        super(name, true, false);
    }

    @Override
    protected Capability<?>[] getConnectionCapabilities() {
        return new Capability[] { CapabilityItemHandler.ITEM_HANDLER_CAPABILITY };
    }

    @Override
    protected Class<? extends IPipeTile<ItemPipeType, ItemPipeProperties, NetEdge>> getBasePipeClass() {
        return TileEntityItemPipe.class;
    }

    @Override
    protected IEdgePredicateOld<?> getPredicate(Cover thisCover, Cover neighbourCover) {
        ItemEdgePredicate predicate = new ItemEdgePredicate();
        if (thisCover instanceof CoverItemFilter filter &&
                filter.getFilterMode() != ItemFilterMode.FILTER_INSERT) {
            predicate.setSourceFilter(filter.getFilterContainer());
        }
        if (neighbourCover instanceof CoverItemFilter filter &&
                filter.getFilterMode() != ItemFilterMode.FILTER_EXTRACT) {
            predicate.setTargetFilter(filter.getFilterContainer());
        }
        if (thisCover instanceof CoverConveyor conveyor) {
            if (conveyor.getManualImportExportMode() == ManualImportExportMode.DISABLED) {
                predicate.setShutteredSource(true);
            } else if (conveyor.getManualImportExportMode() == ManualImportExportMode.FILTERED) {
                predicate.setSourceFilter(conveyor.getItemFilterContainer());
            }
        }
        if (neighbourCover instanceof CoverConveyor conveyor) {
            if (conveyor.getManualImportExportMode() == ManualImportExportMode.DISABLED) {
                predicate.setShutteredTarget(true);
            } else if (conveyor.getManualImportExportMode() == ManualImportExportMode.FILTERED) {
                predicate.setTargetFilter(conveyor.getItemFilterContainer());
            }
        }
        // TODO should robot arms apply rate limits to edge predicates?
        return shutterify(predicate, thisCover, neighbourCover);
    }

    @Override
    protected void writeNodeData(ItemPipeProperties nodeData, NBTTagCompound tagCompound) {
        tagCompound.setInteger("Priority", nodeData.getPriority());
        tagCompound.setFloat("Rate", nodeData.getTransferRate());
    }

    @Override
    protected ItemPipeProperties readNodeData(NBTTagCompound tagCompound) {
        return new ItemPipeProperties(tagCompound.getInteger("Priority"), tagCompound.getFloat("Rate"));
    }
}
