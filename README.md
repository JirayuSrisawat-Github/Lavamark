# Lavamark

Lavaplayer server benchmarker. How many lavaplayer tracks can your server handle playing at once?

[Download](../../releases)

## Usage

```
java -jar lavamark.jar
```

## Lavamark Options

You can specify any of the following options as a command-line argument when launching Lavamark.
Application options are specified after the JAR name.

| Option                      | Description                                                                                                           |
|-----------------------------|-----------------------------------------------------------------------------------------------------------------------|
| `-h`/`--help`               | Displays Lavamark's available options.                                                                                |
| `-i`/`--identifier`         | The identifier or URL of the track/playlist to use for the benchmark.                                                 |
| `-b`/`--block`              | The IPv6 block to use for rotation, specified as CIDR notation. Only applies to YouTube currently.                    |
| `-s`/`--step`               | The number of players to spawn after two seconds. Be careful when using large values.                                 |
| `-t`/`--transcode`          | Simulate a load by forcing transcoding.                                                                               |
| `-r`/`--resamplingQuality`  | Quality of resampling operations. Valid values are LOW, MEDIUM and HIGH, where HIGH uses the most CPU.                |
| `-o`/`--opusEncoderQuality` | Opus encoder quality. Valid values range from 0 to 10, where 10 is best quality but is the most expensive on the CPU. |