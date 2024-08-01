package gregtech.api.graphnet;

import gregtech.api.graphnet.logic.INetLogicEntry;
import gregtech.api.graphnet.logic.INetLogicEntryListener;
import gregtech.api.graphnet.logic.NetLogicData;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * MultiNodeHelpers are utility objects used to preserve sync between multiple nodes owned by different graphs. They do
 * this by <br>
 * A) keeping a record of traversals to allow for blocking traversal when another net has been traversed
 * recently and <br>
 * B) making sure that logic entries requiring it are the same object across all synced nodes. <br>
 * <br>
 * MultiNodeHelpers have no standard implementation and must be handled by a net and its nodes; see
 * {@link gregtech.api.graphnet.pipenet.WorldPipeNet} and {@link gregtech.api.graphnet.pipenet.WorldPipeNetNode}
 * for an example of this in action.
 */
public class MultiNodeHelper implements INetLogicEntryListener {

    protected final Object2ObjectOpenHashMap<IGraphNet, LogicDataHandler> handledDatas = new Object2ObjectOpenHashMap<>();

    protected final Object2ObjectOpenHashMap<IGraphNet, @NotNull Long> recentTransferNets = new Object2ObjectOpenHashMap<>();
    protected final int transferTimeout;

    protected final NetLogicData mergedData = new NetLogicData();

    public MultiNodeHelper(int transferTimeout) {
        this.transferTimeout = transferTimeout;
    }

    public boolean traverse(IGraphNet net, long queryTick, boolean simulate) {
        var iter = recentTransferNets.object2ObjectEntrySet().fastIterator();
        boolean allowed = true;
        while (iter.hasNext()) {
            var next = iter.next();
            if (net.clashesWith(next.getKey())) {
                if (next.getValue() <= queryTick) {
                    iter.remove();
                } else {
                    allowed = false;
                    break;
                }
            }
        }
        if (allowed && !simulate) {
            recentTransferNets.put(net, queryTick + transferTimeout);
        }
        return allowed;
    }

    @Override
    public void markLogicEntryAsUpdated(INetLogicEntry<?, ?> entry, boolean fullChange) {
        // TODO have a helper or something on clientside to avoid redundant packets
        handledDatas.forEach((k, v) -> v.data.markLogicEntryAsUpdated(entry, fullChange));
    }

    public void addNode(@NotNull NetNode node) {
        List<INetLogicEntry<?, ?>> toSet = new ObjectArrayList<>();
        for (INetLogicEntry<?, ?> entry : node.getData().getEntries()) {
            if (entry.mergedToMultiNodeHelper()) {
                INetLogicEntry<?, ?> existing = mergedData.getLogicEntryNullable(entry);
                if (existing != null) {
                    existing.merge(node, entry);
                    // don't put it into the data yet because we're currently iterating through the data's entries.
                    toSet.add(existing);
                } else {
                    addNewLogicEntry(entry);
                }
            }
        }
        handledDatas.put(node.getNet(), new LogicDataHandler(node));
        for (INetLogicEntry<?, ?> entry : toSet) {
            node.getData().setLogicEntry(entry);
        }
    }

    public void removeNode(NetNode node) {
        LogicDataHandler removed = handledDatas.remove(node.getNet());
        if (removed != null) {
            removed.invalidate();
            for (INetLogicEntry<?, ?> entry : this.mergedData.getEntries()) {
                node.getData().removeLogicEntry(entry);
                entry.unmerge(node);
            }
        }
    }

    private void addNewLogicEntry(@NotNull INetLogicEntry<?, ?> entry) {
        entry.registerToMultiNodeHelper(this);
        mergedData.setLogicEntry(entry);
        handledDatas.values().forEach(h -> h.data.setLogicEntry(entry));
    }

    protected class LogicDataHandler implements NetLogicData.ILogicDataListener {

        public final WeakReference<NetNode> nodeRef;
        public final @NotNull NetLogicData.LogicDataListener listener;
        public final @NotNull NetLogicData data;

        public LogicDataHandler(@NotNull NetNode node) {
            this.data = node.getData();
            this.listener = data.createListener(this);
            this.nodeRef = new WeakReference<>(node);
        }

        public void invalidate() {
            this.listener.invalidate();
        }

        @Override
        public void markChanged(INetLogicEntry<?, ?> updatedEntry, boolean removed, boolean fullChange) {
            if (!fullChange || !updatedEntry.mergedToMultiNodeHelper()) return;
            NetNode node = nodeRef.get();
            if (node == null) return;
            INetLogicEntry<?, ?> existing = mergedData.getLogicEntryNullable(updatedEntry);
            if (removed) {
                if (existing != null) mergedData.removeLogicEntry(existing);
            } else {
                if (existing != null) {
                    if (existing != updatedEntry) {
                        existing.merge(node, updatedEntry);
                        data.setLogicEntry(existing);
                    }
                } else {
                    addNewLogicEntry(updatedEntry);
                }
            }
        }
    }
}
