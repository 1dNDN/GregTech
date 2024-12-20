package gregtech.api.graphnet.traverse.iter;

import org.jgrapht.Graph;

import java.util.Set;

public enum EdgeDirection implements EdgeSelector {

    OUTGOING,
    INCOMING,
    ALL;

    @Override
    public <V, E> Set<E> selectEdges(Graph<V, E> graph, V vertex) {
        return switch (this) {
            case ALL -> graph.edgesOf(vertex);
            case INCOMING -> graph.incomingEdgesOf(vertex);
            case OUTGOING -> graph.outgoingEdgesOf(vertex);
        };
    }
}
