# Space Exploration — Install Guide (Fabric mod, Minecraft 1.21.8)

This is a **Fabric mod**, so both the **server** and **every player** must install it. A plain
vanilla client can no longer join.

## Files you need
- **Fabric Loader** for Minecraft **1.21.8**
- **Fabric API** — version `0.136.1+1.21.8` (from Modrinth/CurseForge)
- **`spacemod-0.1.0.jar`** — this mod

---

## A. Server owner (Minefort)
1. In the Minefort dashboard, **stop** the server.
2. Change the **server software** to **Fabric** (version **1.21.8**).
   ⚠️ This removes your current Paper plugins (EliteMobs, LifeSteal, LuckPerms, etc.) — they do
   not run on Fabric.
3. Open **Files** (dashboard or FTP) and go to the **`mods`** folder (Fabric uses `mods`, not `plugins`).
4. Upload **both**: `fabric-api-0.136.1+1.21.8.jar` and `spacemod-0.1.0.jar`.
5. **Start** the server. Check the console log for `[Space Exploration] initialized`.

## B. Players (each person who joins)
Easiest with a modern launcher:

**Option 1 — Modrinth App (recommended)**
1. Install the free **Modrinth App**.
2. Create a new instance: **Minecraft 1.21.8**, loader **Fabric**.
3. Add **Fabric API** (1.21.8) to the instance.
4. Drop **`spacemod-0.1.0.jar`** into the instance's **mods** folder.
5. Launch and connect to the server.

**Option 2 — Vanilla launcher**
1. Download the **Fabric Installer** from fabricmc.net, run it, pick **1.21.8**, install.
2. In the official launcher, select the new **fabric-loader-1.21.8** profile.
3. Put **Fabric API** + **`spacemod-0.1.0.jar`** in `%appdata%\.minecraft\mods`.
4. Launch that profile and connect.

> The mod version, Fabric API version, and Minecraft version must match on server and client.

---

## Playing
- `/planet moon` (also `mars`, `europa`, `venus`, `earth`) — travel between dimensions.
- On a planet: wear a **Space Helmet** or your oxygen drains; gravity is real per-planet;
  Mars has dust hazards, Europa freezes you, Venus burns you without a **Thermal** suit.
- Craft at the **Fabricator** block; find Xylite/Titanium ore; `/summon spacemod:void_walker`.

## Honest status
Built + compiled clean for 1.21.8, but **not yet run in-game** (no client available during
development). Dimensions, custom entity rendering, and the crafting GUI are best-effort and may
need a small fix on first launch — report any startup error and it'll be patched.
