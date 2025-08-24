package com.randoola.lootpackages;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = LootPackagesMod.MODID, dist = Dist.CLIENT)
public class StarterPackModClient {

    public StarterPackModClient() {
        // Client-side initialization (does nothing for this mod)
    }

    @SubscribeEvent
    public void onClientSetup(FMLClientSetupEvent event) {
        LootPackagesMod.LOGGER.info("Starter Pack client setup complete");
    }
}