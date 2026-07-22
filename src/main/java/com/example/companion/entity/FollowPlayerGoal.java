package com.example.companion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

/**
 * Bufo walks to the nearest player when they get too far and stops when close.
 * If he falls a long way behind (sprinting, boats, etc.) he teleports to catch up.
 */
public class FollowPlayerGoal extends Goal {
    private final BufoEntity bufo;
    private final PathNavigation nav;
    private final double speed;
    private final float startDist;
    private final float stopDist;
    private static final float TELEPORT_DIST = 18.0f;
    private Player target;
    private int recalc;

    public FollowPlayerGoal(BufoEntity bufo, double speed, float startDist, float stopDist) {
        this.bufo = bufo;
        this.nav = bufo.getNavigation();
        this.speed = speed;
        this.startDist = startDist;
        this.stopDist = stopDist;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Player p = bufo.level().getNearestPlayer(bufo, 32.0);
        if (p == null || p.isSpectator()) return false;
        if (bufo.distanceToSqr(p) < (double) (startDist * startDist)) return false;
        this.target = p;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null || !target.isAlive() || target.isSpectator()) return false;
        return bufo.distanceToSqr(target) > (double) (stopDist * stopDist);
    }

    @Override
    public void start() {
        this.recalc = 0;
    }

    @Override
    public void stop() {
        this.target = null;
        this.nav.stop();
    }

    @Override
    public void tick() {
        if (target == null) return;
        bufo.getLookControl().setLookAt(target, 10.0f, (float) bufo.getMaxHeadXRot());

        if (bufo.distanceToSqr(target) >= (double) (TELEPORT_DIST * TELEPORT_DIST) && tryTeleport()) {
            return;
        }
        if (--this.recalc <= 0) {
            this.recalc = 10;
            this.nav.moveTo(target, this.speed);
        }
    }

    private boolean tryTeleport() {
        BlockPos base = target.blockPosition();
        for (int i = 0; i < 10; i++) {
            int dx = randRange(-2, 2);
            int dy = randRange(-1, 1);
            int dz = randRange(-2, 2);
            BlockPos pos = base.offset(dx, dy, dz);
            if (canStandAt(pos)) {
                bufo.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, bufo.getYRot(), bufo.getXRot());
                this.nav.stop();
                return true;
            }
        }
        return false;
    }

    private int randRange(int min, int max) {
        return min + bufo.getRandom().nextInt(max - min + 1);
    }

    private boolean canStandAt(BlockPos pos) {
        Level lvl = bufo.level();
        boolean supported = !lvl.getBlockState(pos.below()).getCollisionShape(lvl, pos.below()).isEmpty()
            || !lvl.getFluidState(pos.below()).isEmpty();
        boolean feetClear = lvl.getBlockState(pos).getCollisionShape(lvl, pos).isEmpty();
        boolean headClear = lvl.getBlockState(pos.above()).getCollisionShape(lvl, pos.above()).isEmpty();
        return supported && feetClear && headClear;
    }
}
