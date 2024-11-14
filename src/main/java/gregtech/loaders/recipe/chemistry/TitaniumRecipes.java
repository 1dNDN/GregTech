package gregtech.loaders.recipe.chemistry;

import static gregtech.api.GTValues.*;
import static gregtech.api.recipes.RecipeMaps.*;
import static gregtech.api.recipes.RecipeMaps.BLAST_RECIPES;
import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.material.Materials.Water;
import static gregtech.api.unification.ore.OrePrefix.*;

public class TitaniumRecipes {

    public static void init() {
        titaniumProcess();
        solvayProcess();
        bauxiteProcess();
        ilmeniteProcess();
    }

    // Ilmenite and Rutile Processing
    private static void titaniumProcess() {
        // Rutile extraction from Ilmenite
        // FeTiO3 + C -> Fe + TiO2 + CO
        BLAST_RECIPES.recipeBuilder()
                .inputItem(dust, Ilmenite, 5)
                .inputItem(dust, Carbon)
                .outputItem(ingot, WroughtIron)
                .outputItem(dust, Rutile, 3)
                .fluidOutputs(CarbonMonoxide.getFluid(1000))
                .blastFurnaceTemp(1700)
                .duration(1600).volts(VA[HV]).buildAndRegister();

        // Chloride Process
        // TiO2 + 2C + 4Cl -> TiCl4 + 2CO
        CHEMICAL_RECIPES.recipeBuilder()
                .inputItem(dust, Carbon, 2)
                .inputItem(dust, Rutile)
                .fluidInputs(Chlorine.getFluid(4000))
                .fluidOutputs(CarbonMonoxide.getFluid(2000))
                .fluidOutputs(TitaniumTetrachloride.getFluid(1000))
                .duration(400).volts(VA[HV]).buildAndRegister();

        // Kroll Process
        // TiCl4 + 2Mg -> Ti + 2MgCl2
        BLAST_RECIPES.recipeBuilder().duration(800).volts(VA[HV])
                .inputItem(dust, Magnesium, 2)
                .fluidInputs(TitaniumTetrachloride.getFluid(1000))
                .outputItem(ingotHot, Titanium)
                .outputItem(dust, MagnesiumChloride, 6)
                .blastFurnaceTemp(Titanium.getBlastTemperature() + 200)
                .buildAndRegister();

        CHEMICAL_RECIPES.recipeBuilder().duration(200).volts(VA[HV])
                .inputItem(dust, Sodium, 2)
                .inputItem(dust, MagnesiumChloride, 3)
                .outputItem(dust, Magnesium)
                .outputItem(dust, Salt, 4)
                .buildAndRegister();
    }

    // The production of Soda Ash and Calcium Chloride from Salt and Calcite
    // Used in the Bauxite Process
    private static void solvayProcess() {
        // CaCO3 -> CaO + CO2
        CHEMICAL_RECIPES.recipeBuilder()
                .circuitMeta(1)
                .inputItem(dust, Calcite, 5)
                .outputItem(dust, Quicklime, 2)
                .fluidOutputs(CarbonDioxide.getFluid(1000))
                .duration(200).volts(VA[LV]).buildAndRegister();

        // NaCl(H2O) + CO2 + NH3 -> NH4Cl + NaHCO3
        CHEMICAL_RECIPES.recipeBuilder()
                .inputItem(dust, Salt, 4)
                .fluidInputs(CarbonDioxide.getFluid(1000))
                .fluidInputs(Ammonia.getFluid(1000))
                .fluidInputs(Water.getFluid(1000))
                .outputItem(dust, AmmoniumChloride, 2)
                .outputItem(dust, SodiumBicarbonate, 6)
                .duration(400).volts(VA[MV]).buildAndRegister();

        // 2NaHCO3 -> Na2CO3 + CO2 + H2O
        ELECTROLYZER_RECIPES.recipeBuilder()
                .inputItem(dust, SodiumBicarbonate, 12)
                .outputItem(dust, SodaAsh, 6)
                .fluidOutputs(CarbonDioxide.getFluid(1000))
                .fluidOutputs(Water.getFluid(1000))
                .duration(200).volts(VA[MV]).buildAndRegister();

        // 2NH4Cl + CaO -> CaCl2 + 2NH3 + H2O
        CHEMICAL_RECIPES.recipeBuilder()
                .inputItem(dust, AmmoniumChloride, 4)
                .inputItem(dust, Quicklime, 2)
                .outputItem(dust, CalciumChloride, 3)
                .fluidOutputs(Ammonia.getFluid(2000))
                .fluidOutputs(Water.getFluid(1000))
                .duration(200).volts(VA[MV]).buildAndRegister();
    }

    // Advanced separation process for Bauxite
    private static void bauxiteProcess() {
        // Bauxite (crushed) + Soda Ash + Calcium Chloride -> Bauxite Slurry
        MIXER_RECIPES.recipeBuilder()
                .inputItem(crushed, Bauxite, 32)
                .inputItem(dust, SodaAsh, 12)
                .inputItem(dust, CalciumChloride, 6)
                .fluidInputs(Water.getFluid(4000))
                .fluidOutputs(BauxiteSlurry.getFluid(4000))
                .duration(500).volts(VA[HV]).buildAndRegister();

        // Bauxite (washed) + Soda Ash + Calcium Chloride -> Bauxite Slurry
        MIXER_RECIPES.recipeBuilder()
                .inputItem(crushedPurified, Bauxite, 32)
                .inputItem(dust, SodaAsh, 12)
                .inputItem(dust, CalciumChloride, 6)
                .fluidInputs(Water.getFluid(4000))
                .fluidOutputs(BauxiteSlurry.getFluid(4000))
                .duration(500).volts(VA[HV]).buildAndRegister();

        // Bauxite Slurry -> Cracked Bauxite Slurry
        CRACKING_RECIPES.recipeBuilder()
                .circuitMeta(1)
                .fluidInputs(BauxiteSlurry.getFluid(16000))
                .fluidInputs(Steam.getFluid(1000))
                .fluidOutputs(CrackedBauxiteSlurry.getFluid(16000))
                .duration(500).volts(VA[HV]).buildAndRegister();

        // Bauxite Slurry + Sulfuric -> Aluminium, Slag, Sludge, and SO3 (for looping back to Sulfuric Acid)
        LARGE_CHEMICAL_RECIPES.recipeBuilder()
                .fluidInputs(CrackedBauxiteSlurry.getFluid(4000))
                .fluidInputs(SulfuricAcid.getFluid(1000))
                .outputItem(dust, Aluminium, 24)
                .outputItem(dust, BauxiteSlag, 8)
                .fluidOutputs(BauxiteSludge.getFluid(2500))
                .fluidOutputs(SulfurTrioxide.getFluid(1000))
                .duration(500).volts(VA[HV]).buildAndRegister();

        // Bauxite Slag -> Salt (looped) + Nd + Cr (byproducts)
        ELECTROMAGNETIC_SEPARATOR_RECIPES.recipeBuilder()
                .inputItem(dust, BauxiteSlag)
                .outputItem(dust, Salt).outputItemRoll(dust, Neodymium, 2000, 250)
                .outputItemRoll(dust, Chrome, 1000, 250)
                .duration(50).volts(VA[MV]).buildAndRegister();

        // Bauxite Sludge -> Calcite (looped) + Decalcified Bauxite Sludge
        DISTILLERY_RECIPES.recipeBuilder()
                .circuitMeta(1)
                .fluidInputs(BauxiteSludge.getFluid(500))
                .outputItem(dust, Calcite, 2)
                .fluidOutputs(DecalcifiedBauxiteSludge.getFluid(500))
                .duration(100).volts(VA[MV]).buildAndRegister();

        // Decalcified Bauxite Sludge -> Rutile, Gallium, SiO2, Iron, Water
        CENTRIFUGE_RECIPES.recipeBuilder()
                .fluidInputs(DecalcifiedBauxiteSludge.getFluid(250))
                .outputItem(dust, Rutile, 2).outputItemRoll(dust, Gallium, 5000, 550)
                .outputItemRoll(dust, Gallium, 3000, 800).outputItemRoll(dust, Gallium, 1000, 1000)
                .outputItemRoll(dust, SiliconDioxide, 9000, 250).outputItemRoll(dust, Iron, 8000, 250)
                .fluidOutputs(Water.getFluid(250))
                .duration(100).volts(VA[MV]).buildAndRegister();
    }

    // Byproduct separation for Ilmenite
    private static void ilmeniteProcess() {
        ELECTROMAGNETIC_SEPARATOR_RECIPES.recipeBuilder()
                .inputItem(dust, IlmeniteSlag).outputItemRoll(dust, Iron, 8000, 0).outputItemRoll(dust, Zircon, 2500, 0)
                .outputItemRoll(dust, Tantalum, 2000, 0).outputItemRoll(dust, Niobium, 500, 0)
                .duration(50).volts(VA[MV]).buildAndRegister();
    }
}
