# Reproducible release packaging

F-Droid verifies the developer-signed APK by rebuilding the same tagged source
and copying the published signature onto its unsigned output. Matching extracted
file contents alone is insufficient: ZIP packaging must also match.

## v2.2 failure and release policy

The inherited F-Droid recipe uses `reproducible-apk-tools@v0.3.0`:

```sh
python3 zipalign.py --page-size 4 --pad-like-apksigner --replace unsigned.apk aligned.apk
```

For v2.2, this replaced three zero-padding bytes on the `resources.arsc` local
ZIP header with seven bytes. Application contents were identical, but signature
copying failed: `APK Signing Block offset < central directory offset`.

For v2.3, `:app:assembleRelease` canonicalizes the unsigned APK's resources.arsc
alignment in `gradle/release-apk-alignment.gradle`. No application payload is
changed or recompressed. The external F-Droid step is therefore idempotent,
and signature reproduction is required to pass both with and without it.
Unfamiliar uncompressed entries or extra fields fail the build for review.

Publish by signing this normalized unsigned output with `apksigner`. Do not use
Gradle's directly signed output: the alignment hook intentionally never edits
an already signed APK. This supports either F-Droid recipe, independently of
when the separate v2.2 metadata correction is merged.

## Verification procedure

1. Commit all source, version metadata, and changelog changes. Build from two
   fresh standalone clones at that exact commit using JDK 17, the pinned Gradle,
   AGP and Kotlin versions, and Android SDK/build-tools selected by the project.
   Do not use linked worktrees for the final provenance check.
2. Leave signing environment variables unset and run `./gradlew clean
   :app:assembleRelease --no-daemon --max-workers=2` in each checkout. Confirm
   both embedded `META-INF/version-control-info.textproto` revisions equal the
   commit to be tagged.
3. Obtain `zipalign.py` from `obfusk/reproducible-apk-tools` tag `v0.3.0`.
   Expected SHA-256:
   `a47596d10395e099176c7b6624f1392c7f579e888310b0552ae25fe86c2565c9`.
   Normalize each independent unsigned APK with the command above. Require
   identical bytes before and after this external normalization.
4. Sign the first normalized APK with Android build-tools `apksigner` and the
   existing protected release key. Never commit or log signing credentials.
5. Run `apksigcopier copy SIGNED_APK SECOND_ALIGNED_APK REPRODUCED_APK`.
   Require byte-for-byte equality between SIGNED_APK and REPRODUCED_APK;
   verify the reproduced signature with `apksigner verify --print-certs`.
6. Also test signature copying from the second unsigned APK without any
   external normalization. Require byte-identical signed output and a valid
   signature for both paths, so removing the inherited recipe cannot break it.
7. Verify application ID, version name/code, one allowed signing certificate,
   embedded revision, and disabled dependency metadata. After publication,
   download the public APK and repeat checksum, signature and provenance checks.

A local recipe reproduction does not claim that F-Droid has accepted, built, or
published the release. Track that independently in upstream metadata and index.
