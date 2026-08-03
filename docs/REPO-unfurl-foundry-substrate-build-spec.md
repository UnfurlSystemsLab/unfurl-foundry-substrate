# REPO: unfurl-foundry-substrate Build Spec

`unfurl-foundry-substrate` is a coordinated Maven module set for Foundry substrate contracts and helpers.

## Build

```bash
mvn test
```

From the workspace root:

```bash
mvn -pl unfurl-foundry-substrate -am test
```

## Test Focus

- Agent substrate record round trips.
- Agent harness definition/state round trips and validation.
- Embedded agent harness turn loop behavior: continue, clarification wait, gap, completion, policy exhaustion.
- Tool, prompt, event, and model boundary contracts.
- Compatibility with `unfurl-foundry` runtime consumers.

## GitHub Packages

This repository participates in the `UnfurlSystemsLab` private Maven package chain.

- Publish: GitHub Actions verifies this repository, then dispatches `UnfurlSystemsLab/unfurl` `publish-lab-maven.yml` with `publish_scope=changed`; the root aggregator publishes this repository's Maven artifacts to `https://maven.pkg.github.com/unfurlsystemslab/unfurl` using Maven server id `github`.
- Consume: this repository resolves internal `com.unfurl...` artifacts through `https://maven.pkg.github.com/unfurlsystemslab/*`.
- Credentials: local and CI Maven settings must provide server id `github`; use `CI_REPO_TOKEN` or a PAT with `repo`, `workflow`, `read:packages`, and `write:packages` for central Lab package dispatch/publish and cross-repository private dependency reads.
- Component CI must use `CI_REPO_TOKEN` for internal package reads and root workflow dispatch; it must fail before Maven verify when that token is unavailable rather than falling back to the repository-scoped `GITHUB_TOKEN`.
- Bootstrap order: publish `unfurl-substrate` and `dcp` before publishing `unfurl-foundry-substrate`.

