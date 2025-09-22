package com.randoola.lootpackages;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Supplier;

@Mod("lootpackages")
public class LootPackagesMod {

    public static final String MODID = "lootpackages";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Register items properly
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, MODID);

    // Package item registrations
    public static final Supplier<Item> STARTER_SURVIVAL_PACKAGE = ITEMS.register("starter_survival_package",
            () -> new PackageItem(PackageType.STARTER_SURVIVAL));
    public static final Supplier<Item> DUNGEON_LOOT_PACKAGE = ITEMS.register("dungeon_loot_package",
            () -> new PackageItem(PackageType.DUNGEON_LOOT));
    public static final Supplier<Item> TREASURE_PACKAGE = ITEMS.register("treasure_package",
            () -> new PackageItem(PackageType.TREASURE));
    public static final Supplier<Item> NETHER_LOOT_PACKAGE = ITEMS.register("nether_loot_package",
            () -> new PackageItem(PackageType.NETHER_LOOT));
    public static final Supplier<Item> END_LOOT_PACKAGE = ITEMS.register("end_loot_package",
            () -> new PackageItem(PackageType.END_LOOT));
    public static final Supplier<Item> MOB_DROP_PACKAGE = ITEMS.register("mob_drop_package",
            () -> new PackageItem(PackageType.MOB_DROP));
    public static final Supplier<Item> BIOME_PACKAGE = ITEMS.register("biome_package",
            () -> new PackageItem(PackageType.BIOME_SPECIFIC));
    public static final Supplier<Item> MYSTERY_PACKAGE = ITEMS.register("mystery_package",
            () -> new PackageItem(PackageType.MYSTERY));
    public static final Supplier<Item> DEEP_DARK_PACKAGE = ITEMS.register("deep_dark_package",
            () -> new PackageItem(PackageType.DEEP_DARK));

    private static final String NBT_TAG_RECEIVED_STARTER = "received_starter_pack";
    private static final Random RANDOM = new Random();

    // Items to exclude from random selection
    private static final Set<String> EXCLUDED_KEYWORDS = Set.of(
            "barrier", "structure_void", "structure_block", "jigsaw", "command_block",
            "debug_stick", "knowledge_book", "bedrock"
    );

    private static final Set<Item> EXCLUDED_ITEMS = Set.of(
            Items.AIR, Items.BARRIER, Items.STRUCTURE_VOID, Items.STRUCTURE_BLOCK,
            Items.JIGSAW, Items.COMMAND_BLOCK, Items.REPEATING_COMMAND_BLOCK,
            Items.CHAIN_COMMAND_BLOCK, Items.COMMAND_BLOCK_MINECART,
            Items.DEBUG_STICK, Items.KNOWLEDGE_BOOK, Items.BEDROCK
    );

    // PERFORMANCE OPTIMIZATION: Cache for allowed items
    private static List<Item> cachedAllowedItems = null;
    private static boolean itemCacheBuilt = false;

    public LootPackagesMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register items
        ITEMS.register(modEventBus);

        // Register creative mode tabs
        ModCreativeModeTabs.register(modEventBus);

        // Register the config
        modContainer.registerConfig(ModConfig.Type.COMMON, LootPackagesConfig.SPEC);

        // Register for setup event to build cache
        modEventBus.addListener(this::onCommonSetup);

        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("Loot Packages Mod loaded - Multiple themed packages available!");
        LOGGER.info("Config file will be created at: config/lootpackages-common.toml");
    }

    public void onCommonSetup(FMLCommonSetupEvent event) {
        // Build the item cache after all mods have registered their items
        event.enqueueWork(() -> {
            buildItemCache();
            LOGGER.info("Built item cache with {} allowed items", cachedAllowedItems.size());
        });
    }

    /**
     * Builds the cache of allowed items once during mod initialization
     * This prevents expensive registry iteration every time mystery packages are opened
     */
    private static void buildItemCache() {
        if (itemCacheBuilt) {
            return; // Prevent multiple cache builds
        }

        cachedAllowedItems = new ArrayList<>();
        int totalItems = 0;
        int excludedItems = 0;

        for (Item item : BuiltInRegistries.ITEM) {
            totalItems++;
            ResourceLocation location = BuiltInRegistries.ITEM.getKey(item);
            if (location != null) {
                boolean isVanilla = "minecraft".equals(location.getNamespace());
                if (isVanilla && isItemAllowed(item)) {
                    cachedAllowedItems.add(item);
                } else {
                    excludedItems++;
                }
            } else {
                excludedItems++;
            }
        }

        // Make the list immutable to prevent accidental modification
        cachedAllowedItems = Collections.unmodifiableList(cachedAllowedItems);
        itemCacheBuilt = true;

        LOGGER.info("Item cache built: {} allowed items out of {} total items ({} excluded)",
                cachedAllowedItems.size(), totalItems, excludedItems);
    }

    /**
     * Gets all allowed server items from cache
     * This is now a fast O(1) operation instead of iterating through the entire registry
     */
    public static List<Item> getAllServerItems() {
        if (!itemCacheBuilt || cachedAllowedItems == null) {
            LOGGER.warn("Item cache not yet built, building now...");
            buildItemCache();
        }
        return cachedAllowedItems;
    }

    /**
     * Force rebuild the item cache (useful if items are registered after initial setup)
     */
    public static void rebuildItemCache() {
        LOGGER.info("Rebuilding item cache...");
        itemCacheBuilt = false;
        cachedAllowedItems = null;
        buildItemCache();
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Check if starter packages are disabled in config
        if (!LootPackagesConfig.shouldGiveStarterPackage()) {
            LOGGER.debug("Starter packages are disabled in config - skipping player {}", player.getName().getString());
            return;
        }

        if (hasReceivedStarterPack(player)) {
            LOGGER.debug("Player {} has already received starter pack", player.getName().getString());
            return;
        }

        // Give player a starter survival package
        player.getInventory().add(new ItemStack(STARTER_SURVIVAL_PACKAGE.get()));
        markReceivedStarterPack(player);

        LOGGER.info("Gave starter survival package to new player: {}", player.getName().getString());
    }

    // Package type definitions
    public enum PackageType {
        STARTER_SURVIVAL("Starter Survival Package"),
        DUNGEON_LOOT("Dungeon Loot Package"),
        TREASURE("Treasure Package"),
        NETHER_LOOT("Nether Loot Package"),
        END_LOOT("End Loot Package"),
        MOB_DROP("Mob Drop Package"),
        BIOME_SPECIFIC("Biome Package"),
        MYSTERY("Mystery Package"),
        DEEP_DARK("Deep Dark Package");

        private final String displayName;

        PackageType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Loot generation methods for each package type
    public static void generatePackageLoot(ServerPlayer player, PackageType packageType) {
        List<ItemStack> loot = new ArrayList<>();

        switch (packageType) {
            case STARTER_SURVIVAL -> generateStarterSurvivalLoot(loot);
            case DUNGEON_LOOT -> generateDungeonLoot(loot);
            case TREASURE -> generateTreasureLoot(loot);
            case NETHER_LOOT -> generateNetherLoot(loot);
            case END_LOOT -> generateEndLoot(loot);
            case MOB_DROP -> generateMobDropLoot(loot);
            case BIOME_SPECIFIC -> generateBiomeLoot(loot);
            case MYSTERY -> generateMysteryLoot(loot);
            case DEEP_DARK -> generateDeepDarkLoot(loot);
        }

        // Give items to player
        for (ItemStack stack : loot) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }

        LOGGER.info("Player {} opened {} and received {} items",
                player.getName().getString(), packageType.getDisplayName(), loot.size());
    }

    private static void generateStarterSurvivalLoot(List<ItemStack> loot) {
        // Basic tools (wooden or stone tier)
        List<Item> basicTools = Arrays.asList(
                Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_SHOVEL,
                Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_SHOVEL
        );
        loot.add(new ItemStack(basicTools.get(RANDOM.nextInt(basicTools.size()))));

        // Food items
        List<Item> foods = Arrays.asList(
                Items.BREAD, Items.APPLE, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN
        );
        for (int i = 0; i < 2; i++) {
            Item food = foods.get(RANDOM.nextInt(foods.size()));
            loot.add(new ItemStack(food, 4 + RANDOM.nextInt(9))); // 4-12 food
        }

        // Essential items
        loot.add(new ItemStack(Items.TORCH, 16 + RANDOM.nextInt(17))); // 16-32 torches
        loot.add(new ItemStack(Items.CRAFTING_TABLE));

        // Seeds
        List<Item> seeds = Arrays.asList(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.CARROT, Items.POTATO);
        loot.add(new ItemStack(seeds.get(RANDOM.nextInt(seeds.size())), 2 + RANDOM.nextInt(4))); // 2-5 seeds

        // Low-tier armor (chance)
        if (RANDOM.nextBoolean()) {
            List<Item> armor = Arrays.asList(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE,
                    Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
            loot.add(new ItemStack(armor.get(RANDOM.nextInt(armor.size()))));
        }

        // Building materials
        loot.add(new ItemStack(Items.OAK_PLANKS, 16 + RANDOM.nextInt(33))); // 16-48 planks
    }

    private static void generateDungeonLoot(List<ItemStack> loot) {
        // Iron gear
        List<Item> ironGear = Arrays.asList(
                Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_HELMET, Items.IRON_CHESTPLATE
        );
        loot.add(new ItemStack(ironGear.get(RANDOM.nextInt(ironGear.size()))));

        // Rare ores
        List<Item> ores = Arrays.asList(Items.GOLD_INGOT, Items.LAPIS_LAZULI, Items.REDSTONE);
        Item ore = ores.get(RANDOM.nextInt(ores.size()));
        loot.add(new ItemStack(ore, 2 + RANDOM.nextInt(6))); // 2-7 items

        // Potions ingredients
        List<Item> potionIngredients = Arrays.asList(
                Items.GLOWSTONE_DUST, Items.REDSTONE, Items.SPIDER_EYE, Items.GOLDEN_CARROT
        );
        loot.add(new ItemStack(potionIngredients.get(RANDOM.nextInt(potionIngredients.size())), 1 + RANDOM.nextInt(3)));

        // Dungeon atmosphere items
        if (RANDOM.nextFloat() < 0.3f) {
            loot.add(new ItemStack(Items.MUSIC_DISC_CAT));
        }
        loot.add(new ItemStack(Items.BONE, 3 + RANDOM.nextInt(5)));
        loot.add(new ItemStack(Items.STRING, 1 + RANDOM.nextInt(4)));
    }

    private static void generateTreasureLoot(List<ItemStack> loot) {
        // Valuable materials
        loot.add(new ItemStack(Items.DIAMOND, 1 + RANDOM.nextInt(3))); // 1-3 diamonds
        loot.add(new ItemStack(Items.EMERALD, 2 + RANDOM.nextInt(4))); // 2-5 emeralds
        loot.add(new ItemStack(Items.GOLD_INGOT, 3 + RANDOM.nextInt(6))); // 3-8 gold

        // Ocean-themed treasure
        if (RANDOM.nextFloat() < 0.4f) {
            loot.add(new ItemStack(Items.HEART_OF_THE_SEA));
        }
        if (RANDOM.nextFloat() < 0.6f) {
            loot.add(new ItemStack(Items.NAUTILUS_SHELL, 1 + RANDOM.nextInt(3)));
        }

        // Maps and compass
        loot.add(new ItemStack(Items.MAP));
        loot.add(new ItemStack(Items.COMPASS));

        // Rare gear
        List<Item> rareGear = Arrays.asList(Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.BOW);
        loot.add(new ItemStack(rareGear.get(RANDOM.nextInt(rareGear.size()))));
    }

    private static void generateNetherLoot(List<ItemStack> loot) {
        // Nether-specific materials
        loot.add(new ItemStack(Items.GOLD_BLOCK, 1 + RANDOM.nextInt(3))); // 1-3 gold blocks

        if (RANDOM.nextFloat() < 0.2f) {
            loot.add(new ItemStack(Items.NETHERITE_SCRAP, 1 + RANDOM.nextInt(2))); // 1-2 scraps
        }

        // Nether items
        loot.add(new ItemStack(Items.MAGMA_CREAM, 2 + RANDOM.nextInt(4))); // 2-5 magma cream
        loot.add(new ItemStack(Items.GHAST_TEAR, 1 + RANDOM.nextInt(2))); // 1-2 tears
        loot.add(new ItemStack(Items.BLAZE_ROD, 1 + RANDOM.nextInt(3))); // 1-3 rods

        // Fire resistance materials
        loot.add(new ItemStack(Items.FIRE_CHARGE, 3 + RANDOM.nextInt(5))); // 3-7 charges

        // Nether building materials
        loot.add(new ItemStack(Items.BLACKSTONE, 8 + RANDOM.nextInt(17))); // 8-24 blackstone
        if (RANDOM.nextFloat() < 0.3f) {
            loot.add(new ItemStack(Items.CRYING_OBSIDIAN, 1 + RANDOM.nextInt(3)));
        }
        if (RANDOM.nextFloat() < 0.4f) {
            loot.add(new ItemStack(Items.CHAIN, 2 + RANDOM.nextInt(4)));
        }
    }

    private static void generateEndLoot(List<ItemStack> loot) {
        // End-specific rare items
        if (RANDOM.nextFloat() < 0.1f) {
            loot.add(new ItemStack(Items.ELYTRA)); // Very rare
        }

        if (RANDOM.nextFloat() < 0.3f) {
            loot.add(new ItemStack(Items.SHULKER_SHELL, 1 + RANDOM.nextInt(2))); // 1-2 shells
        }

        // End materials
        loot.add(new ItemStack(Items.ENDER_PEARL, 4 + RANDOM.nextInt(9))); // 4-12 pearls
        loot.add(new ItemStack(Items.CHORUS_FRUIT, 6 + RANDOM.nextInt(11))); // 6-16 fruit
        loot.add(new ItemStack(Items.CHORUS_FLOWER, 1 + RANDOM.nextInt(3))); // 1-3 flowers

        // High-tier gear
        List<Item> endGear = Arrays.asList(Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_CHESTPLATE);
        loot.add(new ItemStack(endGear.get(RANDOM.nextInt(endGear.size()))));

        // End building materials
        loot.add(new ItemStack(Items.END_STONE, 16 + RANDOM.nextInt(17))); // 16-32 end stone
        loot.add(new ItemStack(Items.END_ROD, 4 + RANDOM.nextInt(5))); // 4-8 end rods

        // XP bottles
        loot.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 5 + RANDOM.nextInt(11))); // 5-15 bottles
    }

    private static void generateMobDropLoot(List<ItemStack> loot) {
        // Mob-specific drops
        List<Item> validMobDrops = Arrays.asList(
                Items.BLAZE_ROD, Items.PHANTOM_MEMBRANE, Items.SPIDER_EYE, Items.SLIME_BALL,
                Items.GHAST_TEAR, Items.ENDER_PEARL, Items.BONE, Items.STRING, Items.GUNPOWDER
        );

        for (int i = 0; i < 3; i++) {
            Item drop = validMobDrops.get(RANDOM.nextInt(validMobDrops.size()));
            loot.add(new ItemStack(drop, 1 + RANDOM.nextInt(4))); // 1-4 of each
        }

        // Rare chance for gear
        if (RANDOM.nextFloat() < 0.2f) {
            List<Item> gearDrops = Arrays.asList(Items.IRON_SWORD, Items.BOW, Items.IRON_HELMET);
            loot.add(new ItemStack(gearDrops.get(RANDOM.nextInt(gearDrops.size()))));
        }

        // Thematic combinations
        if (RANDOM.nextFloat() < 0.3f) {
            loot.add(new ItemStack(Items.COBWEB, 2 + RANDOM.nextInt(4)));
        }
    }

    private static void generateBiomeLoot(List<ItemStack> loot) {
        // Randomly pick a biome theme
        String[] biomes = {"jungle", "desert", "snowy", "ocean", "forest", "mountain"};
        String biome = biomes[RANDOM.nextInt(biomes.length)];

        switch (biome) {
            case "jungle":
                loot.add(new ItemStack(Items.MELON_SLICE, 8 + RANDOM.nextInt(9)));
                loot.add(new ItemStack(Items.BAMBOO, 16 + RANDOM.nextInt(17)));
                loot.add(new ItemStack(Items.COCOA_BEANS, 4 + RANDOM.nextInt(5)));
                loot.add(new ItemStack(Items.JUNGLE_LOG, 8 + RANDOM.nextInt(17)));
                break;

            case "desert":
                loot.add(new ItemStack(Items.SAND, 16 + RANDOM.nextInt(33)));
                loot.add(new ItemStack(Items.CACTUS, 4 + RANDOM.nextInt(5)));
                loot.add(new ItemStack(Items.GOLD_NUGGET, 8 + RANDOM.nextInt(9)));
                loot.add(new ItemStack(Items.DEAD_BUSH, 2 + RANDOM.nextInt(4)));
                break;

            case "snowy":
                loot.add(new ItemStack(Items.PACKED_ICE, 8 + RANDOM.nextInt(9)));
                loot.add(new ItemStack(Items.SNOWBALL, 16 + RANDOM.nextInt(17)));
                loot.add(new ItemStack(Items.RABBIT_HIDE, 3 + RANDOM.nextInt(4)));
                loot.add(new ItemStack(Items.SPRUCE_LOG, 8 + RANDOM.nextInt(17)));
                break;

            case "ocean":
                loot.add(new ItemStack(Items.PRISMARINE, 8 + RANDOM.nextInt(17)));
                loot.add(new ItemStack(Items.KELP, 12 + RANDOM.nextInt(13)));
                if (RANDOM.nextFloat() < 0.1f) {
                    loot.add(new ItemStack(Items.TRIDENT));
                }
                loot.add(new ItemStack(Items.COD, 4 + RANDOM.nextInt(5)));
                break;

            case "forest":
                loot.add(new ItemStack(Items.OAK_LOG, 12 + RANDOM.nextInt(17)));
                loot.add(new ItemStack(Items.APPLE, 6 + RANDOM.nextInt(7)));
                loot.add(new ItemStack(Items.SWEET_BERRIES, 8 + RANDOM.nextInt(9)));
                loot.add(new ItemStack(Items.MUSHROOM_STEW, 2 + RANDOM.nextInt(3)));
                break;

            case "mountain":
                loot.add(new ItemStack(Items.STONE, 16 + RANDOM.nextInt(17)));
                loot.add(new ItemStack(Items.COAL, 8 + RANDOM.nextInt(9)));
                loot.add(new ItemStack(Items.IRON_ORE, 3 + RANDOM.nextInt(4)));
                loot.add(new ItemStack(Items.EMERALD, 1 + RANDOM.nextInt(3)));
                break;
        }

        LOGGER.info("Generated {} biome package", biome);
    }

    private static void generateDeepDarkLoot(List<ItemStack> loot) {
        // === Ancient City Exclusive Items (Always Include) ===
        loot.add(new ItemStack(Items.ECHO_SHARD, 1 + RANDOM.nextInt(3))); // 1-3 echo shards
        loot.add(new ItemStack(Items.DISC_FRAGMENT_5, 1 + RANDOM.nextInt(3))); // 1-3 disc fragments

        // === Common Ancient City Loot ===
        loot.add(new ItemStack(Items.COAL, 6 + RANDOM.nextInt(10))); // 6-15 coal
        loot.add(new ItemStack(Items.BONE, 1 + RANDOM.nextInt(15))); // 1-15 bones
        loot.add(new ItemStack(Items.SOUL_TORCH, 1 + RANDOM.nextInt(15))); // 1-15 soul torches
        loot.add(new ItemStack(Items.CANDLE, 1 + RANDOM.nextInt(4))); // 1-4 candles
        loot.add(new ItemStack(Items.SCULK, 4 + RANDOM.nextInt(7))); // 4-10 sculk
        loot.add(new ItemStack(Items.SCULK_SENSOR, 1 + RANDOM.nextInt(3))); // 1-3 sculk sensors
        loot.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 1 + RANDOM.nextInt(3))); // 1-3 XP bottles
        loot.add(new ItemStack(Items.BOOK, 3 + RANDOM.nextInt(8))); // 3-10 regular books
        loot.add(new ItemStack(Items.AMETHYST_SHARD, 1 + RANDOM.nextInt(15))); // 1-15 amethyst
        loot.add(new ItemStack(Items.GLOW_BERRIES, 1 + RANDOM.nextInt(15))); // 1-15 glow berries

        // === High Value Ancient City Items (Chance-based) ===

        // Enchanted Golden Apple (most valuable)
        if (RANDOM.nextFloat() < 0.4f) {
            loot.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1 + RANDOM.nextInt(2))); // 1-2
        }

        // Music Disc Otherside (very rare)
        if (RANDOM.nextFloat() < 0.15f) {
            loot.add(new ItemStack(Items.MUSIC_DISC_OTHERSIDE));
        }

        // Other valuable items from Ancient City
        if (RANDOM.nextFloat() < 0.20f) {
            loot.add(new ItemStack(Items.SCULK_CATALYST, 1 + RANDOM.nextInt(2))); // 1-2 catalysts
        }

        if (RANDOM.nextFloat() < 0.25f) {
            loot.add(new ItemStack(Items.NAME_TAG));
        }

        if (RANDOM.nextFloat() < 0.25f) {
            loot.add(new ItemStack(Items.LEAD));
        }

        if (RANDOM.nextFloat() < 0.20f) {
            loot.add(new ItemStack(Items.DIAMOND_HORSE_ARMOR));
        }

        if (RANDOM.nextFloat() < 0.20f) {
            loot.add(new ItemStack(Items.SADDLE));
        }

        if (RANDOM.nextFloat() < 0.15f) {
            loot.add(new ItemStack(Items.COMPASS));
        }

        // Music discs
        if (RANDOM.nextFloat() < 0.20f) {
            List<Item> musicDiscs = Arrays.asList(Items.MUSIC_DISC_13, Items.MUSIC_DISC_CAT);
            loot.add(new ItemStack(musicDiscs.get(RANDOM.nextInt(musicDiscs.size()))));
        }

        // Regular potions
        if (RANDOM.nextFloat() < 0.40f) {
            loot.add(new ItemStack(Items.POTION, 1 + RANDOM.nextInt(3))); // 1-3 potions
        }

        // Regular diamond hoe (no enchantments)
        if (RANDOM.nextFloat() < 0.25f) {
            ItemStack hoe = new ItemStack(Items.DIAMOND_HOE);
            // Set some damage like Ancient City does (80-100%)
            int maxDamage = hoe.getMaxDamage();
            int damage = (int)(maxDamage * (0.8f + RANDOM.nextFloat() * 0.2f));
            hoe.setDamageValue(damage);
            loot.add(hoe);
        }

        LOGGER.info("Generated Deep Dark package with authentic Ancient City loot");
    }

    /**
     * OPTIMIZED: Now uses cached item list instead of iterating registry every time
     */
    private static void generateMysteryLoot(List<ItemStack> loot) {
        // Get cached items list (fast operation)
        List<Item> allItems = getAllServerItems();

        if (allItems.isEmpty()) {
            LOGGER.warn("No allowed items found for mystery package! Falling back to basic items.");
            // Fallback to basic items if cache is empty
            loot.add(new ItemStack(Items.STICK, 1 + RANDOM.nextInt(16)));
            loot.add(new ItemStack(Items.COBBLESTONE, 1 + RANDOM.nextInt(32)));
            return;
        }

        // Give 2-5 random items
        int itemCount = 2 + RANDOM.nextInt(4);
        Set<Item> chosenItems = new HashSet<>();

        for (int i = 0; i < itemCount && chosenItems.size() < itemCount; i++) {
            Item randomItem = allItems.get(RANDOM.nextInt(allItems.size()));

            if (chosenItems.contains(randomItem)) {
                i--;
                continue;
            }

            chosenItems.add(randomItem);
            int amount = getRandomAmount(randomItem) / 2 + 1;
            loot.add(new ItemStack(randomItem, Math.max(1, amount)));
        }

        LOGGER.debug("Generated mystery package with {} items from cache of {} total allowed items",
                loot.size(), allItems.size());
    }

    // Utility methods
    private boolean hasReceivedStarterPack(ServerPlayer player) {
        CompoundTag persistentData = player.getPersistentData();
        CompoundTag modData = persistentData.getCompound("lootpackages");
        return modData.getBoolean(NBT_TAG_RECEIVED_STARTER);
    }

    private void markReceivedStarterPack(ServerPlayer player) {
        CompoundTag persistentData = player.getPersistentData();
        CompoundTag modData = persistentData.getCompound("lootpackages");
        modData.putBoolean(NBT_TAG_RECEIVED_STARTER, true);
        persistentData.put("lootpackages", modData);
        LOGGER.info("Marked player {} as having received starter pack", player.getName().getString());
    }

    public static boolean isItemAllowed(Item item) {
        if (EXCLUDED_ITEMS.contains(item)) {
            return false;
        }

        ResourceLocation location = BuiltInRegistries.ITEM.getKey(item);
        if (location != null) {
            String itemName = location.getPath().toLowerCase();
            for (String keyword : EXCLUDED_KEYWORDS) {
                if (itemName.contains(keyword)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static int getRandomAmount(Item item) {
        Random localRandom = new Random();
        ResourceLocation location = BuiltInRegistries.ITEM.getKey(item);
        if (location == null) return 1;

        String itemName = location.getPath().toLowerCase();
        ItemStack tempStack = new ItemStack(item);

        if (tempStack.getMaxStackSize() == 1) {
            return 1;
        }

        // Tool/armor items
        if (itemName.contains("sword") || itemName.contains("pickaxe") || itemName.contains("axe") ||
                itemName.contains("shovel") || itemName.contains("hoe") || itemName.contains("helmet") ||
                itemName.contains("chestplate") || itemName.contains("leggings") || itemName.contains("boots") ||
                itemName.contains("elytra") || itemName.contains("shield")) {
            return 1;
        }

        // Building blocks
        if (itemName.contains("block") || itemName.contains("plank") || itemName.contains("log") ||
                itemName.contains("stone") || itemName.contains("dirt") || itemName.contains("sand")) {
            return 16 + localRandom.nextInt(49);
        }

        // Materials
        if (itemName.contains("ingot") || itemName.contains("gem") || itemName.contains("nugget")) {
            return 4 + localRandom.nextInt(13);
        }

        // Food
        if (itemName.contains("bread") || itemName.contains("cooked") || itemName.contains("food")) {
            return 2 + localRandom.nextInt(11);
        }

        // Default
        int desiredAmount = 1 + localRandom.nextInt(8);
        return Math.min(desiredAmount, tempStack.getMaxStackSize());
    }

    public static String getItemName(Item item) {
        ResourceLocation location = BuiltInRegistries.ITEM.getKey(item);
        if (location != null) {
            String name = location.getPath();
            StringBuilder result = new StringBuilder();
            String[] parts = name.split("_");
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (!part.isEmpty()) {
                    result.append(Character.toUpperCase(part.charAt(0)));
                    if (part.length() > 1) {
                        result.append(part.substring(1));
                    }
                }
                if (i < parts.length - 1) {
                    result.append(" ");
                }
            }
            return result.toString();
        }
        return "Unknown Item";
    }
}