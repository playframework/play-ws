## Releasing

This is released from the `main` branch from `2.2.0` forward. Unless an older version needs patching, then it must be released from the maintenance branch, for instance `2.1.x` branch. If there is no maintenance branch for the release that needs patching, create it from the tag.

## Cutting the release

### Requires contributor access

- Check the [draft release notes](https://github.com/playframework/play-ws/releases) to see if everything is there
- Wait until [main branch build finishes](https://github.com/playframework/play-ws/actions/workflows/publish.yml) after merging the last PR
- Update the [draft release](https://github.com/playframework/play-ws/releases) with the next tag version (eg. `2.2.0`), title and release description
- Check that GitHub Actions release build has executed successfully (GA start a [CI build](https://github.com/playframework/play-ws/actions/workflows/publish.yml) for the new tag and publish artifacts to Sonatype)

### Check Maven Central

- The artifacts will become visible at https://repo1.maven.org/maven2/com/typesafe/play/
