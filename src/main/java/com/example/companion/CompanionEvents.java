package com.example.companion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Game (Forge) bus handlers. The first time a player joins a given world, a
 * black-and-gold Bufo egg appears on the surface nearby for them to hatch.
 */
@Mod.EventBusSubscriber(modid = CompanionMod.MODID)
public final class CompanionEvents {
    private CompanionEvents() {}

    private static final String GRANTED_FLAG = "companion_bufo_granted";

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GRANTED_FLAG)) return;

        BlockPos spot = findEggSpot(level, player.blockPosition());
        if (spot == null) return; // no good spot found; try again next login

        persisted.putBoolean(GRANTED_FLAG, true);
        data.put(Player.PERSISTED_NBT_TAG, persisted);

        level.setBlockAndUpdate(spot, ModRegistry.BUFO_EGG.get().defaultBlockState());
        double cx = spot.getX() + 0.5, cy = spot.getY() + 0.5, cz = spot.getZ() + 0.5;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, cx, cy, cz, 40, 0.3, 0.6, 0.3, 0.3);

        player.displayClientMessage(Component.literal(
            "\u00A7eA strange black-and-gold egg has appeared nearby. Right-click it..."), false);
    }

    /** Find an open, sky-lit surface spot 6-10 blocks away with solid ground below. */
    private static BlockPos findEggSpot(ServerLevel level, BlockPos origin) {
        RandomSource rnd = level.random;
        for (int i = 0; i < 48; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            double dist = 6.0 + rnd.nextDouble() * 4.0; // 6-10 blocks
            int x = origin.getX() + (int) Math.round(Math.cos(ang) * dist);
            int z = origin.getZ() + (int) Math.round(Math.sin(ang) * dist);
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            BlockPos at = new BlockPos(x, y, z);
            BlockPos below = at.below();

            if (!level.getBlockState(at).isAir()) continue;
            if (!level.getFluidState(at).isEmpty()) continue;
            if (!level.canSeeSky(at)) continue;
            BlockState ground = level.getBlockState(below);
            if (!ground.isFaceSturdy(level, below, Direction.UP)) continue;
            return at;
        }
        return null;
    }
}
