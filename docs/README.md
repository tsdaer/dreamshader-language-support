# Documentation Index

This directory is the long-form home for project knowledge. The top-level README should stay as a concise entry point and link here instead of accumulating milestone, baseline, and workflow detail.

## Categories

- [Architecture](architecture.md): runtime layers, data flow, and extension guidelines.
- [Code Map](code-map.md): quick file links for core plugin areas and tests.
- [Project Structure](project-structure.md): source and test directory layout rules.
- [DreamShaderLang Baseline](language-baseline.md): upstream language rules and Rider coverage mapping.
- [Package Baseline](package-baseline.md): package layout, import resolution, store/index behavior, and parity targets.
- [Roadmap and Progress](roadmap.md): project goal, current progress, detailed TODOs, audit matrices, and changelog policy.
- [Development Guide](development.md): build/test/run commands, signing/publishing, Rider actions, settings, and troubleshooting.
- [Version Plans](plans/README.md): version-scoped implementation plans and active release slices.

## Maintenance Rules

- Keep README focused on what the project is, how to get started, and where to find deeper information.
- Put stable language and package behavior in the baseline documents.
- Put version-scoped implementation plans under `plans/`; keep `roadmap.md` for cross-version status and milestone history.
- Put local workflow details in `development.md`, not in README.
- When adding a new feature area, add or update a dedicated docs page and link it from this index.
