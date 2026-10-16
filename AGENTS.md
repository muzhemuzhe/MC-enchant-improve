# Better Enchant – Development rules

1. Build a real playable Minecraft mod, not a simulation, mock-up or unused stub. Report what actually ran. If you cannot compile or launch, explicitly say so.
2. Target Minecraft Java 26.1.2 / Fabric initially; use JDK 25 and a current compatible Fabric Loom / Loader / API combination confirmed from official version metadata. Do not copy 1.21.1 or pre-26.1 Yarn/remapping setup into 26.1.2.
3. Use Mojang's official unobfuscated names for 26.1+, avoiding speculative API names. Inspect available dependency source when unsure.
4. Work in small verifiable milestones. Before every structural rewrite, explain the reason in docs/DEVLOG.md. Do not implement enchanting economy or new enchantments until formation/disassembly has been tested.
5. Server owns machine formation, break/restore, and eventual XP deductions; client owns presentation only. No automatic chunk loading. No item duplication.
6. Gameplay rules belong in separate plain Java services with testable invariants where possible. Compatibility is a first-class design constraint: query registries/tags, avoid hard-coded vanilla enchantment lists.
7. Treat outside source repositories as architectural references only. Do not copy other mods' source, models or assets without verifying their licenses. Attribute third-party dependencies as required by their licenses.
8. Keep model textures provisional and sourced from vanilla assets initially. Deliver a visibly assembled 3×3×2 structure, not merely eighteen unmodified cubes with a chat notification.
9. Preserve the original Git author/committer configuration; do not append additional attribution trailers to commit messages.
10. Do not push commits without the user's separate instruction. Prefer recording exact tested commands and results.
