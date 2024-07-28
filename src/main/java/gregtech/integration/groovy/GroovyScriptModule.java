package gregtech.integration.groovy;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.fluids.FluidBuilder;
import gregtech.api.graphnet.pipenet.physical.block.PipeMaterialBlock;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.modules.GregTechModule;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ingredients.GTRecipeOreInput;
import gregtech.api.unification.Element;
import gregtech.api.unification.Elements;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.event.MaterialEvent;
import gregtech.api.unification.material.event.PostMaterialEvent;
import gregtech.api.unification.material.properties.PipeNetProperties;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.material.registry.MaterialRegistry;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.util.Mods;
import gregtech.common.blocks.BlockCompressed;
import gregtech.common.blocks.BlockFrame;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.pipelike.block.cable.CableBlock;
import gregtech.common.pipelike.handlers.properties.MaterialEnergyProperties;
import gregtech.common.pipelike.handlers.properties.MaterialFluidProperties;
import gregtech.common.pipelike.handlers.properties.MaterialItemProperties;
import gregtech.common.pipelikeold.cable.BlockCable;
import gregtech.common.pipelikeold.fluidpipe.BlockFluidPipe;
import gregtech.common.pipelikeold.itempipe.BlockItemPipe;
import gregtech.integration.IntegrationSubmodule;
import gregtech.modules.GregTechModules;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.cleanroommc.groovyscript.GroovyScript;
import com.cleanroommc.groovyscript.api.GroovyLog;
import com.cleanroommc.groovyscript.api.GroovyPlugin;
import com.cleanroommc.groovyscript.api.IGameObjectParser;
import com.cleanroommc.groovyscript.api.Result;
import com.cleanroommc.groovyscript.compat.mods.GroovyContainer;
import com.cleanroommc.groovyscript.compat.mods.ModPropertyContainer;
import com.cleanroommc.groovyscript.event.ScriptRunEvent;
import com.cleanroommc.groovyscript.gameobjects.GameObjectHandler;
import com.cleanroommc.groovyscript.helper.EnumHelper;
import com.cleanroommc.groovyscript.sandbox.expand.ExpansionHelper;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Supplier;

@Optional.Interface(modid = Mods.Names.GROOVY_SCRIPT,
                    iface = "com.cleanroommc.groovyscript.api.GroovyPlugin",
                    striprefs = true)
@GregTechModule(
                moduleID = GregTechModules.MODULE_GRS,
                containerID = GTValues.MODID,
                modDependencies = Mods.Names.GROOVY_SCRIPT,
                name = "GregTech GroovyScript Integration",
                description = "GroovyScript Integration Module")
public class GroovyScriptModule extends IntegrationSubmodule implements GroovyPlugin {

    private static GroovyContainer<?> modSupportContainer;
    private static final Object2ObjectOpenHashMap<String, Map<String, ItemStack>> metaItems = new Object2ObjectOpenHashMap<>();

    @NotNull
    @Override
    public List<Class<?>> getEventBusSubscribers() {
        return ImmutableList.of(GroovyHandCommand.class, GroovyScriptModule.class);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRecipeEvent(RegistryEvent.Register<IRecipe> event) {
        GroovyScriptModule.loadMetaItemBracketHandler();
    }

    @SubscribeEvent
    @Optional.Method(modid = Mods.Names.GROOVY_SCRIPT)
    public static void afterScriptLoad(ScriptRunEvent.Post event) {
        // Not Needed if JEI Module is enabled
        if (!GregTechAPI.moduleManager.isModuleEnabled(GregTechModules.MODULE_JEI))
            GTRecipeOreInput.refreshStackCache();
    }

    public static boolean isCurrentlyRunning() {
        return GregTechAPI.moduleManager.isModuleEnabled(GregTechModules.MODULE_GRS) &&
                GroovyScript.getSandbox().isRunning();
    }

    public static <T extends Enum<T>> T parseAndValidateEnumValue(Class<T> clazz, String raw, String type) {
        return parseAndValidateEnumValue(clazz, raw, type, false);
    }

    @Contract("_,_,_,true -> !null")
    public static <T extends Enum<T>> T parseAndValidateEnumValue(Class<T> clazz, String raw, String type,
                                                                  boolean crash) {
        T t = EnumHelper.valueOfNullable(clazz, raw, false);
        if (t == null) {
            String msg = GroovyLog.format("Can't find {} for {} in material builder. Valid values are {};",
                    type,
                    raw,
                    Arrays.toString(clazz.getEnumConstants()));
            if (crash) throw new NoSuchElementException(msg);
            GroovyLog.get().error(msg);
            return null;
        }
        return t;
    }

    public static GroovyContainer<?> getInstance() {
        return modSupportContainer;
    }

    public static boolean validateNonNull(Object o, Supplier<String> errorMsg) {
        if (o == null) {
            if (isCurrentlyRunning()) {
                GroovyLog.get().error(errorMsg.get());
            }
            return false;
        }
        return true;
    }

    @Nullable
    public static ItemStack getMetaItem(String name) {
        String[] resultName = splitObjectName(name);
        Map<String, ItemStack> map = metaItems.get(resultName[0]);
        if (map != null) {
            ItemStack stack = map.get(resultName[1]);
            if (stack != null) {
                return stack.copy();
            }
        }

        ItemStack stack = getMetaTileEntityItem(resultName);
        if (stack != null) {
            return stack.copy();
        }
        return null;
    }

    @Nullable
    public static ItemStack getMetaTileEntityItem(String[] split) {
        MetaTileEntity metaTileEntity = GregTechAPI.MTE_REGISTRY.getObject(new ResourceLocation(split[0], split[1]));
        return metaTileEntity == null ? null : metaTileEntity.getStackForm();
    }

    public static String[] splitObjectName(String toSplit) {
        String[] resultSplit = { GTValues.MODID, toSplit };
        int i = toSplit.indexOf(':');
        if (i >= 0) {
            resultSplit[1] = toSplit.substring(i + 1);
            if (i > 1) {
                resultSplit[0] = toSplit.substring(0, i);
            }
        }
        return resultSplit;
    }

    public static void loadMetaItemBracketHandler() {
        metaItems.clear();

        for (Map.Entry<Material, BlockCompressed> entry : MetaBlocks.COMPRESSED.entrySet()) {
            String modid = entry.getKey().getModid();
            Map<String, ItemStack> map = metaItems.computeIfAbsent(modid, (k) -> new Object2ObjectOpenHashMap<>());
            String name = "block" + entry.getKey().toCamelCaseString();
            ItemStack stack = entry.getValue().getItem(entry.getKey());
            map.put(name, stack);
        }
        for (Map.Entry<Material, BlockFrame> entry : MetaBlocks.FRAMES.entrySet()) {
            String modid = entry.getKey().getModid();
            Map<String, ItemStack> map = metaItems.computeIfAbsent(modid, (k) -> new Object2ObjectOpenHashMap<>());
            String name = "frame" + entry.getKey().toCamelCaseString();
            ItemStack stack = entry.getValue().getItem(entry.getKey());
            map.put(name, stack);
        }

        for (MaterialRegistry registry : GregTechAPI.materialManager.getRegistries()) {
            String modid = registry.getModid();
            Map<String, ItemStack> map = metaItems.computeIfAbsent(modid, (k) -> new Object2ObjectOpenHashMap<>());

            for (Material material : registry) {
                PipeNetProperties prop = material.getProperty(PropertyKey.PIPENET_PROPERTIES);
                if (prop == null) continue;

                for (PipeNetProperties.IPipeNetMaterialProperty property : prop.getRegisteredProperties()) {
                    // TODO this is awkward, surely there's a better solution?
                    if (property instanceof MaterialEnergyProperties) {
                        for (CableBlock cable : MetaBlocks.CABLES.get(modid)) {
                            String name = cable.getStructure().getOrePrefix().name + material.toCamelCaseString();
                            ItemStack stack = cable.getItem(material);
                            map.put(name, stack);
                        }
                    } else if (property instanceof MaterialItemProperties || property instanceof MaterialFluidProperties) {
                        for (PipeMaterialBlock pipe : MetaBlocks.MATERIAL_PIPES.get(modid)) {
                            String name = pipe.getStructure().getOrePrefix().name + material.toCamelCaseString();
                            ItemStack stack = pipe.getItem(material);
                            map.put(name, stack);
                        }
                    }
                }
            }
            metaItems.put(modid, map);
        }

        for (MetaItem<?> item : MetaItem.getMetaItems()) {
            Map<String, ItemStack> map = metaItems.computeIfAbsent(
                    Objects.requireNonNull(item.getRegistryName()).getNamespace(),
                    (k) -> new Object2ObjectOpenHashMap<>());
            for (MetaItem<?>.MetaValueItem entry : item.getAllItems()) {
                if (!entry.unlocalizedName.equals("meta_item")) {
                    map.put(entry.unlocalizedName, entry.getStackForm());
                }
            }
        }
    }

    @Override
    public @NotNull String getModId() {
        return GTValues.MODID;
    }

    @Override
    public @NotNull String getContainerName() {
        return GTValues.MOD_NAME;
    }

    @Optional.Method(modid = Mods.Names.GROOVY_SCRIPT)
    @Override
    public @Nullable ModPropertyContainer createModPropertyContainer() {
        return new PropertyContainer();
    }

    @Override
    public void onCompatLoaded(GroovyContainer<?> groovyContainer) {
        GroovyScriptModule.modSupportContainer = groovyContainer;
        GameObjectHandler.builder("recipemap", RecipeMap.class)
                .mod(GTValues.MODID)
                .parser(IGameObjectParser.wrapStringGetter(RecipeMap::getByName))
                .completerOfNamed(RecipeMap::getRecipeMaps, RecipeMap::getUnlocalizedName)
                .register();
        GameObjectHandler.builder("material", Material.class)
                .mod(GTValues.MODID)
                .parser(IGameObjectParser.wrapStringGetter(GregTechAPI.materialManager::getMaterial))
                .completerOfNamed(GregTechAPI.materialManager::getRegisteredMaterials,
                        mat -> mat.getResourceLocation().toString())
                .register();

        GameObjectHandler.builder("oreprefix", OrePrefix.class)
                .mod(GTValues.MODID)
                .parser(IGameObjectParser.wrapStringGetter(OrePrefix::getPrefix))
                .completerOfNamed(OrePrefix::values, v -> v.name)
                .register();

        GameObjectHandler.builder("metaitem", ItemStack.class)
                .mod(GTValues.MODID)
                .parser(IGameObjectParser.wrapStringGetter(GroovyScriptModule::getMetaItem))
                .completer((paramIndex, items) -> {
                    if (paramIndex != 0) return;
                    for (var iterator = metaItems.object2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
                        var entry = iterator.next();
                        String mod = entry.getKey();
                        for (String key : entry.getValue().keySet()) {
                            var item = new CompletionItem(mod + ":" + key);
                            item.setKind(CompletionItemKind.Constant);
                            items.add(item);
                        }
                    }
                })
                .register();

        GameObjectHandler.builder("element", Element.class)
                .mod(GTValues.MODID)
                .parser((s, args) -> {
                    Element element = Elements.get(s);
                    if (element != null) return Result.some(element);
                    if (s.length() <= 6) {
                        for (Element element1 : Elements.getAllElements()) {
                            if (element1.symbol.equals(s)) {
                                return Result.some(element1);
                            }
                        }
                    }
                    return Result.error();
                })
                .completerOfNamed(Elements::getAllElements, Element::getName)
                .register();

        ExpansionHelper.mixinClass(Material.class, MaterialExpansion.class);
        ExpansionHelper.mixinClass(Material.class, MaterialPropertyExpansion.class);
        ExpansionHelper.mixinClass(Material.Builder.class, GroovyMaterialBuilderExpansion.class);
        ExpansionHelper.mixinMethod(RecipeBuilder.class, GroovyExpansions.class, "property");
        ExpansionHelper.mixinMethod(MaterialEvent.class, GroovyExpansions.class, "materialBuilder");
        ExpansionHelper.mixinMethod(MaterialEvent.class, GroovyExpansions.class, "toolBuilder");
        ExpansionHelper.mixinMethod(MaterialEvent.class, GroovyExpansions.class, "fluidBuilder");
        ExpansionHelper.mixinMethod(PostMaterialEvent.class, GroovyExpansions.class, "toolBuilder");
        ExpansionHelper.mixinMethod(PostMaterialEvent.class, GroovyExpansions.class, "fluidBuilder");
        ExpansionHelper.mixinMethod(FluidBuilder.class, GroovyExpansions.class, "acidic");
    }

    protected static boolean checkFrozen(String description) {
        if (!GregTechAPI.materialManager.canModifyMaterials()) {
            GroovyLog.get().error("Cannot {} now, must be done in preInit loadStage and material event", description);
            return true;
        }
        return false;
    }

    protected static void logError(Material m, String cause, String type) {
        GroovyLog.get().error(
                "Cannot {0} of a Material with no {1}! Try calling \"add{1}\" in your late material event first if this is intentional. Material: {2}",
                cause, type, m.getUnlocalizedName());
    }
}
