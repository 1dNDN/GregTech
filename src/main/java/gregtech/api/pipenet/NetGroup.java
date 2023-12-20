package gregtech.api.pipenet;

import gregtech.api.pipenet.block.IPipeType;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

import org.jgrapht.Graph;
import org.jgrapht.traverse.BreadthFirstIterator;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class NetGroup<PipeType extends Enum<PipeType> & IPipeType<NodeDataType>,
        NodeDataType extends INodeData<NodeDataType>> implements INBTSerializable<NBTTagCompound> {

    public final WorldPipeNetG<NodeDataType, PipeType> net;

    private final Graph<NodeG<PipeType, NodeDataType>, NetEdge> graph;

    private final Set<NodeG<PipeType, NodeDataType>> nodes;

    private final AbstractGroupData<PipeType, NodeDataType> data;

    public NetGroup(Graph<NodeG<PipeType, NodeDataType>, NetEdge> graph, WorldPipeNetG<NodeDataType, PipeType> net) {
        this.graph = graph;
        this.nodes = new ObjectOpenHashSet<>();
        this.net = net;
        this.data = net.getBlankGroupData();
    }

    public NetGroup(Graph<NodeG<PipeType, NodeDataType>, NetEdge> graph, WorldPipeNetG<NodeDataType, PipeType> net,
                    Set<NodeG<PipeType, NodeDataType>> nodes) {
        this.graph = graph;
        this.nodes = nodes;
        this.net = net;
        this.nodes.forEach(b -> b.setGroup(this));
        this.data = net.getBlankGroupData();
    }

    private void clear() {
        this.nodes.clear();
    }

    protected void addNode(NodeG<PipeType, NodeDataType> node) {
        this.nodes.add(node);
        node.setGroup(this);
    }

    protected void addNodes(Set<NodeG<PipeType, NodeDataType>> nodes) {
        this.nodes.addAll(nodes);
        nodes.forEach(a -> a.setGroup(this));
    }

    @SafeVarargs
    protected final void addNodes(NodeG<PipeType, NodeDataType>... nodes) {
        for (NodeG<PipeType, NodeDataType> node : nodes) {
            this.addNode(node);
        }
    }

    /**
     * Merges the groups of an edge if necessary.
     * 
     * @param source the source node of the edge
     * @param target the target node of the edge
     * @return True if both nodes belonged to no group, and no merge could be conducted.
     */
    public static boolean mergeEdge(NodeG<?, ?> source, NodeG<?, ?> target) {
        NetGroup<?, ?> sourceGroup = source.getGroup();
        NetGroup<?, ?> targetGroup = target.getGroup();
        if (sourceGroup == targetGroup) {
            if (sourceGroup == null) return true;
            sourceGroup.clearPathCaches();
            return false;
        }
        if (sourceGroup != null) {
            sourceGroup.mergeNode(target);
        } else {
            targetGroup.mergeNode(source);
        }
        return false;
    }

    protected void mergeNode(NodeG<?, ?> node) {
        NodeG<PipeType, NodeDataType> cast = (NodeG<PipeType, NodeDataType>) node;
        if (cast.getGroup() != null) {
            NetGroup<PipeType, NodeDataType> group = cast.getGroup();
            this.addNodes(group.getNodes());
            group.clear();
        } else addNode(cast);
        this.clearPathCaches();
    }

    /**
     * Split this group by removing a node. Automatically removes the node from the graph.
     * 
     * @param source node to remove
     * @return Whether the node existed in the graph
     */
    public boolean splitNode(NodeG<PipeType, NodeDataType> source) {
        if (graph.containsVertex(source)) {
            this.clearPathCaches();
            List<NodeG<?, ?>> targets = graph.outgoingEdgesOf(source).stream().map(a -> {
                // handling so undirected graphs don't throw an error
                if (net.isDirected()) return a.getTarget();
                if (a.getTarget().getNodePos() != source.getNodePos()) return a.getTarget();
                return a.getSource();
            }).collect(Collectors.toList());
            graph.removeVertex(source);
            while (!targets.isEmpty()) {
                // get the lastmost target; if this throws a cast exception, something is very wrong with the graph.
                NodeG<PipeType, NodeDataType> target = (NodeG<PipeType, NodeDataType>) targets
                        .remove(targets.size() - 1);

                Set<NodeG<PipeType, NodeDataType>> targetGroup = new ObjectOpenHashSet<>();
                BreadthFirstIterator<NodeG<PipeType, NodeDataType>, NetEdge> i = new BreadthFirstIterator<>(graph,
                        target);
                NodeG<PipeType, NodeDataType> temp;
                while (i.hasNext()) {
                    temp = i.next();
                    targetGroup.add(temp);
                    // if we find a target node in our search, remove it from the list
                    targets.remove(temp);
                }
                this.nodes.removeAll(targetGroup);
                // if 1 or fewer nodes are in the new group, no need to create it.
                if (targetGroup.size() > 1) {
                    // No need to do more than create it, the involved nodes are automatically updated in constructor
                    new NetGroup<>(this.graph, this.net, targetGroup);
                } else {
                    targetGroup.forEach(NodeG::clearGroup);
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Split this group by removing an edge. Automatically removes the edge from the graph.
     * 
     * @param source source of the edge
     * @param target target of the edge
     * @return Whether the edge existed in the graph
     */
    public boolean splitEdge(NodeG<PipeType, NodeDataType> source, NodeG<PipeType, NodeDataType> target) {
        if (graph.removeEdge(source, target) != null) {
            this.clearPathCaches();
            Set<NodeG<PipeType, NodeDataType>> targetGroup = new ObjectOpenHashSet<>();
            BreadthFirstIterator<NodeG<PipeType, NodeDataType>, NetEdge> i = new BreadthFirstIterator<>(graph, target);
            NodeG<PipeType, NodeDataType> temp;
            while (i.hasNext()) {
                temp = i.next();
                // if there's a another complete path to the source node from the target node, there's no need to split
                if (source == temp) return true;
                targetGroup.add(temp);
            }
            this.nodes.removeAll(targetGroup);
            // if 1 or fewer nodes are in the new group, no need to create it.
            if (targetGroup.size() > 1) {
                // No need to do more than create it, the involved nodes are automatically updated in constructor
                new NetGroup<>(this.graph, this.net, targetGroup);
            } else {
                targetGroup.forEach(NodeG::clearGroup);
            }
            return true;
        }
        return false;
    }

    /**
     * For memory considerations, returns the uncloned set. Do not modify this directly.
     */
    public Set<NodeG<PipeType, NodeDataType>> getNodes() {
        return nodes;
    }

    protected void clearPathCaches() {
        this.nodes.forEach(NodeG::clearPathCache);
    }

    public AbstractGroupData<PipeType, NodeDataType> getData() {
        return this.data;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        int i = 0;
        for (NodeG<PipeType, NodeDataType> node : this.nodes) {
            tag.setLong(String.valueOf(i), node.getLongPos());
            i++;
        }
        tag.setInteger("NodeCount", i);
        return tag;
    }

    /**
     * Use {@link NBTBuilder} instead, this does nothing.
     */
    @Override
    @Deprecated
    public void deserializeNBT(NBTTagCompound nbt) {}

    static final class NBTBuilder<PipeType extends Enum<PipeType> & IPipeType<NodeDataType>,
            NodeDataType extends INodeData<NodeDataType>> {

        private final Set<NodeG<PipeType, NodeDataType>> nodes;

        NBTBuilder(Map<Long, NodeG<PipeType, NodeDataType>> longPosMap, NBTTagCompound tag) {
            nodes = new ObjectOpenHashSet<>();
            for (int i = 0; i < tag.getInteger("NodeCount"); i++) {
                nodes.add(longPosMap.get(tag.getLong(String.valueOf(i))));
            }
        }

        void build(Graph<NodeG<PipeType, NodeDataType>, NetEdge> graph, WorldPipeNetG<NodeDataType, PipeType> net) {
            NetGroup<PipeType, NodeDataType> g = new NetGroup<>(graph, net, nodes);
        }
    }
}
