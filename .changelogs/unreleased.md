* Update the GTNH build tooling and add Angelic Industries publishing configuration.
* Include the pinned upstream resources directly so a normal clone contains the textures, sounds and translations.
* Add the shared development environment, with Tinkers' Construct remaining optional and absent from dev runs.
* Update project metadata and include the mod license and third-party notices in built jars.
* Avoid loading the client merchant screen class during dedicated-server initialization.
* Replace the checked-in optional-mod JARs with pinned Maven dependencies.
* Use Baubles Expanded 2.2.26-GTNH for compilation and client/server development, while keeping Baubles support optional.
* Fix crucible save loading, alloy bookkeeping and server-side casting synchronization.
* Save mold inventory and lid changes reliably, clear stale client output and handle empty extraction safely.
* Reuse existing textures for the hell crucible, smasher and steamed foods to remove missing-texture warnings.
* Process client requests on the server tick and validate reach, loaded chunks, permissions, held items and the active steam-hammer container.
* Bound pending client requests and reject invalid pipe sides and item names.
* Save camouflage and pipe-connection changes, and clear inventory slots when their last camouflage block is consumed.
* Preserve the steam hammer's requested item name across saves and synchronize it to clients.
* Run crucible dunking and tipping on the server, fix wildcard dunking recipes, and save liquid changes immediately.
* Consume steam for crucible heating only when an item can actually melt.
* Respect edit permissions when wrenching, preserve tile interaction results, and save changed machine settings.
* Allow steam hammers to use anvil subclasses, including Et Futurum's replacement, with normal anvil wear.
* Synchronize hammer name fields between open screens, allow clearing names, and prevent slot updates from sending unintended renames.
* Preserve completed and paused hammer progress on clients, and save in-progress strokes without charging steam twice after reload.
* Keep hammer result batches intact during right-click, drop-key and shift-click pickup, including nearly full inventories.
* Use current repair-material counts when multiple players share a hammer, and reset work when its recipe or requested name changes.
* Prevent sided automation from extracting hammer result previews, and invalidate previews when inputs change outside the GUI.
* Detach unloaded chunks from steam networks and separate the remaining connected sections, preserving steam when chunks return.
* Save current steam shares without losing integer remainders, and merge live network totals instead of stale tile snapshots.
* Avoid loading neighboring chunks during steam-network, pipe, valve and gauge checks.
* Tick steam networks once per server tick, handle network removal safely, and clear network references when worlds unload or the server stops.
* Preserve exact steam balances when splitting and reconnecting networks, and retire obsolete network membership immediately.
* Prevent machine rotation from adding steam twice, and make repeated network joins safe.
* Save valve changes immediately, respect the handle-side connection, and fix redstone-source placement and server-authoritative valve operation.
* Detach broken transporters without rejoining them, vent only their stored share, and handle missing tiles safely.
* Correct steam-converter fluid faces and simulation results, reject invalid transfers, and prevent integer overflow when filling steam networks.
* Debit converter output only once, honor the requested steam fluid, and expose its shared storage as one tank.
* Run automatic converter output only on the server, avoid unloaded-neighbor lookups, and animate and save successful transfers.
* Preserve the converter's final mB during fluid extraction and save wrench mode changes immediately.
* Save full flash-boiler water tanks and long fuel burn times without 16-bit truncation, while retaining support for older numeric save tags.
* Validate boiler water input and save tank, inventory, fuel countdown and flash-boiler heat changes immediately.
* Fill boilers and consume fuel on the server, and prevent flash boilers from spending water without a steam network.
* Preserve buckets when their full contents do not fit, transfer only accepted amounts from refillable containers, and keep creative containers intact.
* Return empty containers correctly when filling from held stacks, and safely handle flash-boiler input when its primary block is unavailable.
* Synchronize complete boiler water and fuel values to every open screen without 16-bit truncation, including initial state and empty values.
* Use the flash boiler's actual fuel counters and primary inventory when opening its screen from any formed block, and close access when the structure changes.
* Validate boiler GUI inputs, limit the water slot to one container, and fix shift-click ranges across the main inventory and hotbar.
* Avoid overflow when drawing long fuel burn times and leave authoritative fuel state unchanged while rendering.
* Connect all eight flash-boiler members to the output network and preserve exact steam shares through disassembly and reformation.
* Keep flash-boiler capacity stable across chunk unloads, pause water and fuel consumption until the whole structure is loaded, and invalidate broken structures when missing members return.
* Avoid loading chunks during flash-boiler formation, disassembly and explosion checks; safely handle missing tiles and disconnected steam transfers.
* Prevent steam-machine save notifications from reloading adjacent chunks while retaining notifications for loaded neighbors.
* Recover stored items when breaking an unformed flash boiler or a secondary with legacy contents, without duplicating the shared inventory.
* Preserve unused burn time stored in secondary flash-boiler blocks until those contents can be recovered.
* Reject invalid boiler inventory slots, sides and extraction amounts instead of crashing or increasing stored stacks.
* Keep automated water input to one container at a time and allow hoppers to recover empty bottles and refillable containers after draining.
* Recover water, stored items and paid fuel when rebuilding a flash boiler with a different primary block; spill water above its 80,000 mB capacity and drop conflicting or excess items with their tags.
* Start new flash-boiler blocks with empty tanks so rebuilding cannot create water.
* Keep pressure-gauge comparator signals within 0–15 and update loaded comparators without reloading adjacent chunks.
* Check the correct machine face when mounting gauges and rupture discs, and ignore unavailable or invalid supports without loading chunks.
* Preserve the ruptured disc item when its support is removed, and consume repair plates only on the server while clearing depleted stacks.
* Preserve custom names, tagged inventory and full counters when steam heaters replace ordinary furnaces or restore them after losing steam.
* Stop replaced furnaces from processing stale inventory, and apply heater burn time to the installed furnace.
* Keep heater/furnace processing on the server, pause across unavailable neighboring chunks, and save changed furnace counters.
* Remove duplicate heater registration and exclude the heating face and unknown direction from heater steam connections.
