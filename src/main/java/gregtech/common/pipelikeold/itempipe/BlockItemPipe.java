package gregtech.common.pipelikeold.itempipe;

import gregtech.api.items.toolitem.ToolClasses;
import gregtech.api.graphnet.pipenetold.block.material.BlockMaterialPipe;
import gregtech.api.graphnet.edge.NetEdge;
import gregtech.api.graphnet.pipenetold.tile.IPipeTile;
import gregtech.api.graphnet.pipenetold.tile.TileEntityPipeBase;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.registry.MaterialRegistry;
import gregtech.client.renderer.pipeold.ItemPipeRenderer;
import gregtech.client.renderer.pipeold.PipeRenderer;
import gregtech.common.creativetab.GTCreativeTabs;
import gregtech.common.pipelikeold.itempipe.net.WorldItemPipeNet;
import gregtech.common.pipelikeold.itempipe.tile.TileEntityItemPipe;
import gregtech.common.pipelikeold.itempipe.tile.TileEntityItemPipeTickable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BlockItemPipe extends BlockMaterialPipe<ItemPipeType, ItemPipeProperties, NetEdge, WorldItemPipeNet> {

    private final Map<Material, ItemPipeProperties> enabledMaterials = new HashMap<>();

    public BlockItemPipe(ItemPipeType itemPipeType, MaterialRegistry registry) {
        super(itemPipeType, registry);
        setCreativeTab(GTCreativeTabs.TAB_GREGTECH_PIPES);
        setHarvestLevel(ToolClasses.WRENCH, 1);
    }

    public void addPipeMaterial(Material material, ItemPipeProperties properties) {
        Preconditions.checkNotNull(material, "material");
        Preconditions.checkNotNull(properties, "material %s itemPipeProperties was null", material);
        Preconditions.checkArgument(material.getRegistry().getNameForObject(material) != null,
                "material %s is not registered", material);
        this.enabledMaterials.put(material, properties);
    }

    @Override
    public TileEntityPipeBase<ItemPipeType, ItemPipeProperties, NetEdge> createNewTileEntity(boolean supportsTicking) {
        return supportsTicking ? new TileEntityItemPipeTickable() : new TileEntityItemPipe();
    }

    @Override
    public Class<ItemPipeType> getPipeTypeClass() {
        return ItemPipeType.class;
    }

    @Override
    protected ItemPipeProperties getFallbackType() {
        return enabledMaterials.values().iterator().next();
    }

    @Override
    public WorldItemPipeNet getWorldPipeNet(World world) {
        return WorldItemPipeNet.getWorldPipeNet(world);
    }

    @Override
    protected Pair<TextureAtlasSprite, Integer> getParticleTexture(World world, BlockPos blockPos) {
        return ItemPipeRenderer.INSTANCE.getParticleTexture((TileEntityItemPipe) world.getTileEntity(blockPos));
    }

    @Override
    protected ItemPipeProperties createProperties(ItemPipeType itemPipeType, Material material) {
        return itemPipeType.modifyProperties(enabledMaterials.getOrDefault(material, getFallbackType()));
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    public PipeRenderer getPipeRenderer() {
        return ItemPipeRenderer.INSTANCE;
    }

    public Collection<Material> getEnabledMaterials() {
        return Collections.unmodifiableSet(enabledMaterials.keySet());
    }

    @Override
    public void getSubBlocks(@NotNull CreativeTabs itemIn, @NotNull NonNullList<ItemStack> items) {
        for (Material material : enabledMaterials.keySet()) {
            items.add(getItem(material));
        }
    }

    @Override
    public boolean canPipesConnect(IPipeTile<ItemPipeType, ItemPipeProperties, NetEdge> selfTile, EnumFacing side,
                                   IPipeTile<ItemPipeType, ItemPipeProperties, NetEdge> sideTile) {
        return selfTile instanceof TileEntityItemPipe && sideTile instanceof TileEntityItemPipe;
    }

    @Override
    public boolean canPipeConnectToBlock(IPipeTile<ItemPipeType, ItemPipeProperties, NetEdge> selfTile, EnumFacing side,
                                         TileEntity tile) {
        return tile != null &&
                tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side.getOpposite()) != null;
    }

    @Override
    public boolean isHoldingPipe(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        ItemStack stack = player.getHeldItemMainhand();
        return stack != ItemStack.EMPTY && stack.getItem() instanceof ItemBlockItemPipe;
    }

    @Override
    @NotNull
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("deprecation")
    public EnumBlockRenderType getRenderType(@NotNull IBlockState state) {
        return ItemPipeRenderer.INSTANCE.getBlockRenderType();
    }
}
