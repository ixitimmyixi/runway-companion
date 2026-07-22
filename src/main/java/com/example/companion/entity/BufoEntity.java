package com.example.companion.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import org.jetbrains.annotations.Nullable;

/**
 * Bufo the companion: vanilla frog looks, custom always-follow behavior,
 * amphibious so he can follow the player into water.
 */
public class BufoEntity extends Animal {
    public BufoEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
        // Don't avoid water; let him path into and through it.
        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER_BORDER, 0.0F);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 10.0)
            .add(Attributes.MOVEMENT_SPEED, 0.5)
            .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // speed, start-following-when-farther-than, stop-when-within
        this.goalSelector.addGoal(1, new FollowPlayerGoal(this, 1.4, 2.5f, 1.5f));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 6.0f));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        // Client-only: puff note particles above Bufo while he's speaking.
        if (level().isClientSide
                && com.example.companion.client.BufoSpeakingState.isSpeaking()
                && this.random.nextInt(4) == 0) {
            double px = getX() + (this.random.nextDouble() - 0.5) * 0.4;
            double py = getY() + 0.8;
            double pz = getZ() + (this.random.nextDouble() - 0.5) * 0.4;
            level().addParticle(ParticleTypes.NOTE, px, py, pz, this.random.nextDouble(), 0.0, 0.0);
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }
}
