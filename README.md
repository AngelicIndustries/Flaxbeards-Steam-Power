# Flaxbeard's Steam Power

Metal casting, steam machinery and upgradeable equipment for Minecraft 1.7.10.

<img src="images/logo.png" alt="Flaxbeard's Steam Power" width="400">

[![hub](images/badges/github.png)](https://github.com/AngelicIndustries/Flaxbeards-Steam-Power/releases)
[![cord](images/badges/cord.png)](https://discord.gg/xAWCqGrguG)
![forge](images/badges/forge.png)

<!-- Enable platform badges once this fork has its own project pages. -->

## Features

* Melt metals in crucibles, mix alloys and pour ingots, nuggets and plates into molds.
* Make brass and gilded iron equipment.
* Build boilers, steam networks and machinery.
* Upgrade steam-powered tools and exosuits.
* Customize casting recipes through MineTweaker.
* Configure the Rock Smasher bonus yield and optionally collect Thumper drops above the machine.

The machine settings are in `config/FlaxbeardsSteamPower.cfg`, under `Machines`. The Rock Smasher bonus multiplier defaults to 2 (range 1–64), with the existing 1-in-4 bonus chance. A multiplier of 1 disables the bonus. Thumper drops stay underground by default; the optional nearby-drop setting moves them above the machine and still requires item drops to be enabled.

## Dependencies

Install on both client and server, with Forge 10.13.4.1614. No other mods are required.

Integrations with other mods, including Tinkers' Construct, are optional.

## Building

```sh
./gradlew build
```

The build uses the GTNH toolchain and produces Java 8-compatible jars. Versions come from Git tags; use `VERSION=0.29.5-dev ./gradlew build` to select a local build version.

## Credits

* Flaxbeard and the [Esteemed Innovation team](https://github.com/Esteemed-Innovation/Esteemed-Innovation/tree/1.7) for the original mod.
* [H-QueerCoded](https://github.com/H-QueerCoded/Flaxbeards-Steam-Power-1.7.10-Patched) and [mist475](https://github.com/mist475/Flaxbeards-Steam-Power-1.7.10-Patched) for the patched 1.7.10 forks.
* The [upstream resources project](https://github.com/Esteemed-Innovation/Flaxbeards-Steam-Power-Resources/tree/852d1806f6d7d1a866f0b837dabc763fff6ba5d1) for the textures, sounds and translations, imported from that pinned revision.
* [SKCraft](https://github.com/SKCraft/Flaxbeards-Steam-Power) for the Rock Smasher multiplier and Thumper drop-location options, adapted for this fork.
* The Forge and MCP teams.
* The [GT:NH buildscript](https://github.com/GTNewHorizons/ExampleMod1.7.10).

## License

[LGPLv3](LICENSE), following the original 1.7.10 branch. Third-party notices are retained in [licenses](licenses).
