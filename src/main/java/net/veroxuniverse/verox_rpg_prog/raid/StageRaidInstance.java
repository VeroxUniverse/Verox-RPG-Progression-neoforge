package net.veroxuniverse.verox_rpg_prog.raid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.veroxuniverse.verox_rpg_prog.handler.MobEquipmentHandler;
import net.veroxuniverse.verox_rpg_prog.stage.StageDefinition;
import net.veroxuniverse.verox_rpg_prog.stage.StageRaid;
import net.veroxuniverse.verox_rpg_prog.util.MobScalingApplier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class StageRaidInstance {

    private enum State { PREPARING, FIGHTING, VICTORY, DEFEAT, DONE }

    private static final int WAVE_DELAY = 100;
    private static final int END_DELAY = 100;
    private static final int MAX_TICKS = 36000;
    private static final int MAX_TICKS_WITHOUT_PLAYERS = 600;
    private static final double PLAYER_RANGE = 96.0;
    private static final int MIN_SPAWN_DISTANCE = 18;
    private static final int SPAWN_DISTANCE_VARIANCE = 10;
    private static final int MAX_SURFACE_HEIGHT_DIFFERENCE = 20;
    private static final double NEARBY_TARGET_RANGE = 24.0;
    private static final int SPAWN_POSITION_ATTEMPTS = 24;
    private static final int MAX_SPAWN_RETRIES = 5;
    private static final int SPAWN_RETRY_DELAY = 40;
    private static final int MAX_UNDERGROUND_HEIGHT_DIFFERENCE = 4;

    private final StageRaid raid;
    private final StageDefinition stage;
    private final ServerLevel level;
    private final BlockPos center;
    private final UUID targetPlayer;
    private final ServerBossEvent bossEvent;
    private final boolean outdoors;
    private final Set<UUID> alive = new HashSet<>();

    private State state = State.PREPARING;
    private int waveIndex = -1;
    private int countdown = WAVE_DELAY;
    private int ticksActive;
    private int ticksWithoutPlayers;
    private float totalHealth;
    private int failedSpawnAttempts;
    private boolean spawnedAnyWave;

    public StageRaidInstance(StageRaid raid, StageDefinition stage, ServerLevel level, BlockPos center, UUID targetPlayer) {
        this.raid = raid;
        this.stage = stage;
        this.level = level;
        this.center = center;
        this.targetPlayer = targetPlayer;
        this.bossEvent = new ServerBossEvent(Component.translatable(raid.name()), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        this.bossEvent.setProgress(0.0F);
        this.outdoors = level.dimensionType().hasSkyLight()
                && !level.dimensionType().hasCeiling()
                && level.canSeeSky(center);
    }

    public UUID getTargetPlayer() {
        return this.targetPlayer;
    }

    public boolean isDone() {
        return this.state == State.DONE;
    }

    public void start() {
        this.level.playSound(null, this.center, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 64.0F, 1.0F);
        ServerPlayer target = this.getTarget();
        if (target != null) {
            target.displayClientMessage(Component.translatable("message.verox_rpg_prog.raid_start", Component.translatable(this.raid.name())), true);
        }
        this.updatePlayers();
    }

    public void tick() {
        if (this.state == State.DONE) return;
        this.ticksActive++;

        if (this.ticksActive % 20 == 0) {
            this.updatePlayers();
        }

        if (this.state == State.PREPARING || this.state == State.FIGHTING) {
            if (this.bossEvent.getPlayers().isEmpty()) {
                this.ticksWithoutPlayers++;
            } else {
                this.ticksWithoutPlayers = 0;
            }
            if (this.ticksWithoutPlayers > MAX_TICKS_WITHOUT_PLAYERS || this.ticksActive > MAX_TICKS) {
                this.finish(State.DEFEAT);
                return;
            }
        }

        switch (this.state) {
            case PREPARING -> this.tickPreparing();
            case FIGHTING -> this.tickFighting();
            case VICTORY, DEFEAT -> this.tickEnding();
            default -> {
            }
        }
    }

    public void stop() {
        this.bossEvent.removeAllPlayers();
        this.state = State.DONE;
    }

    private void tickPreparing() {
        this.countdown--;
        this.bossEvent.setProgress(Mth.clamp((WAVE_DELAY - this.countdown) / (float) WAVE_DELAY, 0.0F, 1.0F));
        if (this.countdown > 0) return;

        int next = this.nextPlayableWave(this.waveIndex + 1);
        if (next < 0) {
            if (this.spawnedAnyWave) {
                this.giveRewards();
                this.finish(State.VICTORY);
            } else {
                this.finish(State.DEFEAT);
            }
            return;
        }

        boolean lastWave = this.nextPlayableWave(next + 1) < 0;
        if (this.spawnWave(this.raid.waves().get(next), lastWave) == 0) {
            this.failedSpawnAttempts++;
            if (this.failedSpawnAttempts >= MAX_SPAWN_RETRIES) {
                this.finish(State.DEFEAT);
            } else {
                this.countdown = SPAWN_RETRY_DELAY;
            }
            return;
        }

        this.failedSpawnAttempts = 0;
        this.waveIndex = next;
        this.spawnedAnyWave = true;
        this.state = State.FIGHTING;
    }

    private int nextPlayableWave(int from) {
        int last = this.raid.waves().size() - 1;
        boolean leaderAvailable = this.raid.leader().isPresent() && this.raid.leader().get().isAvailable();
        for (int i = Math.max(0, from); i <= last; i++) {
            boolean hasMobs = this.raid.waves().get(i).mobs().stream().anyMatch(StageRaid.WaveMob::isAvailable);
            if (hasMobs || (i == last && leaderAvailable)) return i;
        }
        return -1;
    }

    private void tickFighting() {
        if (this.ticksActive % 20 == 0) {
            this.enforceTargets();
        }
        if (this.ticksActive % 10 != 0) return;

        float health = 0.0F;
        this.alive.removeIf(uuid -> {
            Entity entity = this.level.getEntity(uuid);
            return !(entity instanceof LivingEntity living) || !living.isAlive();
        });
        for (UUID uuid : this.alive) {
            if (this.level.getEntity(uuid) instanceof LivingEntity living) {
                health += living.getHealth();
            }
        }
        this.bossEvent.setProgress(this.totalHealth > 0.0F ? Mth.clamp(health / this.totalHealth, 0.0F, 1.0F) : 0.0F);

        if (!this.alive.isEmpty()) return;

        if (this.nextPlayableWave(this.waveIndex + 1) >= 0) {
            this.state = State.PREPARING;
            this.countdown = WAVE_DELAY;
        } else {
            this.giveRewards();
            this.finish(State.VICTORY);
        }
    }

    private void enforceTargets() {
        for (UUID uuid : this.alive) {
            if (!(this.level.getEntity(uuid) instanceof Mob mob)) continue;

            LivingEntity target = mob.getTarget() != null && this.isValidTarget(mob.getTarget())
                    ? mob.getTarget()
                    : this.findTargetFor(mob);
            if (target != null) {
                aimAt(mob, target);
            }
        }
    }

    private static void aimAt(Mob mob, LivingEntity target) {
        if (mob.getTarget() != target) {
            mob.setTarget(target);
        }
        if (mob instanceof NeutralMob neutral) {
            if (!target.getUUID().equals(neutral.getPersistentAngerTarget())) {
                neutral.setPersistentAngerTarget(target.getUUID());
            }
            neutral.startPersistentAngerTimer();
        }
    }

    private LivingEntity findTargetFor(Mob mob) {
        LivingEntity nearest = null;
        double nearestDistance = NEARBY_TARGET_RANGE * NEARBY_TARGET_RANGE;
        AABB area = mob.getBoundingBox().inflate(NEARBY_TARGET_RANGE);
        for (LivingEntity candidate : this.level.getEntitiesOfClass(LivingEntity.class, area, this::isValidTarget)) {
            double distance = candidate.distanceToSqr(mob);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        if (nearest != null) return nearest;

        ServerPlayer primary = this.getTarget();
        if (primary != null && this.isValidTarget(primary) && primary.distanceToSqr(mob) <= PLAYER_RANGE * PLAYER_RANGE) {
            return primary;
        }

        Player fallback = null;
        double fallbackDistance = PLAYER_RANGE * PLAYER_RANGE;
        for (ServerPlayer player : this.level.players()) {
            if (!this.isValidTarget(player)) continue;
            double distance = player.distanceToSqr(mob);
            if (distance < fallbackDistance) {
                fallback = player;
                fallbackDistance = distance;
            }
        }
        return fallback;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (!entity.isAlive() || this.alive.contains(entity.getUUID())) return false;
        if (entity instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null;
    }

    private void tickEnding() {
        this.countdown--;
        if (this.countdown <= 0) {
            this.stop();
        }
    }

    private void finish(State result) {
        this.state = result;
        this.countdown = END_DELAY;
        this.bossEvent.setProgress(result == State.VICTORY ? 0.0F : 1.0F);
        this.bossEvent.setName(Component.translatable(result == State.VICTORY
                ? "event.verox_rpg_prog.raid.victory"
                : "event.verox_rpg_prog.raid.defeat", Component.translatable(this.raid.name())));
    }

    private int spawnWave(StageRaid.Wave wave, boolean lastWave) {
        this.totalHealth = 0.0F;
        this.alive.clear();

        for (StageRaid.WaveMob waveMob : wave.mobs()) {
            if (!waveMob.isAvailable()) continue;
            for (int i = 0; i < waveMob.count(); i++) {
                Mob mob = this.spawnMob(waveMob.entity(), waveMob.equipment(), this.raid.mobScaling());
                if (mob != null) {
                    this.track(mob);
                }
            }
        }

        if (lastWave && this.raid.leader().isPresent() && this.raid.leader().get().isAvailable()) {
            StageRaid.RaidMob leader = this.raid.leader().get();
            Mob mob = this.spawnMob(leader.entity(), leader.equipment(), this.raid.mobScaling());
            if (mob != null) {
                MobScalingApplier.apply(mob, leader.scaling(), "raid_leader_scaling");
                mob.setHealth(mob.getMaxHealth());
                mob.setGlowingTag(true);
                this.track(mob);
            }
        }

        if (!this.alive.isEmpty() && this.spawnedAnyWave) {
            this.level.playSound(null, this.center, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 64.0F, 1.0F);
        }
        return this.alive.size();
    }

    private void track(Mob mob) {
        this.alive.add(mob.getUUID());
        this.totalHealth += mob.getHealth();
    }

    private Mob spawnMob(ResourceLocation entityId, List<StageDefinition.EquipmentEntry> equipment, StageDefinition.MobAttributeScaling scaling) {
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)) return null;

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityId);
        Entity created = type.create(this.level);
        if (!(created instanceof Mob mob)) {
            if (created != null) {
                created.discard();
            }
            return null;
        }

        BlockPos pos = this.findSpawnPos();
        if (pos == null) {
            mob.discard();
            return null;
        }

        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, this.level.getRandom().nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(this.level, this.level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        mob.setPersistenceRequired();
        RaidMobGuard.mark(mob);
        this.level.addFreshEntityWithPassengers(mob);

        for (StageDefinition.EquipmentEntry entry : equipment) {
            MobEquipmentHandler.applyEquipment(mob, this.level, entry);
        }
        MobScalingApplier.apply(mob, scaling, "raid_scaling");
        mob.setHealth(mob.getMaxHealth());

        LivingEntity target = this.findTargetFor(mob);
        if (target != null) {
            aimAt(mob, target);
        }
        return mob;
    }

    private BlockPos findSpawnPos() {
        for (int attempt = 0; attempt < SPAWN_POSITION_ATTEMPTS; attempt++) {
            double angle = this.level.getRandom().nextDouble() * Math.PI * 2.0;
            int distance = MIN_SPAWN_DISTANCE + this.level.getRandom().nextInt(SPAWN_DISTANCE_VARIANCE);
            int x = this.center.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = this.center.getZ() + Mth.floor(Math.sin(angle) * distance);

            BlockPos ground = this.findGround(x, z);
            if (ground != null) return ground;
        }
        return null;
    }

    private BlockPos findGround(int x, int z) {
        if (!this.level.hasChunkAt(new BlockPos(x, this.center.getY(), z))) return null;
        return this.outdoors ? this.findSurface(x, z) : this.findNearLevel(x, z);
    }

    private BlockPos findSurface(int x, int z) {
        int y = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - this.center.getY()) > MAX_SURFACE_HEIGHT_DIFFERENCE) return null;

        BlockPos pos = new BlockPos(x, y, z);
        return this.isStandable(pos) ? pos : null;
    }

    private BlockPos findNearLevel(int x, int z) {
        for (int offset = 0; offset <= MAX_UNDERGROUND_HEIGHT_DIFFERENCE; offset++) {
            BlockPos above = new BlockPos(x, this.center.getY() + offset, z);
            if (this.isStandable(above)) return above;
            if (offset == 0) continue;

            BlockPos below = new BlockPos(x, this.center.getY() - offset, z);
            if (this.isStandable(below)) return below;
        }
        return null;
    }

    private boolean isStandable(BlockPos pos) {
        BlockPos groundPos = pos.below();
        BlockState ground = this.level.getBlockState(groundPos);
        boolean solidGround = ground.isFaceSturdy(this.level, groundPos, Direction.UP)
                || ground.getBlock() instanceof SnowLayerBlock;
        return solidGround
                && ground.getFluidState().isEmpty()
                && this.isPassable(pos)
                && this.isPassable(pos.above());
    }

    private boolean isPassable(BlockPos pos) {
        BlockState state = this.level.getBlockState(pos);
        return state.getCollisionShape(this.level, pos).isEmpty()
                && state.getFluidState().isEmpty()
                && !state.is(Blocks.POWDER_SNOW);
    }

    private void giveRewards() {
        List<ServerPlayer> receivers = new ArrayList<>();
        for (ServerPlayer player : this.level.players()) {
            if (player.distanceToSqr(this.center.getX(), this.center.getY(), this.center.getZ()) <= PLAYER_RANGE * PLAYER_RANGE) {
                receivers.add(player);
            }
        }

        ItemStack locator = RaidRewards.createLocator(this.level, this.center, this.raid, this.stage);

        for (ServerPlayer player : receivers) {
            List<ItemStack> rewards = new ArrayList<>(RaidRewards.rollItems(this.raid, player.getRandom()));
            if (!locator.isEmpty()) {
                rewards.add(locator.copy());
            }
            for (ItemStack reward : rewards) {
                if (!player.getInventory().add(reward)) {
                    player.drop(reward, false);
                }
            }
        }
    }

    private void updatePlayers() {
        Set<ServerPlayer> current = new HashSet<>(this.bossEvent.getPlayers());
        for (ServerPlayer player : this.level.players()) {
            boolean inRange = player.isAlive()
                    && player.distanceToSqr(this.center.getX(), this.center.getY(), this.center.getZ()) <= PLAYER_RANGE * PLAYER_RANGE;
            if (inRange && !current.contains(player)) {
                this.bossEvent.addPlayer(player);
            } else if (!inRange && current.contains(player)) {
                this.bossEvent.removePlayer(player);
            }
        }
    }

    private ServerPlayer getTarget() {
        return this.level.getPlayerByUUID(this.targetPlayer) instanceof ServerPlayer player ? player : null;
    }
}