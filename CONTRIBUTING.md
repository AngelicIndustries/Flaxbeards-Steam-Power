# Contributing

Report bugs in this fork's GitHub issues. Include the Minecraft, Forge and mod versions, steps to reproduce, relevant configuration, and the complete crash report or log.

Keep pull requests focused on one change. Preserve existing mod IDs, registry names and public packages so saves and integrations remain compatible. Match the surrounding source style; broad formatting changes and Spotless are not part of this fork's workflow.

Run `./gradlew build` before submitting changes. Changes to gameplay or optional integrations also need relevant client and dedicated-server checks. Optional integrations must continue to load safely when the other mod is absent.

Use a short imperative commit subject, such as `Fix crucible alloy output`. Add user-visible changes to `.changelogs/unreleased.md`.

Build tooling is pinned and updated manually. Local investigation notes belong in the ignored `docs/` directory. Keep credentials in environment variables or the ignored `.env` file.
