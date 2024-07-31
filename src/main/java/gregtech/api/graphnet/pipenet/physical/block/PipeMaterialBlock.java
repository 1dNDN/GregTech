package gregtech.api.graphnet.pipenet.physical.block;

import gregtech.api.graphnet.pipenet.IPipeNetNodeHandler;
import gregtech.api.graphnet.pipenet.physical.IPipeMaterialStructure;
import gregtech.api.graphnet.pipenet.physical.tile.PipeMaterialTileEntity;
import gregtech.api.graphnet.pipenet.physical.tile.PipeTileEntity;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.PipeNetProperties;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.material.registry.MaterialRegistry;
import gregtech.api.util.GTUtility;

import gregtech.client.renderer.pipe.PipeModel;

import gregtech.common.ConfigHolder;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Set;

public abstract class PipeMaterialBlock extends WorldPipeBlock {

    public final MaterialRegistry registry;

    public PipeMaterialBlock(IPipeMaterialStructure structure, MaterialRegistry registry) {
        super(structure);
        this.registry = registry;
    }

    @Override
    public void getSubBlocks(@NotNull CreativeTabs itemIn, @NotNull NonNullList<ItemStack> items) {
        for (Material material : registry) {
            if (!getStructure().getOrePrefix().doGenerateItem(material)) continue;
            PipeNetProperties properties = material.getProperty(PropertyKey.PIPENET_PROPERTIES);
            if (properties != null && properties.generatesStructure(getStructure())) {
                items.add(getItem(material));
            }
        }
    }

    @Override
    public @NotNull ItemStack getPickBlock(@NotNull IBlockState state, @NotNull RayTraceResult target,
                                           @NotNull World world, @NotNull BlockPos pos, @NotNull EntityPlayer player) {
        PipeMaterialTileEntity tile = getTileEntity(world, pos);
        if (tile != null) return getItem(tile.getMaterial());
        else return super.getPickBlock(state, target, world, pos, player);
    }

    @Override
    public IPipeMaterialStructure getStructure() {
        return (IPipeMaterialStructure) super.getStructure();
    }

    @NotNull
    public ItemStack getItem(@NotNull Material material) {
        return new ItemStack(this, 1, registry.getIDForObject(material));
    }

    @Nullable
    public Material getMaterialForStack(@NotNull ItemStack stack) {
        return registry.getObjectById(stack.getMetadata());
    }

    @Override
    protected @NotNull IPipeNetNodeHandler getHandler(IBlockAccess world, BlockPos pos) {
        PipeMaterialTileEntity tile = getTileEntity(world, pos);
        if (tile != null) tile.getMaterial().getProperty(PropertyKey.PIPENET_PROPERTIES);
        return Materials.Aluminium.getProperty(PropertyKey.PIPENET_PROPERTIES);
    }

    @Override
    protected @NotNull IPipeNetNodeHandler getHandler(@NotNull ItemStack stack) {
        Material material = getMaterialForStack(stack);
        if (material == null) material = Materials.Aluminium;
        return material.getProperty(PropertyKey.PIPENET_PROPERTIES);
    }

    @Override
    public void addInformation(@NotNull ItemStack stack, World worldIn, @NotNull List<String> tooltip,
                               @NotNull ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        if (ConfigHolder.misc.debug) {
            Material material = getMaterialForStack(stack);
            if (material != null)
                tooltip.add("MetaItem Id: " + getStructure().getOrePrefix().name + material.toCamelCaseString());
        }
    }

    // tile entity //

    @NotNull
    @Override
    protected BlockStateContainer.Builder constructState(BlockStateContainer.@NotNull Builder builder) {
        return super.constructState(builder).add(PipeModel.MATERIAL_PROPERTY);
    }

    @Override
    public @Nullable PipeMaterialTileEntity getTileEntity(@NotNull IBlockAccess world, @NotNull BlockPos pos) {
        if (GTUtility.arePosEqual(lastTilePos.get(), pos)) {
            PipeTileEntity tile = lastTile.get().get();
            if (tile != null) return (PipeMaterialTileEntity) tile;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof PipeMaterialTileEntity pipe) {
            lastTilePos.set(pos);
            lastTile.set(new WeakReference<>(pipe));
            return pipe;
        } else return null;
    }

    @Override
    public Class<? extends PipeMaterialTileEntity> getTileClass(@NotNull World world, @NotNull IBlockState state) {
        return PipeMaterialTileEntity.class;
    }

    @Override
    protected Pair<TextureAtlasSprite, Integer> getParticleTexture(World world, BlockPos blockPos) {
        PipeMaterialTileEntity tile = getTileEntity(world, blockPos);
        if (tile != null) {
            return getStructure().getModel().getParticleTexture(tile.getPaintingColor(), tile.getMaterial());
        }
        return null;
    }
}
