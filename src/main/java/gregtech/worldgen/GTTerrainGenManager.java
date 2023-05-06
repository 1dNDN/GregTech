package gregtech.worldgen;

import gregtech.api.util.PerlinNoise;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public final class GTTerrainGenManager {

    private static PerlinNoise noise;

    private static final Int2ObjectMap<TerrainGenWorker> workers = new Int2ObjectArrayMap<>();

    private static Int2ObjectMap<StoneTypeMapper> stoneTypeMappers = new Int2ObjectArrayMap<>();
    private static IntSet allowedDimensions = new IntArraySet();

    private GTTerrainGenManager() {}

    /**
     * Start the terrain manager. Call when the game is loading.
     */
    static void startup() {
        reloadFromConfig();
    }

    /**
     * Terminate the terrain manager. Call when the server is stopped.
     */
    static void terminate() {
        // reset noise. It will be re-created when a new world is joined, with the proper seed
        noise = null;
    }

    @SubscribeEvent
    public static void populateChunk(@Nonnull PopulateChunkEvent.Pre event) {
        final World world = event.getWorld();
        int dimension = world.provider.getDimension();

        if (!isDimensionAllowed(dimension)) return;

        tryInitializeNoise(world);

        TerrainGenWorker worker = workers.get(dimension);
        if (worker == null) {
            StoneTypeMapper typeMapper = getStoneTypeMapper(dimension);
            if (typeMapper == null) return;

            worker = new TerrainGenWorker(typeMapper);
            addTerrainGenWorker(dimension, worker);
        }

        final int chunkX = event.getChunkX();
        final int chunkZ = event.getChunkZ();

        worker.generate(world, world.getChunk(chunkX, chunkZ), chunkX << 4, chunkZ << 4);
    }

    /**
     * Initializes the noise for the manager.
     *
     * @param world the world to generate in
     */
    private static void tryInitializeNoise(@Nonnull World world) {
        if (noise == null) {
            noise = new PerlinNoise(world.getSeed());
        }
    }

    /**
     * Add a terrain gen worker
     *
     * @param dimension the dimension the worker operates in
     * @param worker the worker
     */
    public static void addTerrainGenWorker(int dimension, @Nonnull TerrainGenWorker worker) {
        workers.put(dimension, worker);
    }

    /**
     * Reload the stone types and dimension from the config
     */
    public static void reloadFromConfig() {
        stoneTypeMappers = readMappersFromConfig();
        allowedDimensions = readDimensionsFromConfig();
    }

    @Nonnull
    private static Int2ObjectMap<StoneTypeMapper> readMappersFromConfig() {
        Int2ObjectMap<StoneTypeMapper> map = new Int2ObjectArrayMap<>();
        return map; //TODO impl
    }

    @Nonnull
    private static IntSet readDimensionsFromConfig() {
        IntSet set = new IntArraySet();
        set.add(0);
        return set; //TODO impl
    }

    /**
     * @param dimension the dimension of the mapper
     * @return the stone type mapper associated with the dimension
     */
    @Nullable
    public static StoneTypeMapper getStoneTypeMapper(int dimension) {
        return stoneTypeMappers.get(dimension);
    }

    /**
     * @param dimension the dimension to check
     * @return if generation is allowed in the dimension
     */
    public static boolean isDimensionAllowed(int dimension) {
        return allowedDimensions.contains(dimension);
    }

    /**
     *
     * @param candidates the candidates to select from
     * @param x the block x coordinate
     * @param y the block y coordinate
     * @param z the block z coordinate
     * @param surfaceY the y value of the world surface
     * @return the selected blockstate to place
     */
    @Nonnull
    public static IBlockState getStateForPos(@Nonnull List<IBlockState> candidates, int x, int y, int z, int surfaceY) {
        // need abs() for x and y, when generating blobs between - and + coords
        float noiseValue = noise.noise(Math.abs(x * 0.01F), y * 1.0F / surfaceY, Math.abs(z * 0.01F), 4, 0.1F);
        return candidates.get((int) (candidates.size() * noiseValue));
    }
}
