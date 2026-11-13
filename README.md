# PrisonCore

An all-in-one prison plugin for Paper servers. One jar gives you:

- **Mines** – create them in-game with a wand, fill them with any mix of blocks, and they refill on a timer or once they are mined out.
- **Ranks A–Z** – 26 ranks ready to go with rising prices. Change, add or remove ranks freely.
- **Infinite prestige** – at rank Z players prestige back to A, as many times as they like, with prizes at prestige 1, 10, 50, 100, 500, 1,000, 5,000, 10,000 and beyond.
- **Economy** – built-in money with `/balance`, `/pay`, `/baltop` and `/eco`.
- **Selling** – `/sellall`, autosell, auto-pickup, auto-smelt and sell multipliers.
- **Scoreboard** – a live sidebar showing rank, prestige, balance, progress to the next rank and the mine you are in.
- **Permissions** – ranks hand out mine access and any extra permissions by themselves. No permissions plugin is required.

Works out of the box: drop it in, create your mines, done.

## Requirements

- **Paper 1.21 or newer** (Purpur and other Paper forks are fine), running Java 21+.
- Optional: [Vault](https://www.spigotmc.org/resources/vault.34315/) so other plugins (shops, crates…) can use PrisonCore's money.
- Optional: [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) to show ranks and prestige in other plugins.

## Install

1. Put `PrisonCore-1.0.0.jar` in your server's `plugins` folder.
2. Start the server. The config files appear in `plugins/PrisonCore/`.
3. Join as an operator and run **`/prison setup`**. It shows a checklist of what is left to do.

## Setting up in five minutes

Everything is already configured except the mines, because only you know where they go in your world.

For each mine:

1. `/prison wand` – gives you the mine wand.
2. **Left-click** one corner of the area and **right-click** the opposite corner.
   (No wand handy? Stand at each corner and use `/prison mine pos1` and `/prison mine pos2`.)
3. Stand where players should arrive, then run `/prison mine create A`.

The mine fills immediately. Because it is named after rank **A** it is automatically linked to that rank and
gets that rank's block mix. Repeat for `B`, `C` … `Z`, then run `/prison setup` again to confirm nothing is missing.

That's it – players can now `/mines`, mine, `/sellall`, `/rankup` and `/prestige`.

> New players receive a diamond pickaxe (Efficiency III, Unbreaking III) the first time they join.
> Change or remove it under `first-join` in `config.yml`.

## Player commands

| Command | What it does |
| --- | --- |
| `/rankup` | Buy the next rank |
| `/rankupmax` | Buy as many ranks as you can afford |
| `/ranks` | Menu of every rank and its price (click the next rank to buy it) |
| `/prestige` | Prestige once you are at the final rank |
| `/prestigemax` | Rank up and prestige over and over while the money lasts |
| `/prestiges` | List the prestige prizes and which ones you have earned |
| `/prestigetop` | Prestige leaderboard |
| `/mines` | Menu of every mine (click to teleport) |
| `/mine [name]` | Teleport to your best mine, or to a named one |
| `/sellall` (`/sell`) | Sell everything sellable in your inventory |
| `/autosell` | Toggle selling blocks the moment you mine them *(a perk – see permissions)* |
| `/multiplier` | Show your sell multiplier and where it comes from |
| `/balance [player]` (`/bal`, `/money`) | Check money |
| `/pay <player> <amount>` | Send money. Amounts accept `1500`, `1.5k`, `2m`, `3b` … |
| `/baltop` | Richest players |
| `/sb` | Show or hide your scoreboard |

## Admin commands

All of these need `prisoncore.admin` (operators have it). `/prison help` lists them in-game.

**General**

| Command | What it does |
| --- | --- |
| `/prison setup` | Checklist of what is set up and what is missing |
| `/prison reload` | Reload every config file |
| `/prison wand` | Get the mine selection wand |
| `/eco <give\|take\|set> <player> <amount>` | Change a player's money |

**Mines**

| Command | What it does |
| --- | --- |
| `/prison mine create <name>` | Create a mine from your selection |
| `/prison mine redefine <name>` | Move/resize a mine to your selection |
| `/prison mine setspawn <name>` | Set where players arrive |
| `/prison mine addblock <name> <block> <weight>` | Add a block or change its share |
| `/prison mine removeblock <name> <block>` | Remove a block |
| `/prison mine clearblocks <name>` | Remove every block |
| `/prison mine preset <name> <rank>` | Copy a rank's block mix from `config.yml` |
| `/prison mine settimer <name> <seconds\|off\|default>` | Timed refill |
| `/prison mine setthreshold <name> <percent\|off\|default>` | Refill once this much is mined |
| `/prison mine setrank <name> <rank\|none>` | Rank needed to use it |
| `/prison mine setprestige <name> <number>` | Prestige needed to use it |
| `/prison mine reset <name\|all>` | Refill now |
| `/prison mine tp <name>` · `info <name>` · `list` · `delete <name>` | |

**Ranks**

| Command | What it does |
| --- | --- |
| `/prison rank list` | Every rank and its price |
| `/prison rank setcost <rank> <cost>` | Change one price |
| `/prison rank autocost <firstCost> <multiplier>` | Re-price the whole ladder, e.g. `autocost 2500 1.5` |
| `/prison rank setprefix <rank> <prefix>` | Change the tag, e.g. `&7[&bK&7]` |
| `/prison rank create <name> <cost> [prefix]` | Add a rank at the end |
| `/prison rank delete <rank>` | Remove a rank |

**Players**

| Command | What it does |
| --- | --- |
| `/prison player info <player>` | Rank, prestige, balance, multiplier, blocks mined |
| `/prison player setrank <player> <rank>` | |
| `/prison player setprestige <player> <number>` · `addprestige` | |
| `/prison player setmultiplier <player> <bonus>` · `addmultiplier` | Permanent sell bonus (`0.5` = +0.5x) |
| `/prison player reset <player>` | Back to the first rank, prestige 0 |

**Sell prices**

| Command | What it does |
| --- | --- |
| `/prison price set <item\|hand> <price>` | Set what one item sells for |
| `/prison price remove <item\|hand>` | Make an item unsellable |
| `/prison price list [page]` | Show all prices |

## Permissions

You do not need a permissions plugin. Everyone gets the player commands, operators get the admin ones, and
ranks/prestige hand out the rest automatically. If you do use LuckPerms or similar, these are the nodes:

| Permission | Default | What it allows |
| --- | --- | --- |
| `prisoncore.admin` | op | Everything: `/prison`, `/eco`, building in mines, every mine |
| `prisoncore.player` | everyone | All the normal player commands below |
| `prisoncore.rankup` · `.rankupmax` · `.ranks` | everyone | `/rankup`, `/rankupmax`, `/ranks` |
| `prisoncore.prestige` · `.prestigemax` · `.prestiges` · `.prestigetop` | everyone | Prestige commands |
| `prisoncore.mines.teleport` · `prisoncore.mines.menu` | everyone | `/mine`, `/mines` |
| `prisoncore.sellall` · `prisoncore.multiplier` | everyone | `/sellall`, `/multiplier` |
| `prisoncore.balance` · `.balance.others` · `.pay` · `.baltop` | everyone | Money commands |
| `prisoncore.scoreboard` | everyone | `/sb` |
| `prisoncore.autosell` | op | `/autosell`. A perk: by default players unlock it at **prestige 10**. You can also give it to donors |
| `prisoncore.eco` | op | `/eco` |
| `prisoncore.build` | op | Place blocks inside mines and build in protected worlds |
| `prisoncore.mine.*` | op | Use every mine regardless of rank |
| `prisoncore.mine.<name>` | automatic | Use one mine. Given automatically by rank/prestige; grant it yourself for donor mines |
| `prisoncore.rank.<name>` | automatic | Held by players at that rank or higher – handy for other plugins |
| `prisoncore.multiplier.<number>` | – | Sell multiplier perk, e.g. `prisoncore.multiplier.2` doubles sales. The highest one counts |

**Given automatically:** a player always has `prisoncore.rank.<rank>` for their rank and every rank below it,
`prisoncore.mine.<mine>` for every mine their rank and prestige unlock, any `permissions:` you list on a rank in
`ranks.yml`, and any `permissions:` on prestige milestones they have reached.

## How progression works

### Ranks (`ranks.yml`)

Players start at **A** and buy each rank in order. The default prices start at $2,500 for B and rise 1.5x per
rank up to $42M for Z (about $126M for the whole ladder). Each rank can also list extra `permissions` and
console `commands` to run when a player reaches it.

### Prestige (`prestige.yml`)

At the final rank, `/prestige` (fee: $50M by default) sends the player back to rank A with prestige +1.
There is no limit unless you set `max-prestige`.

Each prestige:

- makes rank-ups **10% more expensive** (`rank-cost.increase-per-prestige`) – prestige 10 pays double, prestige 100 pays 11x;
- makes the player earn **5% more** from selling (`sell.multiplier-per-prestige` in `config.yml`);
- wipes their balance (`reset-balance`), then pays any prize.

Because both grow in a straight line, prestiging never becomes impossible: in the long run each prestige takes
about `rank cost increase ÷ sell bonus` = 0.10 ÷ 0.05 = **2x** as long as the very first one. Raise the sell
bonus to make it easier, raise the cost increase to make it harder.

### Prestige prizes

Prizes are given the moment a player reaches a prestige. The defaults:

| Prestige | Prize |
| --- | --- |
| 1 | $50K, +0.1x sell multiplier, server announcement |
| 10 | $500K, +0.25x, unlocks `/autosell` |
| 50 | $5M, +0.5x, 16 diamond blocks |
| 100 | $25M, +1x |
| 500 | $250M, +2.5x, 4 netherite ingots |
| 1,000 | $1B, +5x |
| 5,000 | $10B, +10x |
| 10,000 | $50B, +25x |
| 50,000 | $500B, +50x |
| 100,000 | $5T, +100x |
| every 25 | +0.05x sell multiplier, forever |

Add your own under `milestones:` (one-off) or `repeating:` (every N prestiges). A prize can be any mix of
`money`, `multiplier`, `permissions`, console `commands` (crate keys, items, anything), a `broadcast` and a `message`.

## Mines

- A mine named after a rank is linked to it: players of that rank **or higher** can use it. After prestiging,
  players are back at rank A and unlock the mines again as they rank up.
- Any other mine is private until you link it (`/prison mine setrank vip A` opens it to everyone,
  `setprestige` makes it a prestige mine) or grant `prisoncore.mine.<name>`.
- Mines refill every 10 minutes if someone has mined in them, and straight away once 70% has been mined.
  Change the defaults in `config.yml` or per mine with `settimer` / `setthreshold`.
- Players inside are moved to the mine's spawn when it refills. Big mines refill over several ticks so the
  server never freezes; many mines resetting together take turns.
- Mined blocks go straight to the inventory (`auto-pickup`), raw ores are smelted (`auto-smelt`) and Fortune
  works on every block (`fortune-all-blocks`). All three can be switched off in `config.yml`.
- Players without `prisoncore.build` cannot place blocks in mines. To also stop them breaking blocks
  *outside* mines, list your world under `protection.worlds` in `config.yml` (or use WorldGuard).

The 26 default block mixes are under `mines.presets` in `config.yml` and step up from stone and coal in A to
quartz, diamond and emerald blocks in Z.

## Selling and multipliers

Prices live in `prices.yml` (or use `/prison price`). A player's multiplier is:

```
(1 + prestige bonus + reward bonus + permission bonus) × server boost
```

- **Prestige bonus** – +0.05 per prestige by default.
- **Reward bonus** – from prestige prizes or `/prison player addmultiplier`.
- **Permission bonus** – from `prisoncore.multiplier.<number>` (e.g. `…multiplier.1.5` = +0.5).
- **Server boost** – `sell.global-multiplier` in `config.yml`; set it to `2.0` for a double-money event.

Only plain items are sold. Anything renamed, enchanted or damaged stays in the inventory.

The default prices are balanced so each mine pays about a third more than the last, and so crafting can never
create money (nine diamonds are worth exactly one diamond block).

## Scoreboard, chat and placeholders

The sidebar is configured under `scoreboard` in `config.yml` (title, up to 15 lines, refresh rate). Chat gets the
prestige and rank tags in front of names; change the format or turn it off under `chat`.

Placeholders for the scoreboard and chat format:

| Placeholder | Shows |
| --- | --- |
| `%player%` | Player name |
| `%rank%` · `%rank_prefix%` | Rank name / coloured tag |
| `%next_rank%` · `%next_rank_prefix%` | Next rank, or "Prestige" at the final rank |
| `%prestige%` · `%prestige_prefix%` | Prestige number / coloured tag |
| `%prestige_cost%` · `%next_milestone%` | Fee for the next prestige / next prize prestige |
| `%balance%` · `%balance_full%` | `$1.5M` / `$1,500,000` |
| `%rankup_cost%` · `%rankup_needed%` | Price of the next goal / how much is still missing |
| `%rankup_progress%` · `%rankup_bar%` | Progress as a number 0–100 / as a bar |
| `%multiplier%` | Sell multiplier |
| `%blocks_mined%` · `%blocks_mined_full%` | Blocks mined |
| `%autosell%` | On / Off |
| `%mine%` · `%mine_remaining%` · `%mine_reset%` | Mine you are in, blocks left, time to refill |
| `%online%` · `%max_players%` | Player counts |

With **PlaceholderAPI** installed the same values are available to every other plugin as
`%prisoncore_<name>%` – for example `%prisoncore_rank%` or `%prisoncore_prestige%` – and PlaceholderAPI
placeholders can be used in PrisonCore's scoreboard and chat format.

## Economy and other plugins

By default PrisonCore keeps the money itself (`economy.provider: internal`). If Vault is installed it also
shares that money with every other Vault plugin.

Already running an economy plugin such as EssentialsX? Set `economy.provider: vault` and PrisonCore will use
*that* plugin's money for rank-ups, prestige, selling and prizes instead, so players only ever have one balance.
(PrisonCore warns you in the console if it spots a second economy.)

## Files

| File | What is in it |
| --- | --- |
| `config.yml` | Economy, starter kit, mine defaults and block presets, sell bonuses, scoreboard, chat, protection |
| `ranks.yml` | The rank ladder: prices, tags, extra permissions and commands |
| `prestige.yml` | Prestige fee, price scaling and all prizes |
| `prices.yml` | What each item sells for |
| `messages.yml` | Every message players see |
| `mines.yml` | Your mines (written by the plugin, safe to edit by hand) |
| `players.db` | Player data (rank, prestige, balance…) |

After editing a file run `/prison reload`.

## Building from source

Requires Java 21 and Maven.

```
mvn package
```

The jar is written to `target/PrisonCore-1.0.0.jar`. The build also runs the test suite (about 100 tests) on a
simulated server using [MockBukkit](https://github.com/MockBukkit/MockBukkit); it covers ranks, prestige and
prizes, mines and resets, selling, the economy, Vault, the scoreboard, menus, permissions and saving.
