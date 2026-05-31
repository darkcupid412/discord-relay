# Changelog

## 2.0.0

First release for Hytale server 0.5.x, a full rebuild succeeding 1.5.1.

### Features

- Two way chat relay with player names and avatars (auto created webhook).
- Join, leave, death, and server start/stop announcements, as text or embeds, with per event toggles and randomized death messages.
- `/players` in game and as a Discord slash command.
- Discord console relay, role gated and limited to a command allowlist.
- Account linking (`/link`, `/unlink`) with optional linked role and Discord nickname sync.
- Optional gates to require a linked account to chat from Discord and/or to play.
- Show a linked player's Discord name in game (world map, nameplate, and chat).
- Player stats: `/stats` and a paginated `/playtime` leaderboard with all time, today, 7 day, and 30 day ranges.
- AFK announcements, a single live status embed, and a startup update check.
- Two way ban sync and ban / unban broadcasts.
- Zone discovery broadcasts.
- Self documenting `config.yml`.

### Upgrading

- An existing 1.5.1 `config.yml` is converted automatically on first start and your settings are kept; the original is preserved as `config.yml.old`.
