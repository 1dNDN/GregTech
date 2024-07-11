package gregtech.api.graphnet.pipenet;

import gregtech.api.graphnet.alg.NetPathMapper;
import gregtech.api.graphnet.edge.AbstractNetFlowEdge;
import gregtech.api.graphnet.graph.GraphEdge;
import gregtech.api.graphnet.graph.GraphVertex;
import gregtech.api.graphnet.logic.WeightFactorLogic;
import gregtech.api.graphnet.path.AbstractNetPath;

import org.jgrapht.GraphPath;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FlowWorldPipeNetPath extends AbstractNetPath<WorldPipeNetNode, AbstractNetFlowEdge> {

    public static final NetPathMapper<FlowWorldPipeNetPath> MAPPER =
            new NetPathMapper<>(FlowWorldPipeNetPath::new, FlowWorldPipeNetPath::new, FlowWorldPipeNetPath::new);

    public FlowWorldPipeNetPath(GraphVertex vertex) {
        this(Collections.singletonList(vertex), Collections.emptyList(),
                vertex.wrapped.getData().getLogicEntryDefaultable(WeightFactorLogic.INSTANCE).getValue());
    }

    public FlowWorldPipeNetPath(List<GraphVertex> vertices, List<GraphEdge> edges, double weight) {
        super(vertices.stream().map(v -> (WorldPipeNetNode) v.wrapped).collect(Collectors.toList()),
                edges.stream().map(e -> (AbstractNetFlowEdge) e.wrapped).collect(Collectors.toList()), weight);
    }

    public FlowWorldPipeNetPath(GraphPath<GraphVertex, GraphEdge> path) {
        this(path.getVertexList(), path.getEdgeList(), path.getWeight());
    }
}
