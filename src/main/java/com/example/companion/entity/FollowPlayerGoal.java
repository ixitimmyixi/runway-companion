package com.example.companion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

public class FollowPlayerGoal extends Goal {
    private final BufoEntity bufo;
    private final PathNavigation nav;
    private final double speed;
    private final float startDist;
    private final float stopDist;
    private static final float TELEPORT_DIST = 12.0f;
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
            this.nav.moveTo(target.getX(), target.getEyeY() + 0.4, target.getZ(), this.speed);
        }
    }

    private boolean tryTeleport() {
        for (int i = 0; i < 10; i++) {
            double x = target.getX() + randD(-1.5, 1.5);
            double y = target.getEyeY() + randD(0.0, 1.5);
            double z = target.getZ() + randD(-1.5, 1.5);
            if (isClear(BlockPos.containing(x, y, z))) {
                bufo.moveTo(x, y, z, bufo.getYRot(), bufo.getXRot());
                this.nav.stop();
                return true;
            }
        }
        return false;
    }

    private double randD(double min, double max) {
        return min + bufo.getRandom().nextDouble() * (max - min);
    }

    private boolean isClear(BlockPos pos) {
        Level lvl = bufo.level();
        return lvl.getBlockState(pos).getCollisionShape(lvl, pos).isEmpty();
    }
}
