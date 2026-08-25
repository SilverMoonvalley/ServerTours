# Mineflayer Reusable Integration Testing Guide and Prompts

## Purpose

This document distills the `RookiePostBox` Mineflayer integration testing approach into a reusable guide for other Minecraft plugins.

Note:

- The correct library name is `mineflayer`.
- It is sometimes misspelled as `minefplayer`; this guide refers to the actual `mineflayer` package.

## What This Style of Test Is Good For

Use Mineflayer integration tests when a plugin needs real player-behavior verification that unit tests cannot cover well:

- Command execution and chat feedback
- GUI open/close behavior
- Inventory movement and slot interaction
- Pagination and layout checks
- Item claim or reward flows
- Join/quit/reconnect behavior
- Anti-duplication and race-condition checks
- Localization text, titles, and lore rendering

This style is especially useful for Paper or Spigot plugins with chest GUIs, item movement, and player state transitions.

## Recommended Test Stack

Use two layers together:

1. Java unit tests for service logic, command handlers, repository logic, and edge cases
2. Mineflayer integration tests for end-to-end player behavior on a real test server

Recommended stack:

- Server: Paper
- Plugin under test: built jar placed into `plugins/`
- Test client: `mineflayer`
- Test runner: plain Node.js script is enough
- Assertions: Node `assert/strict`

## Recommended Project Layout

```text
project-root/
  docs/
    testing/
      mineflayer-reusable-integration-testing-guide-and-prompts.md
  test-server/
    paper-<version>/
      plugins/
      server.properties
      start.bat
      start.sh
  tests/
    mineflayer/
      plugin.integration.test.js
  package.json
```

## Minimal package.json

```json
{
  "name": "plugin-integration-tests",
  "private": true,
  "type": "commonjs",
  "scripts": {
    "test:mineflayer": "node tests/mineflayer/plugin.integration.test.js"
  },
  "dependencies": {
    "mineflayer": "^4.31.0"
  }
}
```

## Environment Assumptions

Your test script should assume:

- A Paper server is already running
- The plugin jar is already installed
- The server version matches the Mineflayer client version
- Test accounts can log in with `offline` auth if using local automation
- The test world is disposable

Recommended environment variables:

```text
MC_HOST=127.0.0.1
MC_PORT=25565
MC_VERSION=1.21.4
MC_AUTH=offline
MC_USERNAME=TestBot
```

## Core Design Pattern

The reusable pattern is:

1. Create one or more bots
2. Wait for spawn
3. Prepare clean test state
4. Trigger plugin behavior through commands or GUI clicks
5. Wait for observable signals
6. Assert chat text, inventory counts, open windows, slot layout, lore, or persistence
7. Quit bots cleanly in `finally`

Observable signals usually include:

- Chat messages
- Inventory item count changes
- Window open events
- Window title changes
- Specific slot becoming empty or filled
- State after reconnect

## Reusable Helper Functions

Almost every plugin test suite should build helpers like these:

### Bot lifecycle

- `createBot(username)`
- `waitForSpawn(bot)`
- `quitBot(bot)`

### Polling and safety

- `waitForTicksOrThrow(bot, ticks)`
- `waitForMessage(bot, matcher, maxTicks)`
- `waitForWindowTitle(bot, matcher, maxTicks)`

These helpers should fail fast if the bot is kicked or emits an error.

### Inventory assertions

- `countInventoryItems(bot, itemName)`
- `waitForInventoryCount(bot, itemName, expectedCount)`
- `waitForInventoryCountAtLeast(bot, itemName, minimumCount)`

### GUI helpers

- `openPluginWindow(bot, command)`
- `findWindowSlot(window, predicate)`
- `clickWindow(slot, button, mode)`
- `moveSlotItem(sourceSlot, targetSlot)`
- `waitForWindowSlotEmpty(bot, slot)`

### Text parsing

For GUI-heavy plugins, build utilities to extract:

- Display name
- Lore text
- Window title
- Slot summaries for debugging

This is important because Paper GUI text often lives inside components or NBT-like structures rather than simple strings.

## Generic Test Skeleton

```js
const assert = require('node:assert/strict')
const { once } = require('node:events')
const mineflayer = require('mineflayer')

function createBot(username) {
  const bot = mineflayer.createBot({
    host: process.env.MC_HOST || '127.0.0.1',
    port: Number(process.env.MC_PORT || 25565),
    username,
    version: process.env.MC_VERSION || '1.21.4',
    auth: process.env.MC_AUTH || 'offline'
  })

  bot.observedMessages = []
  bot.on('messagestr', (message) => {
    bot.observedMessages.push(message)
  })
  bot.on('error', (error) => {
    bot.lastError = error
  })
  bot.on('kicked', (reason) => {
    bot.lastKickReason = reason
  })

  return bot
}

async function waitForTicksOrThrow(bot, ticks) {
  for (let i = 0; i < ticks; i += 1) {
    if (bot.lastError) throw bot.lastError
    if (bot.lastKickReason) throw new Error(`Bot kicked: ${bot.lastKickReason}`)
    await new Promise((resolve) => setTimeout(resolve, 50))
  }
}

async function waitForMessage(bot, matcher, maxTicks = 100) {
  const start = bot.observedMessages.length
  for (let i = 0; i < maxTicks; i += 1) {
    const found = bot.observedMessages.slice(start).find((line) => matcher.test(line))
    if (found) return found
    await waitForTicksOrThrow(bot, 1)
  }
  throw new Error(`Timed out waiting for message ${matcher}`)
}

async function main() {
  const bot = createBot(process.env.MC_USERNAME || 'TestBot')

  try {
    await once(bot, 'spawn')
    await waitForTicksOrThrow(bot, 20)

    bot.chat('/yourplugin test-command')
    await waitForMessage(bot, /success/i)

    console.log(JSON.stringify({
      status: 'passed',
      scenario: 'basic command smoke test'
    }, null, 2))
  } finally {
    bot.quit()
  }
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
```

## Test Scenario Catalog

When adapting this approach to another plugin, cover scenarios in this order.

### 1. Smoke test

Verify the plugin is loaded and a simple command responds correctly.

Examples:

- `/plugin reload`
- `/plugin help`
- `/plugin menu`

### 2. Happy path player flow

Verify the main user journey from start to finish.

Examples:

- Open GUI
- Put item into GUI
- Confirm action
- Receive result
- Verify inventory or server feedback

### 3. Multi-player flow

Use 2 or more bots when the plugin involves sender/recipient, admin/player, buyer/seller, challenger/opponent, or party/guild interactions.

Examples:

- One player sends rewards, another claims them
- Admin grants something to multiple online players
- One player triggers an action another player should observe

### 4. GUI layout and localization

Verify that:

- Expected slots are populated
- Reserved separator slots stay empty
- Previous and next buttons appear only when needed
- Display names and lore match configuration
- Chinese or other localized text renders correctly

### 5. Boundary cases

Examples:

- Empty mailbox
- Last item on last page
- Full inbox
- No permission
- Missing attachment
- Expired entry

### 6. Abuse and exploit protection

Examples:

- Rapid repeated clicks
- Double submit
- Disconnect immediately after reward claim
- Reconnect and verify no duplicate reward

### 7. Persistence and reconnect

Verify state survives:

- Server-side save
- Client reconnect
- Delayed notification after login

## Practical Tips for Stable Tests

### Prefer polling over fixed sleeps

Short polling loops are usually more stable than large blind delays. Wait for a message, window title, slot state, or inventory count rather than sleeping for several seconds.

### Always clean inventory first

If your test relies on counting items, clear the relevant bot inventories before each scenario.

### Use unique per-run identifiers

Append a timestamp or suffix to test messages and temporary player names so repeated runs do not collide with stale state.

### Keep each scenario independent

Each scenario should prepare its own state and not depend on execution order beyond shared helper functions.

### Log structured results

At the end of each scenario, print JSON so failures are easier to inspect and CI logs stay machine-readable.

### Summarize window slots on failure

When GUI assertions fail, include a compact slot summary in the error message. This saves a lot of debugging time.

### Reuse one admin bot pattern

For admin-driven plugins, a common pattern is:

- `adminBot` prepares the world and grants fixtures
- `playerBot` performs the user flow

### Match on stable text fragments

Avoid overfitting to color codes or full localized strings if the plugin config may change. Prefer key fragments unless you are explicitly testing localization output.

## Suggested CI Flow

Recommended sequence:

1. Run Java unit tests
2. Build plugin jar
3. Start test Paper server
4. Copy plugin jar into server `plugins/`
5. Wait until the server is ready
6. Run `npm run test:mineflayer`
7. Collect logs and JSON outputs
8. Stop server

If CI is too heavy for full Mineflayer coverage, keep a smaller smoke subset in CI and run the full suite locally or nightly.

## Reusable Prompt Template for AI

Use the following prompt when asking an AI coding agent to add Mineflayer integration tests to another plugin.

```md
Please add a reusable Mineflayer integration test suite for this Minecraft plugin.

Goals:
- Test the plugin through a real Paper server, not only unit tests
- Use Node.js + mineflayer
- Keep the test runner simple: a plain Node script is fine
- Prefer polling helpers over long fixed sleeps
- Use structured JSON output per scenario
- Fail fast on bot error or kick
- Clean up bots in finally blocks

Please do the following:
1. Inspect the plugin's main player flows, commands, GUIs, and state transitions.
2. Create a `tests/mineflayer/` test script and wire it through `package.json`.
3. Add reusable helpers for:
   - createBot
   - waitForMessage
   - waitForWindowTitle
   - inventory count assertions
   - slot lookup and GUI summaries
4. Cover these scenario types where relevant:
   - smoke command test
   - happy path player flow
   - multi-player interaction
   - GUI layout and pagination
   - boundary conditions
   - reconnect or persistence checks
   - rapid-click or duplicate-claim protection
5. Use environment variables for host, port, version, auth, and usernames.
6. Make assertions against observable outcomes:
   - chat feedback
   - inventory changes
   - open windows
   - slot contents
   - lore or display names
   - reconnect state
7. Keep the tests reusable and avoid hardcoding behavior that is not stable.
8. Document how to run the suite locally.

When done, provide:
- the files you changed
- the scenarios covered
- any assumptions about the Paper test server
- the exact commands to run
```

## Reusable Prompt Template for Expanding an Existing Suite

```md
Please extend the existing Mineflayer integration tests for this Minecraft plugin.

Constraints:
- Preserve the current test style and helpers
- Do not rewrite unrelated scenarios
- Reuse helper functions where possible
- Prefer additive changes
- Keep each new scenario isolated and deterministic

Please:
1. Inspect the current Mineflayer test script and summarize its helper patterns.
2. Add new scenarios for the feature or bugfix I describe.
3. Assert the result through chat messages, GUI state, item counts, or reconnect behavior.
4. Add detailed failure messages for GUI-related assertions.
5. Print structured JSON summaries for each new scenario.
6. Document any new env vars or server setup assumptions.

Feature or bugfix to cover:
[describe the plugin feature here]
```

## Reusable Prompt Template for Exploit Regression Tests

```md
Please add Mineflayer regression tests for duplication, race-condition, or reconnect exploits in this Minecraft plugin.

Focus:
- real player timing behavior
- repeated clicks
- repeated command submissions
- disconnect during or immediately after reward delivery
- reconnect verification

Requirements:
- Use one or more Mineflayer bots
- Make the exploit attempt explicit in the scenario name
- Assert the final inventory count exactly
- Assert the claimed item or reward is no longer claimable afterward
- Add enough polling to avoid flaky timing
- Log a structured JSON result with attempted action count and final item count
```

## Adaptation Checklist

Before applying this guide to another plugin, confirm:

- Which commands are stable enough to automate
- Which GUI titles and slots are intended behavior
- Which text should be matched exactly versus partially
- Which item names are safe test fixtures
- Whether the plugin requires operator permissions
- Whether the plugin depends on database state
- Whether online multi-player behavior must be simulated
- Whether reconnect behavior matters

## What to Avoid

Avoid these common mistakes:

- Relying only on unit tests for GUI-heavy plugins
- Hardcoding long sleeps instead of waiting for observable state
- Reusing dirty player inventories between scenarios
- Testing too many behaviors in one mega-scenario
- Asserting exact full strings when only a key fragment is stable
- Ignoring reconnect and duplicate-claim abuse cases
- Forgetting to capture bot kick and error events

## Suggested Output Artifact

For each plugin, aim to produce:

- `tests/mineflayer/<plugin>.integration.test.js`
- `package.json` script entry
- `docs/<plugin>-mineflayer-test-guide.md` or test report

That combination is usually enough to make the test suite discoverable, runnable, and maintainable.
