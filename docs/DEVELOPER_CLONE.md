# Cloning and large files

## Do I need Git LFS?

**No — not for this repository as it is today.**

Runtime models live under `assets/models/` and `assets/labels/` as **normal Git files** (each under GitHub’s **100 MB** hard limit). After you clone, those files are already there; nothing extra to download for a standard app build.

| Location | Role |
|----------|------|
| `assets/models/*.nb`, `*.onnx` | Shipped with the app; committed in Git |
| `android/app/src/main/jniLibs/` | Paddle Lite JNI (tracked) |
| `.venv/`, `scripts/**/venv/`, `scripts/arabic_rec_build/` | **Not** in Git — local Python tooling only |

## Quick start (most developers)

```bash
git clone <repo-url>
cd flutter_ocr_poc
flutter pub get
flutter run   # pick Android device/emulator for full Paddle pipeline
```

Use **Android** for the native Paddle/ONNX pipeline described in the project. iOS native OCR wiring may differ; see `SETUP.md`.

For deeper setup (Paddle Lite jars, model conversion, ML Kit), see **`SETUP.md`** at the repo root.

## What never gets cloned (by design)

These paths are **gitignored** — they are large or machine-specific and must be recreated locally if you work on export scripts:

- Python virtualenvs (`scripts/.venv310/`, etc.)
- Full `scripts/arabic_rec_build/` checkouts (regenerate from `scripts/README_arabic_rec.md` if needed)

That does **not** stop the Flutter app from running; it only affects optional training/export workflows.

---

## Optional: Git LFS (when to use it)

Consider **Git LFS** only if you later add binaries that:

- Approach or exceed **~80–100 MB** per file, or  
- You want **smudge filters** / partial checkouts for very large artifacts.

**Trade-offs**

- Contributors must run `git lfs install` once per machine.
- GitHub Free has **LFS storage and bandwidth quotas**; heavy traffic can hit limits.
- Moving **existing** history onto LFS requires `git lfs migrate` (history rewrite) — coordinate with the team.

**If you enable LFS for new commits only** (no migrate):

```bash
git lfs install
git lfs track "*.onnx"
git lfs track "*.nb"
git add .gitattributes
# commit — new/changed matching files go to LFS; old blobs stay as normal Git until migrated
```

**If you migrate existing `assets/models` into LFS** (rewrites history — force-push required):

```bash
git lfs install
git lfs migrate import --include="assets/models/*.onnx,assets/models/*.nb" --everything
# then: git push --force-with-lease origin main   # only after team agrees
```

Only do migrate on a coordinated branch; everyone else must re-clone or reset.

---

## Alternative to LFS: external artifacts

For very large or rarely changing models:

1. Attach a **zip** to a **GitHub Release** and document the URL in `SETUP.md`, or  
2. Host on internal storage (S3, Artifactory) and add a small script: “download into `assets/models/`”.

That keeps the Git repo small without LFS quota concerns.
