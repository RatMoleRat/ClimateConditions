package org.terasology.climateConditions;

import com.google.common.base.Function;
import org.terasology.core.world.CoreBiome;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.delay.DelayManager;
import org.terasology.logic.delay.PeriodicActionTriggeredEvent;
import org.terasology.logic.players.LocalPlayer;
import org.terasology.math.geom.Vector3i;
import org.terasology.registry.In;
import org.terasology.world.WorldProvider;
import org.terasology.world.biomes.Biome;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@RegisterSystem(value = RegisterMode.AUTHORITY)
public class BiomeShifterSystem extends BaseComponentSystem {
    private final int SEA_LEVEL = 32;

    @In
    LocalPlayer localPlayer;

    private Map<Vector3i, Float> updateList; //The Vector3i is the initial position, the Float is the surface y-position

    @In
    private WorldProvider world;

    @In
    private BlockManager blockManager;

    @In
    private ClimateConditionsSystem conditionsSystem;

    @In
    private DelayManager delayManager;

    private Block air;
    private Block stone;
    private Block water;
    private Block ice; //TODO: use ice
    private Block sand;
    private Block grass;
    private Block snow;
    private Block dirt;

    @Override
    public void postBegin() {
        updateList = new HashMap<>();

        stone = blockManager.getBlock("core:stone");
        water = blockManager.getBlock("core:water");
        ice = blockManager.getBlock("core:Ice");
        sand = blockManager.getBlock("core:Sand");
        grass = blockManager.getBlock("core:Grass");
        snow = blockManager.getBlock("core:Snow");
        dirt = blockManager.getBlock("core:Dirt");
        air = blockManager.getBlock("engine:air");

        delayManager.addPeriodicAction(world.getWorldEntity(), "biomeChange", 6000, 6000);

        conditionsSystem.setWorldSeed(world.getSeed());
        conditionsSystem.configureTemperature(SEA_LEVEL, 1000, 0.5f, new Function<Float, Float>() {
            @Nullable
            @Override
            public Float apply(@Nullable Float input) {
                return input;
            }
        }, 0, 1);
        conditionsSystem.configureHumidity(SEA_LEVEL, 1000, 0.5f, new Function<Float, Float>() {
            @Nullable
            @Override
            public Float apply(@Nullable Float input) {
                return input;
            }
        }, 0, 1);
        //TODO: make effects come from the world - not centered on the player
    }

    /**
     * Possibly shifts the biomes around the players.
     * @param event The Periodic event that triggers the the biome change (not used).
     * @param ref The world entity that sent the event (not used).
     */
    @ReceiveEvent
    public void biomeChangeEvent(PeriodicActionTriggeredEvent event, EntityRef ref) {
        if (event.getActionId().equals("biomeChange")) {
            extendBiomes();
            float x = localPlayer.getPosition().x + (float) (Math.random() * 20 - 10);
            float z = localPlayer.getPosition().z + (float) (Math.random() * 20 - 10);
            float surfaceY = getSurfaceBlock(x, localPlayer.getPosition().y, z);
            Vector3i location = new Vector3i(x, surfaceY, z);
            Biome biome = calculateBiome(x, surfaceY, z);
            world.setBiome(location, biome);
            updateList.put(location, surfaceY);
        }
    }

    private Biome calculateBiome(float x, float y, float z) {
        float temp = conditionsSystem.getTemperatureBaseField().get(x, y, z);
        float hum = conditionsSystem.getHumidityBaseField().get(x, y, z);
        if (y <= SEA_LEVEL) {
            return CoreBiome.OCEAN;
        } else if (y <= SEA_LEVEL + 2) {
            return CoreBiome.BEACH;
        } else if (temp >= 0.5f && hum < 0.3f) {
            return CoreBiome.DESERT;
        } else if (hum >= 0.3f && hum <= 0.6f && temp >= 0.5f) {
            return CoreBiome.PLAINS;
        } else if (temp <= 0.3f && hum > 0.5f) {
            return CoreBiome.SNOW;
        } else if (hum >= 0.2f && hum <= 0.6f && temp < 0.5f) {
            return CoreBiome.MOUNTAINS;
        } else {
            return CoreBiome.FOREST;
        }
    }

    private void extendBiomes() {
        for (Vector3i vect : updateList.keySet()) {
            if (updateList.containsKey(vect)) {
                float yPos = updateList.get(vect);
                Biome biome = calculateBiome(vect.x, yPos, vect.z);
                int depth = (int) (vect.y - updateList.get(vect));
                Vector3i newVect = new Vector3i(vect.x, yPos, vect.z);
                Block toSet = null;
                switch ((CoreBiome) biome) {
                    case FOREST:
                    case PLAINS:
                    case MOUNTAINS:
                        if (depth == 0 && yPos > SEA_LEVEL && yPos < SEA_LEVEL + 96) {
                            if (world.getBlock(newVect).equals(water) || world.getBlock(newVect).equals(ice)) {
                                toSet = water;
                            } else {
                                toSet = grass;
                            }
                        } else if (depth == 0 && yPos >= SEA_LEVEL + 96) {
                            if (world.getBlock(newVect).equals(water) || world.getBlock(newVect).equals(ice)) {
                                toSet = ice;
                            } else {
                                toSet = snow;
                            }
                        } else if (depth > 32) {
                            toSet = stone;
                        } else {
                            toSet = dirt;
                        }
                        break;
                    case SNOW:
                        if (world.getBlock(newVect).equals(water) || world.getBlock(newVect).equals(ice)) {
                            toSet = ice;
                        } else if (depth == 0 && yPos > SEA_LEVEL) {
                            toSet = snow;
                        } else if (depth > 32) {
                            toSet = stone;
                        } else {
                            toSet = dirt;
                        }
                        break;
                    case DESERT:
                        if (world.getBlock(newVect).equals(water) || world.getBlock(newVect).equals(ice)) {
                            toSet = water;
                        } else if (depth > 8) {
                            toSet = stone;
                        } else {
                            toSet = sand;
                        }
                        break;
                    case OCEAN:
                        if (world.getBlock(newVect).equals(water) || world.getBlock(newVect).equals(ice)) {
                            toSet = water;
                        } else if (depth == 0) {
                            toSet = sand;
                        } else {
                            toSet = stone;
                        }
                        break;
                    case BEACH:
                        if (world.getBlock(newVect).equals(water) || world.getBlock(newVect).equals(ice)) {
                            toSet = water;
                        } else if (depth < 3) {
                            toSet = sand;
                        } else {
                            toSet = stone;
                        }
                        break;
                }
                world.setBlock(newVect, toSet);
                world.setBiome(newVect, biome);

                updateList.replace(vect, updateList.get(vect) - 1);
            }
        }
    }

    /**
     * Finds the first block on the surface at a given position, then returns its y position.
     * @param x The x position to look at.
     * @param y The (initial) y position to look at.
     * @param z The z position to look at.
     * @return The y position corresponding to the highest block at the given position that isn't air.
     * TODO: changes the blocks all the way up (starting at 0)
     */
    private float getSurfaceBlock(float x, float y, float z) {
        float yPos = y;
        int adjustments = 0; // limit the looking to 40 adjustments, just in case
        while (!world.getBlock(new Vector3i(x, yPos, z)).equals(air) && adjustments < 40) {
            yPos++;
            adjustments++;
        }
        while (world.getBlock(new Vector3i(x, yPos, z)).equals(air) && adjustments < 40) {
            yPos--;
            adjustments++;
        }
        return yPos;
    }
}
