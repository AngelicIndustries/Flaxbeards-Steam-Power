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
