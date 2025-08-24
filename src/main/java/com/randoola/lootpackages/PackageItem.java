package com.randoola.lootpackages;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class PackageItem extends Item {

    private final LootPackagesMod.PackageType packageType;

    public PackageItem(LootPackagesMod.PackageType packageType) {
        super(new Item.Properties().stacksTo(16));
        this.packageType = packageType;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // Generate loot based on package type
            LootPackagesMod.generatePackageLoot(serverPlayer, packageType);

            // Send message to player about what package they opened
            String message = "§6Opened " + packageType.getDisplayName() + "!";
            serverPlayer.sendSystemMessage(Component.literal(message), false);

            // Consume the package
            itemStack.shrink(1);

            return InteractionResultHolder.success(itemStack);
        }

        return InteractionResultHolder.pass(itemStack);
    }

    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        switch (packageType) {
            case STARTER_SURVIVAL -> {
                tooltip.add(Component.literal("§7Basic tools, food, and supplies"));
                tooltip.add(Component.literal("§7Perfect for new adventurers"));
            }
            case DUNGEON_LOOT -> {
                tooltip.add(Component.literal("§7Enchanted gear and rare materials"));
                tooltip.add(Component.literal("§7Found in dark underground places"));
            }
            case TREASURE -> {
                tooltip.add(Component.literal("§6Valuable gems and precious items"));
                tooltip.add(Component.literal("§6Buried treasure and riches"));
            }
            case NETHER_LOOT -> {
                tooltip.add(Component.literal("§cFiery materials from the Nether"));
                tooltip.add(Component.literal("§cGold, magma, and flame-touched gear"));
            }
            case END_LOOT -> {
                tooltip.add(Component.literal("§5Mysterious items from the End"));
                tooltip.add(Component.literal("§5Ender pearls and otherworldly gear"));
            }
            case MOB_DROP -> {
                tooltip.add(Component.literal("§8Materials dropped by creatures"));
                tooltip.add(Component.literal("§8Bones, strings, and mob essences"));
            }
            case BIOME_SPECIFIC -> {
                tooltip.add(Component.literal("§2Resources from various biomes"));
                tooltip.add(Component.literal("§2Forest, desert, ocean, and more"));
            }
            case MYSTERY -> {
                tooltip.add(Component.literal("§d???"));
                tooltip.add(Component.literal("§dAnything could be inside!"));
            }
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§eRight-click to open"));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(packageType.getDisplayName());
    }
}