# Contributing

Thanks for your interest in improving DiscordRelay. Bug reports, suggestions, and pull requests are all welcome.

## Reporting issues

Open an issue with what you expected, what actually happened, your Hytale server version, and the relevant lines from the server log. For a Discord side problem, the bot permissions and the intents you have enabled help a lot.

## Building

You need a Java 25 JDK. The plugin compiles against the Hytale server API from Maven, so no game install is needed to build.

```
./gradlew shadowJar
```

The jar lands in `build/libs/`. If you have a Hytale server installed locally, `./gradlew runServer` builds it, copies it into `run/mods`, and launches that server for testing (point it at your install with `-Phytale_home=/path/to/Hytale` or the `HYTALE_HOME` env var).

## Pull requests

- For anything large, open an issue first so we can agree on the approach before you spend time on it.
- Keep each pull request focused on a single change.
- Make sure `./gradlew shadowJar` succeeds before opening the PR.
- Match the style of the surrounding code.

## License

By contributing you agree that your contributions are licensed under the MIT License, the same as the rest of the project.
