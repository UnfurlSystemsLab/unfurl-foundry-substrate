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
- Tool, prompt, event, and model boundary contracts.
- Compatibility with `unfurl-foundry` runtime consumers.

