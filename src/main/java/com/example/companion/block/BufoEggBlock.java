package com.example.companion.block;

import com.example.companion.ModRegistry;
import com.example.companion.Pipeline;
import com.example.companion.entity.BufoEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Collections;
import java.util.List;

/** A black-and-gold egg that hatches Bufo when the player right-clicks it. */
public class BufoEggBlock extends Block {
    public BufoEggBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level instanceof ServerLevel server) hatch(server, pos, player);
        return InteractionResult.CONSUME;
    }

    private void hatch(ServerLevel level, BlockPos pos, Player player) {
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;

        level.removeBlock(pos, false);

        level.sendParticles(ParticleTypes.FLASH,            cx, cy, cz, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD,          cx, cy, cz, 140, 0.4, 0.6, 0.4, 0.09);
        level.sendParticles(ParticleTypes.DRAGON_BREATH,    cx, cy, cz, 90, 0.5, 0.25, 0.5, 0.02);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,   cx, cy, cz, 120, 0.35, 0.9, 0.35, 0.5);
        level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.playSound(null, pos, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.0f, 1.0f);

        BufoEntity bufo = ModRegistry.BUFO.get().create(level);
        if (bufo != null) {
            float yaw = player != null ? (float) faceYaw(cx, cz, player.getX(), player.getZ()) : 0f;
            bufo.moveTo(cx, cy + 0.3, cz, yaw, 0f);
            level.addFreshEntity(bufo);
        }

        Pipeline.announce("So\u2026 you've found me. I am Bufo. I have watched over this world since its first "
            + "sunrise, long before your kind walked it. Something about you caught my eye \u2014 so I'll walk with "
            + "you a while. Call on me whenever you like.");
    }

    private static double faceYaw(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX, dz = toZ - fromZ;
        return (Math.toDegrees(Math.atan2(-dx, dz)));
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return Collections.emptyList();
    }
}
