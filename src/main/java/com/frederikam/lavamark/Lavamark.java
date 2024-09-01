/*
 * MIT License
 *
 * Copyright (c) 2017 Frederik Ar. Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.frederikam.lavamark;

import com.sedmelluq.discord.lavaplayer.natives.ConnectorNativeLibLoader;
import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.*;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Lavamark {

    private static final long INTERVAL = 2 * 1000;
    private static final long STEP = 20;
    private static final Object WAITER = new Object();

    private static List<AudioTrack> tracks;
    private static final CopyOnWriteArrayList<Player> players = new CopyOnWriteArrayList<>();

    private static final String DEFAULT_OPUS = "https://soundcloud.com/r2rmoe/r2r-moe-9-lives";
    static final AudioPlayerManager PLAYER_MANAGER = new DefaultAudioPlayerManager();

    private static final Logger log = LoggerFactory.getLogger(Lavamark.class);

    public static void main(String[] args) {
        /* Soundcloud */
        DefaultSoundCloudDataReader dataReader = new DefaultSoundCloudDataReader();
        DefaultSoundCloudDataLoader dataLoader = new DefaultSoundCloudDataLoader();
        DefaultSoundCloudFormatHandler formatHandler = new DefaultSoundCloudFormatHandler();
        DefaultSoundCloudPlaylistLoader playlistLoader = new DefaultSoundCloudPlaylistLoader(dataLoader,dataReader,formatHandler);

        /* Set up the player manager */
        PLAYER_MANAGER.enableGcMonitoring();
        PLAYER_MANAGER.setItemLoaderThreadPoolSize(100);
        PLAYER_MANAGER.registerSourceManager(new SoundCloudAudioSourceManager(true, dataReader, dataLoader, formatHandler, playlistLoader));
        AudioSourceManagers.registerRemoteSources(PLAYER_MANAGER, com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager.class);

        String jarPath = Lavamark.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        String jarName = jarPath.substring(jarPath.lastIndexOf("/") + 1);

        Options options = new Options()
                .addOption("s", "step", true, "The number of players to spawn after two seconds. Be careful when using large values.")
                .addOption("i", "identifier", true, "The identifier or URL of the track/playlist to use for the benchmark.")
                .addOption("t", "transcode", false, "Simulate a load by forcing transcoding.")
                .addOption("r", "resamplingQuality", true, "Quality of resampling operations. Valid values are LOW, MEDIUM and HIGH, where HIGH uses the most CPU.")
                .addOption("o", "opusEncoderQuality", true, "Opus encoder quality. Valid values range from 0 to 10, where 10 is best quality but is the most expensive on the CPU.")
                .addOption("h", "help", false, "Displays Lavamark's available options.");

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();

        CommandLine parsed;

        try {
            parsed = parser.parse(options, args);
        } catch (ParseException parseException) {
            formatter.printHelp("java -jar " + jarName, options);
            return;
        }

        if (parsed.hasOption("help")) {
            formatter.printHelp("java -jar " + jarName, options);
            return;
        }

        if (parsed.hasOption("resamplingQuality")) {
            String value = parsed.getOptionValue("resamplingQuality");

            if (value.equals("HIGH")) {
                PLAYER_MANAGER.getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.HIGH);
            } else if (value.equals("MEDIUM")) {
                PLAYER_MANAGER.getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.MEDIUM);
            } else {
                PLAYER_MANAGER.getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.LOW);
            }
        }

        if (parsed.hasOption("opusEncodingQuality")) {
            String value = (parsed.getOptionValue("opusEncodingQuality"));

            if (isNumeric(value)) {
                int opusEncodingQuality = Integer.parseInt(value);

                if (opusEncodingQuality < 0 || opusEncodingQuality > 10) {
                    PLAYER_MANAGER.getConfiguration().setOpusEncodingQuality(0);
                } else {
                    PLAYER_MANAGER.getConfiguration().setOpusEncodingQuality(opusEncodingQuality);
                }
            }
        }

        boolean transcode = parsed.hasOption("transcode");

        if (transcode) {
            ConnectorNativeLibLoader.loadConnectorLibrary();
        }

        String identifier = parsed.getOptionValue("identifier", DEFAULT_OPUS);

        log.info("Loading AudioTracks from identifier {}", identifier);

        tracks = new PlaylistLoader().loadTracksSync(identifier);
        log.info("{} tracks loaded. Beginning benchmark...", tracks.size());

        long stepSize = STEP;

        if (parsed.hasOption("step")) {
            stepSize = Math.max(1, Long.parseLong(parsed.getOptionValue("step")));
            log.info("Step set to {} players every {} milliseconds.", stepSize, INTERVAL);
        }

        try {
            doLoop(stepSize, transcode);
        } catch (Exception e) {
            log.error("Benchmark ended due to exception!");
            throw new RuntimeException(e);
        }


        System.exit(0);
    }

    private static void doLoop(long stepSize, boolean transcode) throws InterruptedException {
        while (true) {
            spawnPlayers(stepSize, transcode);

            AudioConsumer.Results results = AudioConsumer.getResults();
            log.info("Players: {}, Null frames: {}, Served: {}, Missed: {}", players.size(), results.getLoss() + "%", results.getServed(), results.getMissed());

            if (results.getEndReason() != AudioConsumer.EndReason.NONE) {
                log.info("Benchmark ended. Reason: {}", results.getEndReason());
                break;
            }

            synchronized (WAITER) {
                WAITER.wait(INTERVAL);
            }
        }
    }

    private static void spawnPlayers(long stepSize, boolean transcode) {
        for (int i = 0; i < stepSize; i++) {
            players.add(new Player(transcode));
        }
    }

    static AudioTrack getTrack() {
        int rand = (int) (Math.random() * tracks.size());
        return tracks.get(rand).makeClone();
    }

    public static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
