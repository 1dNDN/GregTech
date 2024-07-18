package gregtech.api.graphnet.graph;

import gregtech.api.graphnet.GraphNetBacker;
import gregtech.api.graphnet.IGraphNet;
import gregtech.api.graphnet.edge.SimulatorKey;
import gregtech.api.graphnet.predicate.test.IPredicateTestObject;

import org.jgrapht.graph.SimpleWeightedGraph;

import java.util.function.Function;
import java.util.function.Supplier;

public class NetUndirectedGraph extends SimpleWeightedGraph<GraphVertex, GraphEdge> implements INetGraph {

    private boolean dynamicWeights;
    private IGraphNet net;

    private IPredicateTestObject testObject;
    private SimulatorKey simulator;
    private long queryTick;

    public NetUndirectedGraph(Supplier<GraphVertex> vertexSupplier, Supplier<GraphEdge> edgeSupplier) {
        super(vertexSupplier, edgeSupplier);
    }

    @Override
    public void prepareForAlgorithmRun(IPredicateTestObject testObject, SimulatorKey simulator, long queryTick) {
        this.testObject = testObject;
        this.simulator = simulator;
        this.queryTick = queryTick;
    }

    @Override
    public boolean isDirected() {
        return false;
    }

    @Override
    public void setupInternal(GraphNetBacker backer, boolean dynamicWeights) {
        this.net = backer.getBackedNet();
        this.dynamicWeights = dynamicWeights;
    }

    @Override
    public double getEdgeWeight(GraphEdge graphEdge) {
        if (!graphEdge.getSource().wrapped.traverse(queryTick, true) ||
                !graphEdge.getTarget().wrapped.traverse(queryTick, true))
            return Double.POSITIVE_INFINITY;

        if (graphEdge.wrapped.test(testObject)) {
            if (dynamicWeights) {
                return graphEdge.wrapped.getDynamicWeight(testObject, net, simulator, queryTick, super.getEdgeWeight(graphEdge));
            } else return super.getEdgeWeight(graphEdge);
        } else return Double.POSITIVE_INFINITY;
    }

    public static Function<IGraphNet, INetGraph> standardBuilder() {
        return iGraphNet -> new NetUndirectedGraph(() -> new GraphVertex(iGraphNet.getNewNode()),
                () -> new GraphEdge(iGraphNet.getNewEdge()));
    }
}
