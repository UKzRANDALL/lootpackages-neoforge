package com.randoola.lootpackages;

import net.neoforged.neoforge.common.ModConfigSpec;

public class LootPackagesConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue GIVE_STARTER_PACKAGE;

    static {
        BUILDER.comment("Starter Pack Settings");

        GIVE_STARTER_PACKAGE = BUILDER
                .comment("Set to true to give new players a starter survival package when they join for the first time")
                .comment("Set to false to disable starter packages for new players")
                .define("giveStarterPackage", true);
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean shouldGiveStarterPackage() {
        return GIVE_STARTER_PACKAGE.get();
    }
}