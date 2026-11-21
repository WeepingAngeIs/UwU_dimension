package dev.uwu.guardiancheck;

import static dev.uwu.guardiancheck.GuardianArena.require;

import dev.uwu.dimension.Keeper;
import dev.uwu.dimension.People;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;

final class KeeperVoiceProbe {
    private final List<SoundEvent> heard = new ArrayList<>();
    @Nullable private Keeper listening;

    KeeperVoiceProbe() {
        NeoForge.EVENT_BUS.addListener(this::capture);
    }

    void verify(ServerLevel level, BlockPos origin) {
        verify(level, origin, People.PETAL_KEEPER.get().create(level), People.PETAL_VOICE.get(), People.PETAL_HURT.get());
        verify(level, origin, People.DUSK_KEEPER.get().create(level), People.DUSK_VOICE.get(), People.DUSK_HURT.get());
        System.out.println("KEEPER HURT ROUTING PASS one custom sound on actual damage per variant, with ambient/trade/death preserved");
    }

    private void verify(ServerLevel level, BlockPos origin, @Nullable Keeper keeper, SoundEvent voice, SoundEvent hurt) {
        require(keeper != null, "Could not create a Keeper for the hurt sound check");
        Vec3 at = Vec3.atBottomCenterOf(origin);
        keeper.moveTo(at.x, at.y, at.z, 0, 0);
        keeper.setNoAi(true);
        require(level.addFreshEntity(keeper), "Could not add the sound probe Keeper");
        listening = keeper;
        try {
            require(keeper.hurt(level.damageSources().generic(), 1.0F), "Keeper rejected the nonlethal probe hit");
            expectOnly(hurt, "hurt");
            keeper.playAmbientSound();
            expectOnly(voice, "ambient");
            require(keeper.getNotifyTradeSound() == voice, "Keeper trade voice changed");
            keeper.invulnerableTime = 0;
            require(keeper.hurt(level.damageSources().generic(), keeper.getMaxHealth() * 2), "Keeper rejected the lethal probe hit");
            expectOnly(SoundEvents.VILLAGER_DEATH, "death");
        } finally {
            listening = null;
            heard.clear();
            keeper.discard();
        }
    }

    private void capture(PlayLevelSoundEvent.AtPosition event) {
        Keeper keeper = listening;
        if (keeper != null && event.getLevel() == keeper.level() && event.getPosition().distanceToSqr(keeper.position()) < .001
                && !event.isCanceled() && event.getSound() != null) {
            heard.add(event.getSound().value());
        }
    }

    private void expectOnly(SoundEvent sound, String phase) {
        require(heard.size() == 1 && heard.getFirst() == sound,
                "Expected only " + sound.getLocation() + " on " + phase + ", heard "
                        + heard.stream().map(SoundEvent::getLocation).toList());
        heard.clear();
    }
}
