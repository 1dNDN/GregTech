package gregtech.api.graphnet.logic;

import gregtech.api.network.IPacket;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.IStringSerializable;
import net.minecraftforge.common.util.INBTSerializable;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Note - since the internal map representation encodes keys using {@link IStringSerializable#getName()} on logics,
 * making a logics class return two different names is a valid way to register multiple instances.
 */
public final class NetLogicData implements INBTSerializable<NBTTagList>, IPacket, INetLogicEntryListener {

    // TODO caching logic on simple logics to reduce amount of reduntant creation?
    private final Object2ObjectOpenHashMap<String, NetLogicEntry<?, ?>> logicEntrySet;

    private final Set<LogicDataListener> listeners = new ObjectOpenHashSet<>();

    public NetLogicData() {
        logicEntrySet = new Object2ObjectOpenHashMap<>(4);
    }

    private NetLogicData(Object2ObjectOpenHashMap<String, NetLogicEntry<?, ?>> logicEntrySet) {
        this.logicEntrySet = logicEntrySet;
    }

    /**
     * If the {@link NetLogicEntry#union(NetLogicEntry)} operation is not supported for this entry,
     * nothing happens if an entry is already present.
     */
    public NetLogicData mergeLogicEntry(NetLogicEntry<?, ?> entry) {
        NetLogicEntry<?, ?> current = logicEntrySet.get(entry.getName());
        if (current == null) return setLogicEntry(entry);

        if (entry.getClass().isInstance(current)) {
            entry = current.union(entry);
            if (entry == null) return this;
        }
        return setLogicEntry(entry);
    }

    public NetLogicData setLogicEntry(NetLogicEntry<?, ?> entry) {
        entry.registerToNetLogicData(this);
        logicEntrySet.put(entry.getName(), entry);
        this.markLogicEntryAsUpdated(entry, true);
        return this;
    }

    /**
     * Returns all registered logic entries; this should be treated in read-only manner.
     */
    public ObjectCollection<NetLogicEntry<?, ?>> getEntries() {
        return logicEntrySet.values();
    }

    public void clearData() {
        logicEntrySet.clear();
        logicEntrySet.trim(4);
    }

    public NetLogicData removeLogicEntry(@NotNull NetLogicEntry<?, ?> key) {
        return removeLogicEntry(key.getName());
    }

    public NetLogicData removeLogicEntry(@NotNull String key) {
        NetLogicEntry<?, ?> entry = logicEntrySet.remove(key);
        if (entry != null) {
            entry.deregisterFromNetLogicData(this);
            this.listeners.forEach(l -> l.markChanged(entry, true, true));
            logicEntrySet.trim();
        }
        return this;
    }

    @Override
    public void markLogicEntryAsUpdated(NetLogicEntry<?, ?> entry, boolean fullChange) {
        this.listeners.forEach(l -> l.markChanged(entry, false, fullChange));
    }

    @Nullable
    public NetLogicEntry<?, ?> getLogicEntryNullable(@NotNull String key) {
        return logicEntrySet.get(key);
    }

    @Nullable
    public <T extends NetLogicEntry<?, ?>> T getLogicEntryNullable(@NotNull T key) {
        try {
            return (T) logicEntrySet.get(key.getName());
        } catch (ClassCastException ignored) {
            return null;
        }
    }

    @NotNull
    public <T extends NetLogicEntry<T, ?>> T getLogicEntryDefaultable(@NotNull T key) {
        try {
            T returnable = (T) logicEntrySet.get(key.getName());
            return returnable == null ? key : returnable;
        } catch (ClassCastException ignored) {
            return key;
        }
    }

    @Contract("null, null -> null; !null, _ -> new; _, !null -> new")
    public static @Nullable NetLogicData unionNullable(@Nullable NetLogicData sourceData, @Nullable NetLogicData targetData) {
        if (sourceData == null && targetData == null) return null;
        return union(sourceData == null ? targetData : sourceData, sourceData == null ? null : targetData);
    }

    @Contract("_, _ -> new")
    public static @NotNull NetLogicData union(@NotNull NetLogicData sourceData, @Nullable NetLogicData targetData) {
        Object2ObjectOpenHashMap<String, NetLogicEntry<?, ?>> newLogic = new Object2ObjectOpenHashMap<>(
                sourceData.logicEntrySet);
        if (targetData != null) {
            for (String key : newLogic.keySet()) {
                newLogic.computeIfPresent(key, (k, v) -> v.union(targetData.logicEntrySet.get(k)));
            }
            targetData.logicEntrySet.forEach((key, value) -> newLogic.computeIfAbsent(key, k -> value.union(null)));
        }
        return new NetLogicData(newLogic);
    }

    @Contract("_, _ -> new")
    public static @NotNull NetLogicData union(@NotNull NetLogicData first, @NotNull NetLogicData... others) {
        Object2ObjectOpenHashMap<String, NetLogicEntry<?, ?>> newLogic = new Object2ObjectOpenHashMap<>(
                first.logicEntrySet);
        for (NetLogicData other : others) {
            for (String key : newLogic.keySet()) {
                newLogic.computeIfPresent(key, (k, v) -> v.union(other.logicEntrySet.get(k)));
            }
            other.logicEntrySet.forEach((key, value) -> newLogic.computeIfAbsent(key, k -> value.union(null)));
        }
        return new NetLogicData(newLogic);
    }

    public void addListener(LogicDataListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public NBTTagList serializeNBT() {
        NBTTagList list = new NBTTagList();
        for (NetLogicEntry<?, ?> entry : getEntries()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag("Tag", entry.serializeNBT());
            tag.setString("Name", entry.getName());
            list.appendTag(tag);
        }
        return list;
    }

    @Override
    public void deserializeNBT(NBTTagList nbt) {
        for (int i = 0; i < nbt.tagCount(); i++) {
            NBTTagCompound tag = nbt.getCompoundTagAt(i);
            String key = tag.getString("Name");
            NetLogicEntry<?, ?> entry = this.logicEntrySet.get(key);
            if (entry == null) entry = NetLogicRegistry.getSupplierNotNull(key).get();
            if (entry == null) continue;
            entry.deserializeNBTNaive(tag.getTag("Tag"));
        }
    }

    @Override
    public void encode(PacketBuffer buf) {
        buf.writeVarInt(getEntries().size());
        for (NetLogicEntry<?, ?> entry : getEntries()) {
            buf.writeString(entry.getName());
            entry.encode(buf, true);
        }
    }

    @Override
    public void decode(PacketBuffer buf) {
        this.logicEntrySet.clear();
        int entryCount = buf.readVarInt();
        for (int i = 0; i < entryCount; i++) {
            String name = buf.readString(255);
            NetLogicEntry<?, ?> existing = NetLogicRegistry.getSupplierErroring(name).get();
            if (existing == null)
                throw new RuntimeException("Could not find a matching supplier for an encoded NetLogicEntry. " +
                        "This suggests that the server and client have different GT versions or modifications.");
            existing.registerToNetLogicData(this);
            existing.decode(buf);
            this.logicEntrySet.put(name, existing);
        }
        this.logicEntrySet.trim();
    }

    public LogicDataListener createListener(ILogicDataListener listener) {
        return new LogicDataListener(listener);
    }

    public final class LogicDataListener {

        private final ILogicDataListener listener;

        private LogicDataListener(ILogicDataListener listener) {
            this.listener = listener;
        }

        private void markChanged(NetLogicEntry<?, ?> updatedEntry, boolean removed, boolean fullChange) {
            this.listener.markChanged(updatedEntry, removed, fullChange);
        }

        // TODO would a weak set be better?
        public void invalidate() {
            listeners.remove(this);
        }
    }

    @FunctionalInterface
    public interface ILogicDataListener {

        void markChanged(NetLogicEntry<?, ?> updatedEntry, boolean removed, boolean fullChange);
    }
}
