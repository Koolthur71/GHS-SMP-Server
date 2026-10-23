package com.ghs.guns;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;

import java.util.concurrent.ThreadLocalRandom;

final class Effects {
    private Effects() {}

    static Color randomColor() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return Color.fromRGB(r.nextInt(256), r.nextInt(256), r.nextInt(256));
    }

    static void confetti(Location at, int amount) {
        for (int i = 0; i < amount; i++) {
            at.getWorld().spawnParticle(Particle.DUST, at, 2, 0.4, 0.4, 0.4, 0,
                    new Particle.DustOptions(randomColor(), 1.2f));
        }
    }
}
