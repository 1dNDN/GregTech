package gregtech.api.graphnet.pipenet.physical.tile;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.cover.Cover;
import gregtech.api.graphnet.logic.NetLogicData;
import gregtech.api.graphnet.logic.NetLogicEntry;
import gregtech.api.graphnet.logic.NetLogicRegistry;
import gregtech.api.graphnet.pipenet.WorldPipeNet;
import gregtech.api.graphnet.pipenet.WorldPipeNetNode;
import gregtech.api.graphnet.pipenet.logic.TemperatureLogic;
import gregtech.api.graphnet.pipenet.physical.IInsulatable;
import gregtech.api.graphnet.pipenet.physical.IPipeCapabilityObject;
import gregtech.api.graphnet.pipenet.physical.IPipeStructure;
import gregtech.api.graphnet.pipenet.physical.block.PipeBlock;
import gregtech.api.metatileentity.NeighborCacheTileEntityBase;
import gregtech.api.unification.material.Material;
import gregtech.client.particle.GTOverheatParticle;
import gregtech.client.renderer.pipe.AbstractPipeModel;
import gregtech.client.renderer.pipe.cover.CoverRendererBuilder;
import gregtech.client.renderer.pipe.cover.CoverRendererPackage;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static gregtech.api.capability.GregtechDataCodes.*;

public class PipeTileEntity extends NeighborCacheTileEntityBase implements ITickable, IWorldPipeNetTile {

    public static final int DEFAULT_COLOR = 0xFFFFFFFF;

    private final Int2ObjectOpenHashMap<NetLogicData> netLogicDatas = new Int2ObjectOpenHashMap<>();
    private final ObjectOpenHashSet<NetLogicData.LogicDataListener> listeners = new ObjectOpenHashSet<>();

    // information that is only required for determining graph topology should be stored on the tile entity level,
    // while information interacted with during graph traversal should be stored on the NetLogicData level.

    private byte connectionMask;
    private byte renderMask;
    private byte blockedMask;
    private int paintingColor = -1;

    private @Nullable Material frameMaterial;

    private final Set<ITickable> tickers = new ObjectOpenHashSet<>();

    protected final PipeCoverHolder covers = new PipeCoverHolder(this);
    private final Object2ObjectOpenHashMap<Capability<?>, IPipeCapabilityObject> capabilities = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenCustomHashMap<WorldPipeNetNode, PipeCapabilityWrapper> netCapabilities = WorldPipeNet
            .getSensitiveHashMap();

    @Nullable
    private TemperatureLogic temperatureLogic;
    @SideOnly(Side.CLIENT)
    @Nullable
    private GTOverheatParticle overheatParticle;

    private final int offset = (int) (Math.random() * 20);

    private long nextDamageTime = 0;
    private long nextSoundTime = 0;

    @Nullable
    public PipeTileEntity getPipeNeighbor(EnumFacing facing, boolean allowChunkloading) {
        TileEntity tile = allowChunkloading ? getNeighbor(facing) : getNeighborNoChunkloading(facing);
        if (tile instanceof PipeTileEntity pipe) return pipe;
        else return null;
    }

    public void getDrops(@NotNull NonNullList<ItemStack> drops, @NotNull IBlockState state) {
        drops.add(getMainDrop(state));
        if (getFrameMaterial() != null)
            drops.add(MetaBlocks.FRAMES.get(getFrameMaterial()).getItem(getFrameMaterial()));
    }

    @Override
    public void validate() {
        super.validate();
        scheduleRenderUpdate();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        // TODO I hate this so much can someone please make it so that covers go through getDrops()?
        getCoverHolder().dropAllCovers();
    }

    public ItemStack getMainDrop(@NotNull IBlockState state) {
        return new ItemStack(state.getBlock(), 1);
    }

    public ItemStack getDrop() {
        return new ItemStack(getBlockType(), 1, getBlockType().damageDropped(getBlockState()));
    }

    public long getOffsetTimer() {
        return FMLCommonHandler.instance().getMinecraftServerInstance().getTickCounter() + offset;
    }

    public void placedBy(ItemStack stack, EntityPlayer player) {}

    public IPipeStructure getStructure() {
        return getBlockType().getStructure();
    }

    // mask //

    public boolean canConnectTo(EnumFacing facing) {
        return this.getStructure().canConnectTo(facing, connectionMask);
    }

    public void setConnected(EnumFacing facing, boolean renderClosed) {
        this.connectionMask |= 1 << facing.ordinal();
        updateActiveStatus(facing, false);
        if (renderClosed) {
            this.renderMask |= 1 << facing.ordinal();
        } else {
            this.renderMask &= ~(1 << facing.ordinal());
        }
        syncConnected();
    }

    public void setDisconnected(EnumFacing facing) {
        this.connectionMask &= ~(1 << facing.ordinal());
        this.renderMask &= ~(1 << facing.ordinal());
        updateActiveStatus(facing, false);
        syncConnected();
    }

    private void syncConnected() {
        if (!getWorld().isRemote) {
            writeCustomData(UPDATE_CONNECTIONS, buffer -> {
                buffer.writeByte(connectionMask);
                buffer.writeByte(renderMask);
            });
        } else scheduleRenderUpdate();
        markDirty();
    }

    public boolean isConnected(EnumFacing facing) {
        return (this.connectionMask & 1 << facing.ordinal()) > 0;
    }

    public boolean isConnectedCoverAdjusted(EnumFacing facing) {
        Cover cover;
        return ((this.connectionMask & 1 << facing.ordinal()) > 0) ||
                (cover = getCoverHolder().getCoverAtSide(facing)) != null && cover.forcePipeRenderConnection();
    }

    public boolean renderClosed(EnumFacing facing) {
        return (this.renderMask & 1 << facing.ordinal()) > 0;
    }

    public byte getConnectionMask() {
        return connectionMask;
    }

    public void setBlocked(EnumFacing facing) {
        this.blockedMask |= 1 << facing.ordinal();
        syncBlocked();
    }

    public void setUnblocked(EnumFacing facing) {
        this.blockedMask &= ~(1 << facing.ordinal());
        syncBlocked();
    }

    private void syncBlocked() {
        if (!getWorld().isRemote) {
            writeCustomData(UPDATE_BLOCKED_CONNECTIONS, buffer -> buffer.writeByte(blockedMask));
        } else scheduleRenderUpdate();
        markDirty();
    }

    public boolean isBlocked(EnumFacing facing) {
        return (this.blockedMask & 1 << facing.ordinal()) > 0;
    }

    public byte getBlockedMask() {
        return blockedMask;
    }

    // paint //

    public int getPaintingColor() {
        return isPainted() ? paintingColor : getDefaultPaintingColor();
    }

    public void setPaintingColor(int paintingColor, boolean alphaSensitive) {
        if (!alphaSensitive) {
            paintingColor |= 0xFF000000;
        }
        this.paintingColor = paintingColor;
        if (!getWorld().isRemote) {
            writeCustomData(UPDATE_PAINT, buffer -> buffer.writeInt(this.paintingColor));
            markDirty();
        } else scheduleRenderUpdate();
    }

    public boolean isPainted() {
        return this.paintingColor != -1;
    }

    public int getDefaultPaintingColor() {
        return DEFAULT_COLOR;
    }

    // frame //

    public void setFrameMaterial(@Nullable Material frameMaterial) {
        this.frameMaterial = frameMaterial;
        syncFrameMaterial();
    }

    private void syncFrameMaterial() {
        if (!getWorld().isRemote) {
            writeCustomData(UPDATE_FRAME_MATERIAL, buffer -> {
                if (frameMaterial != null) buffer.writeString(this.frameMaterial.getRegistryName());
                else buffer.writeString("");
            });
        } else scheduleRenderUpdate();
        markDirty();
    }

    public @Nullable Material getFrameMaterial() {
        return frameMaterial;
    }

    // ticking //

    public void addTicker(ITickable ticker) {
        this.tickers.add(ticker);
        // noinspection ConstantValue
        if (getWorld() != null) getWorld().tickableTileEntities.add(this);
    }

    @Override
    public void update() {
        this.tickers.forEach(ITickable::update);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        initialize();
        // since we're an instance of ITickable, we're automatically added to the tickable list just before this exact
        // moment.
        // it would theoretically be a micro optimization to just pop the last tile from the tickable list, but that's
        // not guaranteed.
        if (!this.isTicking()) this.getWorld().tickableTileEntities.remove(this);
    }

    public void removeTicker(ITickable ticker) {
        this.tickers.remove(ticker);
        // noinspection ConstantValue
        if (!this.isTicking() && getWorld() != null) getWorld().tickableTileEntities.remove(this);
    }

    public boolean isTicking() {
        return !tickers.isEmpty();
    }

    // cover //

    @NotNull
    public PipeCoverHolder getCoverHolder() {
        return covers;
    }

    // activeness //

    @Override
    public void onNeighborChanged(@NotNull EnumFacing facing) {
        super.onNeighborChanged(facing);
        updateActiveStatus(facing, false);
    }

    /**
     * Returns a map of facings to tile entities that should have at least one of the required capabilities.
     * 
     * @param node the node for this tile entity. Used to identify the capabilities to match.
     * @return a map of facings to tile entities.
     */
    public @NotNull EnumMap<EnumFacing, TileEntity> getTargetsWithCapabilities(WorldPipeNetNode node) {
        PipeCapabilityWrapper wrapper = netCapabilities.get(node);
        EnumMap<EnumFacing, TileEntity> caps = new EnumMap<>(EnumFacing.class);
        if (wrapper == null) return caps;

        for (EnumFacing facing : EnumFacing.VALUES) {
            if (wrapper.isActive(facing)) {
                TileEntity tile = getNeighbor(facing);
                if (tile == null) updateActiveStatus(facing, false);
                else caps.put(facing, tile);
            }
        }
        return caps;
    }

    @Override
    public @Nullable TileEntity getTargetWithCapabilities(WorldPipeNetNode node, EnumFacing facing) {
        PipeCapabilityWrapper wrapper = netCapabilities.get(node);
        if (wrapper == null || !wrapper.isActive(facing)) return null;
        else return getNeighbor(facing);
    }

    @Override
    public PipeCapabilityWrapper getWrapperForNode(WorldPipeNetNode node) {
        return netCapabilities.get(node);
    }

    /**
     * Updates the pipe's active status based on the tile entity connected to the side.
     * 
     * @param facing            the side to check. Can be null, in which case all sides will be checked.
     * @param canOpenConnection whether the pipe is allowed to open a new connection if it finds a tile it can connect
     *                          to.
     */
    public void updateActiveStatus(@Nullable EnumFacing facing, boolean canOpenConnection) {
        if (facing == null) {
            for (EnumFacing side : EnumFacing.VALUES) {
                updateActiveStatus(side, canOpenConnection);
            }
            return;
        }
        if (!this.isConnectedCoverAdjusted(facing) && !(canOpenConnection && canConnectTo(facing))) {
            setAllIdle(facing);
            return;
        }

        TileEntity tile = getNeighbor(facing);
        if (tile == null || tile instanceof PipeTileEntity) {
            setAllIdle(facing);
            return;
        }

        boolean oneActive = false;
        for (var netCapability : netCapabilities.entrySet()) {
            for (Capability<?> cap : netCapability.getValue().capabilities) {
                if (tile.hasCapability(cap, facing.getOpposite())) {
                    oneActive = true;
                    netCapability.getValue().setActive(facing);
                    break;
                }
            }
        }
        if (canOpenConnection && oneActive) this.setConnected(facing, false);
    }

    private void setAllIdle(EnumFacing facing) {
        for (var netCapability : netCapabilities.entrySet()) {
            netCapability.getValue().setIdle(facing);
        }
    }

    // capability //

    private void addCapabilities(IPipeCapabilityObject[] capabilities) {
        for (IPipeCapabilityObject capabilityObject : capabilities) {
            capabilityObject.setTile(this);
            for (Capability<?> capability : capabilityObject.getCapabilities()) {
                this.capabilities.put(capability, capabilityObject);
            }
        }
    }

    public <T> T getCapabilityCoverQuery(@NotNull Capability<T> capability, @Nullable EnumFacing facing) {
        // covers have access to the capability objects no matter the connection status
        IPipeCapabilityObject object = capabilities.get(capability);
        return object == null ? null : object.getCapabilityForSide(capability, facing);
    }

    @Override
    public boolean hasCapability(@NotNull Capability<?> capability, EnumFacing facing) {
        return getCapability(capability, facing) != null;
    }

    @Override
    public <T> T getCapability(@NotNull Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == GregtechTileCapabilities.CAPABILITY_COVER_HOLDER) {
            return GregtechTileCapabilities.CAPABILITY_COVER_HOLDER.cast(getCoverHolder());
        }
        T pipeCapability;
        IPipeCapabilityObject object = capabilities.get(capability);
        if (object == null || (pipeCapability = object.getCapabilityForSide(capability, facing)) == null)
            pipeCapability = super.getCapability(capability, facing);

        Cover cover = facing == null ? null : getCoverHolder().getCoverAtSide(facing);
        if (cover == null) {
            if (facing == null || isConnected(facing)) {
                return pipeCapability;
            }
            return null;
        }

        T coverCapability = cover.getCapability(capability, pipeCapability);
        if (coverCapability == pipeCapability) {
            if (isConnectedCoverAdjusted(facing)) {
                return pipeCapability;
            }
            return null;
        }
        return coverCapability;
    }

    // data sync management //

    public NetLogicData getNetLogicData(int networkID) {
        return netLogicDatas.get(networkID);
    }

    @Override
    public @NotNull PipeBlock getBlockType() {
        return (PipeBlock) super.getBlockType();
    }

    @Override
    public void setWorld(@NotNull World worldIn) {
        if (worldIn == this.getWorld()) return;
        super.setWorld(worldIn);
    }

    protected void initialize() {
        if (!getWorld().isRemote) {
            this.netLogicDatas.clear();
            this.capabilities.clear();
            this.netCapabilities.clear();
            this.listeners.forEach(NetLogicData.LogicDataListener::invalidate);
            this.listeners.clear();
            boolean firstNode = true;
            for (WorldPipeNetNode node : PipeBlock.getNodesForTile(this)) {
                this.addCapabilities(node.getNet().getNewCapabilityObjects(node));
                this.netCapabilities.put(node, new PipeCapabilityWrapper(this, node));
                int networkID = node.getNet().getNetworkID();
                netLogicDatas.put(networkID, node.getData());
                var listener = node.getData().createListener(
                        (e, r, f) -> writeCustomData(UPDATE_PIPE_LOGIC, buf -> {
                            buf.writeVarInt(networkID);
                            buf.writeString(e.getName());
                            buf.writeBoolean(r);
                            buf.writeBoolean(f);
                            if (!r) {
                                e.encode(buf);
                            }
                        }));
                this.listeners.add(listener);
                node.getData().addListener(listener);
                if (firstNode) {
                    firstNode = false;
                    this.temperatureLogic = node.getData().getLogicEntryNullable(TemperatureLogic.INSTANCE);
                }
                // TODO
                // this and updateActiveStatus() theoretically only need to be called when loading old world data;
                // is there a way to detect that and skip if so?
                node.getNet().updatePredication(node, this);
            }
            this.netLogicDatas.trim();
            this.listeners.trim();
            this.capabilities.trim();
            this.netCapabilities.trim();
            updateActiveStatus(null, false);
        }
    }

    @Override
    public @NotNull NBTTagCompound writeToNBT(@NotNull NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setByte("ConnectionMask", connectionMask);
        compound.setByte("RenderMask", renderMask);
        compound.setByte("BlockedMask", blockedMask);
        compound.setInteger("Paint", paintingColor);
        if (frameMaterial != null) compound.setString("Frame", frameMaterial.getRegistryName());
        compound.setTag("Covers", getCoverHolder().serializeNBT());
        return compound;
    }

    @Override
    public void readFromNBT(@NotNull NBTTagCompound compound) {
        super.readFromNBT(compound);
        connectionMask = compound.getByte("ConnectionMask");
        renderMask = compound.getByte("RenderMask");
        blockedMask = compound.getByte("BlockedMask");
        paintingColor = compound.getInteger("Paint");
        if (compound.hasKey("Frame"))
            this.frameMaterial = GregTechAPI.materialManager.getMaterial(compound.getString("Frame"));
        else this.frameMaterial = null;
        this.getCoverHolder().deserializeNBT(compound.getCompoundTag("Covers"));
    }

    protected void encodeMaterialToBuffer(@NotNull Material material, @NotNull PacketBuffer buf) {
        buf.writeVarInt(material.getRegistry().getNetworkId());
        buf.writeInt(material.getId());
    }

    protected Material decodeMaterialFromBuffer(@NotNull PacketBuffer buf) {
        return GregTechAPI.materialManager.getRegistry(buf.readVarInt()).getObjectById(buf.readInt());
    }

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        buf.writeByte(connectionMask);
        buf.writeByte(renderMask);
        buf.writeByte(blockedMask);
        buf.writeInt(paintingColor);
        buf.writeBoolean(frameMaterial != null);
        if (frameMaterial != null) encodeMaterialToBuffer(frameMaterial, buf);
        buf.writeVarInt(netLogicDatas.size());
        for (var entry : netLogicDatas.entrySet()) {
            buf.writeVarInt(entry.getKey());
            entry.getValue().encode(buf);
        }
        this.getCoverHolder().writeInitialSyncData(buf);
    }

    @Override
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
        if (world.isRemote) {
            connectionMask = buf.readByte();
            renderMask = buf.readByte();
            blockedMask = buf.readByte();
            paintingColor = buf.readInt();
            if (buf.readBoolean()) frameMaterial = decodeMaterialFromBuffer(buf);
            else frameMaterial = null;
            netLogicDatas.clear();
            int count = buf.readVarInt();
            for (int i = 0; i < count; i++) {
                int networkID = buf.readVarInt();
                NetLogicData data = new NetLogicData();
                data.decode(buf);
                netLogicDatas.put(networkID, data);
            }
            this.getCoverHolder().readInitialSyncData(buf);
        }
        scheduleRenderUpdate();
    }

    @Override
    public void receiveCustomData(int discriminator, @NotNull PacketBuffer buf) {
        if (discriminator == UPDATE_PIPE_LOGIC) {
            // extra check just to make sure we don't affect actual net data with our writes
            if (world.isRemote) {
                int networkID = buf.readVarInt();
                String identifier = buf.readString(255);
                boolean removed = buf.readBoolean();
                boolean fullChange = buf.readBoolean();
                if (removed) {
                    this.netLogicDatas.computeIfPresent(networkID, (k, v) -> v.removeLogicEntry(identifier));
                } else {
                    if (fullChange) {
                        NetLogicEntry<?, ?> logic = NetLogicRegistry.getSupplierErroring(identifier).get();
                        logic.decode(buf, fullChange);
                        this.netLogicDatas.compute(networkID, (k, v) -> {
                            if (v == null) v = new NetLogicData();
                            v.setLogicEntry(logic);
                            return v;
                        });
                    } else {
                        NetLogicData data = this.netLogicDatas.get(networkID);
                        if (data != null) {
                            NetLogicEntry<?, ?> entry = data.getLogicEntryNullable(identifier);
                            if (entry != null) entry.decode(buf);
                        } else return;
                    }
                    if (identifier.equals(TemperatureLogic.INSTANCE.getName())) {
                        TemperatureLogic tempLogic = this.netLogicDatas.get(networkID)
                                .getLogicEntryNullable(TemperatureLogic.INSTANCE);
                        if (tempLogic != null) updateTemperatureLogic(tempLogic);
                    }
                }
            }
        } else if (discriminator == UPDATE_CONNECTIONS) {
            this.connectionMask = buf.readByte();
            this.renderMask = buf.readByte();
            scheduleRenderUpdate();
        } else if (discriminator == UPDATE_BLOCKED_CONNECTIONS) {
            this.blockedMask = buf.readByte();
            scheduleRenderUpdate();
        } else if (discriminator == UPDATE_FRAME_MATERIAL) {
            String name = buf.readString(255);
            if (name.equals("")) this.frameMaterial = null;
            else this.frameMaterial = GregTechAPI.materialManager.getMaterial(name);
            scheduleRenderUpdate();
        } else if (discriminator == UPDATE_PAINT) {
            this.paintingColor = buf.readInt();
            scheduleRenderUpdate();
        } else {
            this.getCoverHolder().readCustomData(discriminator, buf);
        }
    }

    // particle //

    public void updateTemperatureLogic(@NotNull TemperatureLogic logic) {
        if (overheatParticle == null || !overheatParticle.isAlive()) {
            long tick = FMLCommonHandler.instance().getMinecraftServerInstance().getTickCounter();
            int temp = logic.getTemperature(tick);
            if (temp > GTOverheatParticle.TEMPERATURE_CUTOFF) {
                IPipeStructure structure = this.getStructure();
                overheatParticle = new GTOverheatParticle(this, logic, structure.getPipeBoxes(this),
                        structure instanceof IInsulatable i && i.isInsulated());
            }
        } else {
            overheatParticle.setTemperatureLogic(logic);
        }
    }

    public @Nullable TemperatureLogic getTemperatureLogic() {
        return temperatureLogic;
    }

    @SideOnly(Side.CLIENT)
    public void killOverheatParticle() {
        if (overheatParticle != null) {
            overheatParticle.setExpired();
            overheatParticle = null;
        }
    }

    @SideOnly(Side.CLIENT)
    public boolean isOverheatParticleAlive() {
        return overheatParticle != null && overheatParticle.isAlive();
    }

    @Override
    public void spawnParticles(EnumFacing direction, EnumParticleTypes particleType, int particleCount) {
        if (getWorld() instanceof WorldServer server) {
            server.spawnParticle(particleType,
                    getPos().getX() + 0.5,
                    getPos().getY() + 0.5,
                    getPos().getZ() + 0.5,
                    particleCount,
                    direction.getXOffset() * 0.2 + GTValues.RNG.nextDouble() * 0.1,
                    direction.getYOffset() * 0.2 + GTValues.RNG.nextDouble() * 0.1,
                    direction.getZOffset() * 0.2 + GTValues.RNG.nextDouble() * 0.1,
                    0.1);
        }
    }

    // misc overrides //

    @Override
    public World world() {
        return getWorld();
    }

    @Override
    public BlockPos pos() {
        return getPos();
    }

    @Override
    public void notifyBlockUpdate() {
        getWorld().notifyNeighborsOfStateChange(getPos(), getBlockType(), true);
    }

    @SuppressWarnings("ConstantConditions") // yes this CAN actually be null
    @Override
    public void markDirty() {
        if (getWorld() != null && getPos() != null) {
            getWorld().markChunkDirty(getPos(), this);
        }
    }

    @Override
    public void markAsDirty() {
        markDirty();
        // this most notably gets called when the covers of a pipe get updated, aka the edge predicates need syncing.
        for (var node : this.netCapabilities.keySet()) {
            node.getNet().updatePredication(node, this);
        }
    }

    public static @Nullable PipeTileEntity getTileNoLoading(BlockPos pos, int dimension) {
        World world = DimensionManager.getWorld(dimension);
        if (world == null || !world.isBlockLoaded(pos)) return null;

        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof PipeTileEntity pipe) return pipe;
        else return null;
    }

    /**
     * Note - the block corresponding to this tile entity must register any new unlisted properties to the default
     * state.
     */
    @SideOnly(Side.CLIENT)
    @MustBeInvokedByOverriders
    public IExtendedBlockState getRenderInformation(IExtendedBlockState state) {
        byte frameMask = 0;
        byte connectionMask = this.connectionMask;
        for (EnumFacing facing : EnumFacing.VALUES) {
            Cover cover = getCoverHolder().getCoverAtSide(facing);
            if (cover != null) {
                frameMask |= 1 << facing.ordinal();
                if (cover.forcePipeRenderConnection()) connectionMask |= 1 << facing.ordinal();
            }
        }
        frameMask = (byte) ~frameMask;
        return state.withProperty(AbstractPipeModel.THICKNESS_PROPERTY, this.getStructure().getRenderThickness())
                .withProperty(AbstractPipeModel.CONNECTION_MASK_PROPERTY, connectionMask)
                .withProperty(AbstractPipeModel.CLOSED_MASK_PROPERTY, renderMask)
                .withProperty(AbstractPipeModel.BLOCKED_MASK_PROPERTY, blockedMask)
                .withProperty(AbstractPipeModel.COLOR_PROPERTY, getPaintingColor())
                .withProperty(AbstractPipeModel.FRAME_MATERIAL_PROPERTY, frameMaterial)
                .withProperty(AbstractPipeModel.FRAME_MASK_PROPERTY, frameMask)
                .withProperty(CoverRendererPackage.PROPERTY, getCoverHolder().createPackage());
    }

    public void getCoverBoxes(Consumer<AxisAlignedBB> consumer) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (getCoverHolder().hasCover(facing)) {
                consumer.accept(CoverRendererBuilder.PLATE_AABBS.get(facing));
            }
        }
    }

    @Override
    public void dealAreaDamage(int size, Consumer<EntityLivingBase> damageFunction) {
        long timer = getOffsetTimer();
        if (timer >= this.nextDamageTime) {
            List<EntityLivingBase> entities = getWorld().getEntitiesWithinAABB(EntityLivingBase.class,
                    new AxisAlignedBB(getPos()).grow(size));
            entities.forEach(damageFunction);
            this.nextDamageTime = timer + 20;
        }
    }

    public void playLossSound() {
        long timer = getOffsetTimer();
        if (timer >= this.nextSoundTime) {
            getWorld().playSound(null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 1.0F, 1.0F);
            this.nextSoundTime = timer + 20;
        }
    }

    public void visuallyExplode() {
        getWorld().createExplosion(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                1.0f + GTValues.RNG.nextFloat(), false);
    }

    public void setNeighborsToFire() {
        for (EnumFacing side : EnumFacing.VALUES) {
            if (!GTValues.RNG.nextBoolean()) continue;
            BlockPos blockPos = getPos().offset(side);
            IBlockState blockState = getWorld().getBlockState(blockPos);
            if (blockState.getBlock().isAir(blockState, getWorld(), blockPos) ||
                    blockState.getBlock().isFlammable(getWorld(), blockPos, side.getOpposite())) {
                getWorld().setBlockState(blockPos, Blocks.FIRE.getDefaultState());
            }
        }
    }
}
