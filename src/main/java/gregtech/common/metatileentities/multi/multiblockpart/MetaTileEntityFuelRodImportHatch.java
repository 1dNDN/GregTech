package gregtech.common.metatileentities.multi.multiblockpart;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.ILockableItemHandler;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.SlotWidget;
import gregtech.api.items.itemhandlers.LockableItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IFissionReactorHatch;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.blocks.BlockFissionCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.MetaTileEntities;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import java.util.List;

public class MetaTileEntityFuelRodImportHatch extends MetaTileEntityMultiblockNotifiablePart implements IMultiblockAbilityPart<ILockableItemHandler>, IControllable, IFissionReactorHatch {

    private boolean workingEnabled;
    private boolean valid;

    public MetaTileEntityFuelRodImportHatch(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, 4, false);
        this.frontFacing = EnumFacing.UP;
    }

    @Override
    public boolean isWorkingEnabled() {
        return workingEnabled;
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        this.workingEnabled = isWorkingAllowed;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityFuelRodImportHatch(metaTileEntityId);
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new ItemStackHandler(1);
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new LockableItemStackHandler(this, false);
    }

    private ModularUI.Builder createUITemplate(EntityPlayer player) {
        ModularUI.Builder builder = ModularUI.builder(GuiTextures.BACKGROUND, 176, 143)
                .label(10, 5, getMetaFullName());

        builder.widget(new SlotWidget(importItems, 0, 79, 18, true, true)
                .setBackgroundTexture(GuiTextures.SLOT));

        return builder.bindPlayerInventory(player.inventory, GuiTextures.SLOT, 7, 60);
    }

    @Override
    protected ModularUI createUI(EntityPlayer entityPlayer) {
        return createUITemplate(entityPlayer).build(getHolder(), entityPlayer);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            SimpleOverlayRenderer renderer = isExportHatch ? Textures.PIPE_OUT_OVERLAY : Textures.PIPE_IN_OVERLAY;
            renderer.renderSided(getFrontFacing(), renderState, translation, pipeline);
            SimpleOverlayRenderer overlay = isExportHatch ? Textures.ITEM_HATCH_OUTPUT_OVERLAY : Textures.ITEM_HATCH_INPUT_OVERLAY;
            overlay.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    @Override
    public MultiblockAbility<ILockableItemHandler> getAbility() {
        return MultiblockAbility.IMPORT_FUEL_ROD;
    }

    @Override
    public void registerAbilities(List<ILockableItemHandler> abilityList) {
        abilityList.add((ILockableItemHandler) this.importItems);
    }

    @Override
    public void setFrontFacing(EnumFacing frontFacing) {
        super.setFrontFacing(EnumFacing.UP);
    }

    @Override
    public boolean checkValidity(int depth) {
        BlockPos pos = this.getPos();
        for (int i = 1; i < depth; i++) {
            if (getWorld().getBlockState(pos.offset(EnumFacing.DOWN, i)) != MetaBlocks.FISSION_CASING.getState(BlockFissionCasing.FissionCasingType.FUEL_CHANNEL)) {
                return false;
            }
        }
        if (getWorld().getTileEntity(pos.offset(EnumFacing.DOWN, depth)) instanceof IGregTechTileEntity gtTe) {
            return gtTe.getMetaTileEntity().metaTileEntityId.equals(MetaTileEntities.FUEL_ROD_OUTPUT.metaTileEntityId);
        }
        return false;
    }

    @Override
    public void setValid(boolean valid) {
        this.valid = valid;
    }
}