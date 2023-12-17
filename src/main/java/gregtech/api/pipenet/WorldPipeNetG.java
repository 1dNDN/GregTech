package gregtech.api.pipenet;

import gregtech.api.cover.Cover;
import gregtech.api.pipenet.block.IPipeType;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.pipenet.tile.TileEntityPipeBase;

import gregtech.common.covers.CoverShutter;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.CHManyToManyShortestPaths;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class WorldPipeNetG<NodeDataType extends INodeData<NodeDataType>,
        PipeType extends Enum<PipeType> & IPipeType<NodeDataType>> extends WorldSavedData {

    private WeakReference<World> worldRef = new WeakReference<>(null);
    private final Graph<NodeG<PipeType, NodeDataType>, NetEdge> pipeGraph;
    private final Map<BlockPos, NodeG<PipeType, NodeDataType>> pipeMap = new Object2ObjectOpenHashMap<>();

    private ShortestPathsAlgorithm<PipeType, NodeDataType> shortestPaths;

    private boolean validAlgorithmInstance = false;

    public WorldPipeNetG(String name) {
        super(name);
        this.pipeGraph = new SimpleWeightedGraph<>(NetEdge.class);
    }

    public World getWorld() {
        return this.worldRef.get();
    }

    protected void setWorldAndInit(World world) {
        if (world != this.worldRef.get()) {
            this.worldRef = new WeakReference<>(world);
            onWorldSet();
        }
    }

    public static String getDataID(final String baseID, final World world) {
        if (world == null || world.isRemote)
            throw new RuntimeException("WorldPipeNetG should only be created on the server!");
        int dimension = world.provider.getDimension();
        return dimension == 0 ? baseID : baseID + '.' + dimension;
    }

    protected void onWorldSet() {
        this.rebuildShortestPaths();
    }

    /**
     * Preferred override. Only collects a fresh TE from the server if the provided TE is not loaded.
     * 
     * @param tile The {@link TileEntityPipeBase} that paths are being requested for
     * @return the ordered list of paths associated with the {@link TileEntityPipeBase}
     */
    public List<NetPath<PipeType, NodeDataType>> getPaths(TileEntityPipeBase<PipeType, NodeDataType> tile) {
        return getPaths(this.pipeMap.get(tile.getPipePos()), tile);
    }

    /**
     * Special-case override. Forces the collection of a fresh TE from the server.
     * 
     * @param pos The {@link BlockPos} that paths are being requested for
     * @return the ordered list of paths associated with the {@link BlockPos}
     */
    public List<NetPath<PipeType, NodeDataType>> getPaths(BlockPos pos) {
        return getPaths(this.pipeMap.get(pos), null);
    }

    public List<NetPath<PipeType, NodeDataType>> getPaths(
                                                                            @Nullable NodeG<PipeType, NodeDataType> node,
                                                                            @Nullable TileEntityPipeBase<PipeType, NodeDataType> tile) {
        if (node == null) return new ObjectArrayList<>();

        node.heldMTE = tile;

        if (!this.validAlgorithmInstance) this.rebuildShortestPaths();

        List<NetPath<PipeType, NodeDataType>> cache = node.getPathCache();
        if (cache != null) {
            return verifyList(cache, node);
        }

        List<NetPath<PipeType, NodeDataType>> list = this.shortestPaths.getPathsList(node);
        return verifyList(node.setPathCache(list), node);
    }

    /**
     * Verification removes paths ending in unloaded TEs,
     * paths that don't connect to anything,
     * and all paths if the source TE is unloaded.
      */
    protected List<NetPath<PipeType, NodeDataType>> verifyList(List<NetPath<PipeType, NodeDataType>> list,
                                                                                 NodeG<PipeType, NodeDataType> source) {
        if (!verifyNode(source)) return new ObjectArrayList<>();
        return list.stream().filter(a -> verifyNode(a.getTargetNode())).collect(Collectors.toList());
    }

    protected boolean verifyNode(NodeG<PipeType, NodeDataType> node) {
        if (!node.hasConnecteds()) return false;
        node.heldMTE = verifyTE(node.getNodePos(), (TileEntity) node.heldMTE);
        return node.heldMTE != null;
    }

    @Nullable
    protected TileEntityPipeBase<PipeType, NodeDataType> verifyTE(BlockPos pos, @Nullable TileEntity te) {
        if (!getWorld().loadedTileEntityList.contains(te)) {
            return castTE(getWorld().getTileEntity(pos));
        }
        return castTE(te);
    }

    @Nullable
    protected TileEntityPipeBase<PipeType, NodeDataType> castTE(TileEntity te) {
        if (te instanceof TileEntityPipeBase<?, ?> pipe) {
            if (!getBasePipeClass().isAssignableFrom(pipe.getClass())) {
                return null;
            }
            return (TileEntityPipeBase<PipeType, NodeDataType>) pipe;
        }
        return null;
    }

    protected abstract Class<? extends IPipeTile<PipeType, NodeDataType>> getBasePipeClass();

    public void addNode(BlockPos nodePos, NodeDataType nodeData, int mark, int openConnections, boolean isActive,
                        @Nullable IPipeTile<PipeType, NodeDataType> heldMTE) {
        if (heldMTE == null) {
            heldMTE = castTE(this.getWorld().getTileEntity(nodePos));
        }
        NodeG<PipeType, NodeDataType> node = new NodeG<>(nodeData, openConnections, mark, isActive, heldMTE);
        if (!canAttachNode(nodeData)) return;

        this.addNode(node);
        addEdges(node);
    }

    private void addEdges(NodeG<PipeType, NodeDataType> node) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            BlockPos offsetPos = node.getNodePos().offset(facing);
            NodeG<PipeType, NodeDataType> nodeOffset = this.pipeMap.get(offsetPos);
            if (nodeOffset != null && this.canNodesConnect(node, facing, nodeOffset)) {
                this.addEdge(node, nodeOffset);
            }
        }
    }

    protected final boolean canNodesConnect(NodeG<PipeType, NodeDataType> source, EnumFacing nodeFacing,
                                            NodeG<PipeType, NodeDataType> target) {
        return areNodeBlockedConnectionsCompatible(source, nodeFacing, target) &&
                areMarksCompatible(source.mark, target.mark) && areNodesCustomContactable(source.data, target.data);
    }

    private static boolean areMarksCompatible(int mark1, int mark2) {
        return mark1 == mark2 || mark1 == Node.DEFAULT_MARK || mark2 == Node.DEFAULT_MARK;
    }

    private boolean areNodeBlockedConnectionsCompatible(NodeG<PipeType, NodeDataType> source, EnumFacing nodeFacing,
                                                        NodeG<PipeType, NodeDataType> target) {
        return !source.isBlocked(nodeFacing) && !target.isBlocked(nodeFacing.getOpposite());
    }

    protected boolean areNodesCustomContactable(NodeDataType source, NodeDataType target) {
        return true;
    }

    protected boolean canAttachNode(NodeDataType nodeData) {
        return true;
    }

    public void updateBlockedConnections(BlockPos nodePos, EnumFacing side, boolean isBlocked) {
        NodeG<PipeType, NodeDataType> node = pipeMap.get(nodePos);
        if (node == null || node.isBlocked(side) == isBlocked) return;

        node.setBlocked(side, isBlocked);
        NodeG<PipeType, NodeDataType> nodeOffset = pipeMap.get(nodePos.offset(side));
        if (nodeOffset == null) return;

        if (!node.isBlocked(side) && !nodeOffset.isBlocked(side.getOpposite())) {
            addEdge(node, nodeOffset);
        } else {
            removeEdge(node, nodeOffset);
        }
    }

    public void updateMark(BlockPos nodePos, int newMark) {
        NodeG<PipeType, NodeDataType> node = pipeMap.get(nodePos);
        if (node == null) return;

        int oldMark = node.mark;
        node.mark = newMark;

        for (EnumFacing side : EnumFacing.VALUES) {
            NodeG<PipeType, NodeDataType> nodeOffset = pipeMap.get(nodePos.offset(side));
            if (nodeOffset == null) continue;
            if (!areNodeBlockedConnectionsCompatible(node, side, nodeOffset) ||
                    !areNodesCustomContactable(node.data, nodeOffset.data))
                continue;
            if (areMarksCompatible(oldMark, nodeOffset.mark) == areMarksCompatible(newMark, nodeOffset.mark)) continue;

            if (areMarksCompatible(newMark, nodeOffset.mark)) {
                addEdge(node, nodeOffset);
            } else {
                removeEdge(node, nodeOffset);
            }
        }
    }

    public boolean hasNode(BlockPos pos) {
        return pipeMap.containsKey(pos);
    }

    public void addNodeSilent(NodeG<PipeType, NodeDataType> node) {
        pipeGraph.addVertex(node);
        this.pipeMap.put(node.getNodePos(), node);
        // we do not need to invalidate the cache, because just adding a node means it's not connected to anything.
    }

    public void addNode(NodeG<PipeType, NodeDataType> node) {
        addNodeSilent(node);
        this.markDirty();
    }

    public void addEdge(NodeG<PipeType, NodeDataType> source, NodeG<PipeType, NodeDataType> target) {
        addEdge(source, target, source.data.getWeightFactor() + target.data.getWeightFactor());
        this.validAlgorithmInstance = false;
    }

    public NodeG<PipeType, NodeDataType> getNode(BlockPos pos) {
        return this.pipeMap.get(pos);
    }

    public void addEdge(NodeG<PipeType, NodeDataType> source, NodeG<PipeType, NodeDataType> target, double weight) {
        if (pipeGraph.addEdge(source, target) != null) {
            if (NetGroup.mergeEdge(source, target)) {
                new NetGroup<>(this.pipeGraph).addNodes(source, target);
            }
            pipeGraph.setEdgeWeight(source, target, weight);
            this.validAlgorithmInstance = false;
            this.markDirty();
        }
    }

    public void predicateEdge(BlockPos pos, EnumFacing faceToNeighbour) {
        NodeG<PipeType, NodeDataType> source = this.pipeMap.get(pos);
        NodeG<PipeType, NodeDataType> target = this.pipeMap.get(pos.offset(faceToNeighbour));
        Cover thisCover = source.heldMTE.getCoverableImplementation().getCoverAtSide(faceToNeighbour);
        Cover neighbourCover = target.heldMTE.getCoverableImplementation().getCoverAtSide(faceToNeighbour.getOpposite());
        List<Predicate<Object>> filters = new ArrayList<>();
        if (thisCover instanceof CoverShutter) {
            filters.add(o -> !((CoverShutter) thisCover).isWorkingEnabled());
        }
        if (neighbourCover instanceof CoverShutter) {
            filters.add(o -> !((CoverShutter) neighbourCover).isWorkingEnabled());
        }
        filters.add(getPredicate(thisCover, neighbourCover));
        NetEdge edge = this.pipeGraph.getEdge(source, target);
        edge.setPredicates(filters);
    }

    protected abstract Predicate<Object> getPredicate(Cover thisCover, Cover neighbourCover);

    public void removeEdge(NodeG<PipeType, NodeDataType> source, NodeG<PipeType, NodeDataType> target) {
        if (source.getGroup() != null && source.getGroup().splitEdge(source, target)) {
            this.validAlgorithmInstance = false;
            this.markDirty();
        }
    }

    public void removeNode(BlockPos pos) {
        this.removeNode(this.pipeMap.get(pos));
    }

    public void removeNode(NodeG<PipeType, NodeDataType> node) {
        if (node.getGroup() != null && node.getGroup().splitNode(node)) {
            // if the node has no group, then it isn't connected to anything, and thus the cache is still valid
            this.validAlgorithmInstance = false;
        } else {
            this.pipeGraph.removeVertex(node);
        }
        this.markDirty();
    }

    public boolean markNodeAsActive(BlockPos nodePos, boolean isActive) {
        NodeG<PipeType, NodeDataType> node = this.pipeMap.get(nodePos);
        if (node != null && node.isActive != isActive) {
            node.isActive = isActive;
            this.markDirty();
            return true;
        }
        return false;
    }

    protected void rebuildShortestPaths() {
        this.shortestPaths = new ShortestPathsAlgorithm<>(pipeGraph);
        this.validAlgorithmInstance = true;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        NBTTagList allPipeNodes = nbt.getTagList("PipeNodes", Constants.NBT.TAG_COMPOUND);
        NBTTagList allNetEdges = nbt.getTagList("NetEdges", Constants.NBT.TAG_COMPOUND);
        Map<Long, NodeG<PipeType, NodeDataType>> longPosMap = new Long2ObjectOpenHashMap<>();
        for (int i = 0; i < allPipeNodes.tagCount(); i++) {
            NBTTagCompound pNodeTag = allPipeNodes.getCompoundTagAt(i);
            NodeG<PipeType, NodeDataType> node = new NodeG<>(pNodeTag, this);
            longPosMap.put(node.getLongPos(), node);
            this.addNodeSilent(node);
        }
        for (int i = 0; i < allNetEdges.tagCount(); i++) {
            NBTTagCompound gEdgeTag = allNetEdges.getCompoundTagAt(i);
            new NetEdge.Builder<>(longPosMap, gEdgeTag, this::addEdge).addIfBuildable();
        }
    }

    @NotNull
    @Override
    public NBTTagCompound writeToNBT(@NotNull NBTTagCompound compound) {
        NBTTagList allPipeNodes = new NBTTagList();
        for (NodeG<PipeType, NodeDataType> node : pipeGraph.vertexSet()) {
            NBTTagCompound nodeTag = node.serializeNBT();
            NBTTagCompound dataTag = new NBTTagCompound();
            writeNodeData(node.data, dataTag);
            nodeTag.setTag("Data", dataTag);
            allPipeNodes.appendTag(nodeTag);
        }
        compound.setTag("PipeNodes", allPipeNodes);

        NBTTagList allNetEdges = new NBTTagList();
        for (NetEdge edge : pipeGraph.edgeSet()) {
            allNetEdges.appendTag(edge.serializeNBT());
        }
        compound.setTag("NetEdges", allNetEdges);
        return compound;
    }

    /**
     * Serializes node data into specified tag compound
     * Used for writing persistent node data
     */
    protected abstract void writeNodeData(NodeDataType nodeData, NBTTagCompound tagCompound);

    /**
     * Deserializes node data from specified tag compound
     * Used for reading persistent node data
     */
    protected abstract NodeDataType readNodeData(NBTTagCompound tagCompound);

    // CHManyToManyShortestPaths is a very good algorithm because our graph will be extremely sparse.
    protected static final class ShortestPathsAlgorithm<PT extends Enum<PT> & IPipeType<NDT>, NDT extends INodeData<NDT>>
                                                       extends CHManyToManyShortestPaths<NodeG<PT, NDT>, NetEdge> {

        public ShortestPathsAlgorithm(Graph<NodeG<PT, NDT>, NetEdge> graph) {
            super(graph);
        }

        public List<NetPath<PT, NDT>> getPathsList(NodeG<PT, NDT> source) {
            if (!graph.containsVertex(source)) {
                throw new IllegalArgumentException("graph must contain the source vertex");
            }
            List<NetPath<PT, NDT>> paths = new ObjectArrayList<>();
            // if the source has no group, it has no paths.
            if (source.getGroup() == null) return paths;
            ManyToManyShortestPaths<NodeG<PT, NDT>, NetEdge> manyToManyPaths = getManyToManyPaths(
                    Collections.singleton(source), source.getGroup().getNodes());
            for (NodeG<PT, NDT> v : source.getGroup().getNodes()) {
                // update mutably so that the returned paths are also updated
                if (v.equals(source)) {
                    source.sync(v);
                    continue;
                }

                GraphPath<NodeG<PT, NDT>, NetEdge> path = manyToManyPaths.getPath(source, v);
                if (path != null) {
                    paths.add(new NetPath<>(path));
                }
            }
            paths.sort(Comparator.comparingDouble(NetPath::getWeight));
            return paths;
        }
    }
}
