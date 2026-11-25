package dev.javaroot.antixray.sess;

import dev.javaroot.antixray.db.mdl.Profile;
import dev.javaroot.antixray.sess.state.BrokenMem;
import dev.javaroot.antixray.sess.state.SusState;
import dev.javaroot.antixray.sess.state.TrapState;
import dev.javaroot.antixray.sess.track.MineTrack;
import dev.javaroot.antixray.sess.track.RotTrack;
import org.bukkit.GameMode;

import java.util.UUID;

public final class Sess {

    private final UUID uuid;
    private volatile String user;
    private volatile String curWorld;
    private volatile GameMode gameMode = GameMode.SURVIVAL;

    private final SusState sus = new SusState();
    private final TrapState traps = new TrapState();
    private final BrokenMem broken = new BrokenMem();
    private final MineTrack mine = new MineTrack();
    private final RotTrack rot = new RotTrack();

    public Sess(UUID uuid, String user) {
        this.uuid = uuid;
        this.user = user;
    }

    public UUID uuid() { return uuid; }
    public String user() { return user; }
    public void setUser(String user) { this.user = user; }
    public String curWorld() { return curWorld; }
    public GameMode gameMode() { return gameMode; }

    public void setGameMode(GameMode gameMode) {
        if (gameMode != null) {
            this.gameMode = gameMode;
        }
    }

    public SusState sus() { return sus; }
    public TrapState traps() { return traps; }
    public BrokenMem broken() { return broken; }
    public MineTrack mine() { return mine; }
    public RotTrack rot() { return rot; }

    public void restore(Profile prof) {
        if (prof == null) {
            return;
        }
        sus.merge(prof.susScore(), prof.watchUntilMs());
        traps.merge(prof.totalPunishments(), prof.phantomHits(), prof.lastPhantomHitMs());
    }

    public synchronized void markWorld(String wName) {
        if (wName == null || wName.equals(curWorld)) {
            return;
        }
        curWorld = wName;
        broken.clear();
        mine.resetWorld();
        rot.reset();
    }

    public synchronized void resetAll() {
        sus.reset();
        traps.resetSess();
        broken.clear();
        mine.resetAll();
        rot.reset();
    }
}
