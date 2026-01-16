# Release checklist

This document describes the release process for publishing new versions to Maven
Central.

## Prerequisites

Ensure the following GitHub secrets are configured:

- `GPG_KEY` - ASCII-armoured GPG private key for signing artifacts
- `GPG_PASSPHRASE` - Passphrase for the GPG key
- `OSSRH_USERNAME` - Sonatype OSSRH username
- `OSSRH_PASSWORD` - Sonatype OSSRH password or token

The `maven-central` GitHub environment must be configured with appropriate
approval gates.

## Pre-release checklist

- [ ] All tests pass on the main branch
- [ ] Version in `pom.xml` is currently a SNAPSHOT version (e.g., `1.0.4-SNAPSHOT`)
- [ ] All changes for this release have been merged to main
- [ ] CHANGELOG or release notes are prepared (if applicable)

## Release steps

### 1. Update version to release version

Update `pom.xml` to remove the `-SNAPSHOT` suffix:

```xml
<version>1.0.4</version>
```

Commit the change:

```bash
git commit -am "Update version to 1.0.4"
git push origin main
```

### 2. Create and push the release tag

Create a tag matching the version with a `v` prefix:

```bash
git tag v1.0.4
git push origin v1.0.4
```

This triggers the `draft-release.yml` workflow, which:

- Verifies the version is not a SNAPSHOT
- Creates a draft GitHub release

### 3. Review and publish the GitHub release

1. Navigate to the [Releases page](../../releases) on GitHub
2. Find the draft release created by the workflow
3. Review the release details and add release notes if desired
4. Click **Publish release**

Publishing triggers the `release.yml` workflow, which:

- Signs artifacts with GPG
- Deploys to Maven Central via the `central-publishing-maven-plugin`
- Auto-publishes to Maven Central

### 4. Verify Maven Central deployment

After the workflow completes:

1. Check the [GitHub Actions](../../actions) page to confirm the workflow
   succeeded
2. Verify the artifacts appear on
   [Maven Central](https://central.sonatype.com/artifact/au.csiro/fhir-bulk)
   (may take up to 30 minutes to sync)

## Post-release steps

### Update to next development version

Update `pom.xml` to the next SNAPSHOT version:

```xml
<version>1.0.5-SNAPSHOT</version>
```

Commit and push:

```bash
git commit -am "Update version to 1.0.5-SNAPSHOT"
git push origin main
```

## Troubleshooting

### Draft release workflow fails with "version is a SNAPSHOT"

The `pom.xml` version must not contain `-SNAPSHOT` when the tag is pushed.
Ensure you committed the version update before creating the tag.

### Release workflow fails during deployment

1. Check the workflow logs for specific error messages
2. Verify all secrets are correctly configured
3. Ensure the GPG key has not expired
4. Check Sonatype OSSRH credentials are valid

### Artifacts not appearing on Maven Central

- Maven Central sync can take up to 30 minutes
- Check the [Sonatype Central Portal](https://central.sonatype.com/) for
  deployment status
- Verify the `release.yml` workflow completed successfully

### Need to re-deploy a release

If deployment failed after the tag was created:

1. Fix the underlying issue
2. Delete the tag locally and remotely:
   ```bash
   git tag -d v1.0.4
   git push origin :refs/tags/v1.0.4
   ```
3. Delete the draft/failed GitHub release
4. Re-create and push the tag

## Snapshot deployments

To deploy a SNAPSHOT version for testing:

1. Ensure the `pom.xml` version ends with `-SNAPSHOT`
2. Navigate to [Actions](../../actions) > "Deploy Maven snapshot"
3. Click **Run workflow**
4. Select the branch to deploy from
5. Click **Run workflow**

Snapshots are deployed to the Sonatype snapshots repository and do not require
a GitHub release.
