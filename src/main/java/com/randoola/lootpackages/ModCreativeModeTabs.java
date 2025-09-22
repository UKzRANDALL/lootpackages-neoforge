package com.randoola.lootpackages;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LootPackagesMod.MODID);

    public static final Supplier<CreativeModeTab> LOOT_PACKAGES_TAB = CREATIVE_MODE_TABS.register("loot_packages_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.lootpackages.loot_packages"))
                    .icon(() -> new ItemStack(LootPackagesMod.STARTER_SURVIVAL_PACKAGE.get()))
                    .displayItems((parameters, output) -> {
                        // Add all your loot packages here
                        output.accept(LootPackagesMod.STARTER_SURVIVAL_PACKAGE.get());
                        output.accept(LootPackagesMod.DUNGEON_LOOT_PACKAGE.get());
                        output.accept(LootPackagesMod.TREASURE_PACKAGE.get());
                        output.accept(LootPackagesMod.NETHER_LOOT_PACKAGE.get());
                        output.accept(LootPackagesMod.END_LOOT_PACKAGE.get());
                        output.accept(LootPackagesMod.MOB_DROP_PACKAGE.get());
                        output.accept(LootPackagesMod.BIOME_PACKAGE.get());
                        output.accept(LootPackagesMod.MYSTERY_PACKAGE.get());
                        output.accept(LootPackagesMod.DEEP_DARK_PACKAGE.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}