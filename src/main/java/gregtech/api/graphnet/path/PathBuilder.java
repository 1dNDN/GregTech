package gregtech.api.graphnet.path;

import gregtech.api.graphnet.NetNode;
import gregtech.api.graphnet.edge.NetEdge;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public interface PathBuilder {

    @Contract("_, _ -> this")
    PathBuilder addToEnd(@NotNull NetNode node, @NotNull NetEdge edge);

    @Contract("_, _ -> this")
    PathBuilder addToStart(@NotNull NetNode node, @NotNull NetEdge edge);

    @Contract("-> this")
    PathBuilder reverse();

    NetPath build();
}
