# Centuria Modules
This is the repository for the official [Centuria](https://github.com/CPeers1/Centuria) server modules.

Centuria is a work-in-progress server emulator for the now-defunct MMORPG Fer.al. The main server project can be found [here](https://github.com/CPeers1/Centuria). Centuria is developed by a group of developers from the Fer.ever discord. The software was originally released by the AerialWorks Software Foundation (SkySwimmer's small organization) but is now owned [Owenvii](https://github.com/CPeers1).

<br/>


# Building the modules
Each project is built with Gradle, you will need Java 17 on your device for this.

<br/>


## Building on Windows
On windows, run the following commands in cmd or powershell (inside a module subdirectory):

Set up a local server to build against:
```powershell
.\createlocalserver.bat
```

Set up a development environment (optional):
```powershell
.\gradlew eclipse createEclipseLaunches
```

Build the project:
```powershell
.\gradlew build
```

<br/>

## Building on Linux and OSX
On linux, in bash or your favorite shell, run the following commands in a module subdirectory: (note that this requires bash to be installed on OSX, most linux distros have bash pre-installed)

Configure permissions:
```bash
chmod +x createlocalserver.sh
chmod +x gradlew
```

Set up a local server to build against:
```bash
./createlocalserver.sh
```

Set up a development environment (optional):
```bash
./gradlew eclipse createEclipseLaunches
```

Build the project:
```bash
./gradlew build
```

<br/>

## Installing the modules on a Centuria server
After building, modules will be placed in `build/libs` (of the module subdirectory), simply copy the jar file into the `modules` folder of a Centuria server.

### Exception to this build directory
Apart from `centuria-discord`, all modules build in `build/libs`, however the Discord bot module has more dependencies. After building, you should copy the contents of `build/moduledata` to the server directory. This directory includes all dependencies of the module.

<br/>
<br/>


# Modules in the project

## Module centuria-discord
This is a Discord bot module designed to provide discord-based registration and a moderation system.

## Module play-as-npcs
This is a VERY BUGGY module designed to add a command to create avatars using NPC data. (**warning: this can break your centuria account**)

## Module peer-to-peer
This module is a very work-in-progress system designed to bring peer-to-peer play to Fer.al.

## Module gcs-for-feral
This module is a system designed to bring group chats to Fer.al via chat commands, **note: it is presently very buggy.**

<br/>

# Project licensing
Each module in this repository as well as the base server software is licensed under the GPLv2 license.
