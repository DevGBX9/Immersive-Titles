# Agent rules for this workspace

These rules come from the owner of this project and apply to every agent session in this
workspace, on every task, until the owner changes them.

## 1. Never build on the owner's machine

No local builds, no `gradlew build`, no compilation, no daemon, no Gradle task that produces
artifacts on this device — not once, not "just to check", not with a small task.

Verification on this device stays static: reading files, text checks, Python scripts that only
read and analyse files, and inspecting existing artefacts (jars, caches) that are already on
disk.

## 2. Nothing larger than 30 MiB gets downloaded without asking first

Any single download above 30 MiB requires the owner's explicit permission in the conversation
before it starts. This includes toolchains, dependencies, models, assets, datasets and archives.
Anything at or below 30 MiB is fine without asking.

## 3. Building happens on GitHub, and finished work is pushed there

The build runs on GitHub Actions only. When the edits are done, commit them and push to GitHub so
that the workflow there builds the mod, then report what the run did. Do not leave the work
sitting on the device when the owner asked for it to be built.

## Arabic

- **القاعدة الأولى**: لا تبني على جهاز المالك أبدًا، إطلاقًا، في أي مهمة.
- **القاعدة الثانية**: لا تنزّل أي شيء يتجاوز حجمه 30 ميجابايت إلا بعد طلب إذن المالك أولًا.
- **القاعدة الثالثة**: البناء يتم على GitHub فقط، وعند الانتهاء من التعديلات تُرفع إلى GitHub
  ليتم البناء هناك.
