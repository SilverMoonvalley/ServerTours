const assert = require('node:assert/strict')
const mineflayer = require('mineflayer')
const minecraftData = require('minecraft-data')

const HOST = process.env.MC_HOST || '127.0.0.1'
const PORT = Number(process.env.MC_PORT || 25566)
// The regression creates routes, changes tick rate and teleports itself, so
// the default matches the operator account used by the bundled Paper fixture.
const USERNAME = process.env.MC_USERNAME || 'TestBot'
const VERSION = process.env.MC_VERSION || '1.21.5'
const AUTH = process.env.MC_AUTH || 'offline'
const ROUTE_NAME = process.env.ST_DISPLAY_ROUTE || 'display_camera_java_regression'
const KEEP_ROUTE = process.env.ST_DISPLAY_KEEP_ROUTE === 'true'
const EXPECT_WASD_EXIT = process.env.ST_DISPLAY_EXPECT_WASD_EXIT === 'true'

const POINT_SECONDS = 0.4
const POINTS = [
  { x: 40, y: 100, z: 40, yaw: -70, pitch: -8, type: 'INTERPOLATE' },
  { x: 48, y: 103, z: 44, yaw: -35, pitch: 5, type: 'INTERPOLATE' },
  { x: 57, y: 101, z: 52, yaw: 5, pitch: -4, type: 'INTERPOLATE' },
  { x: 54, y: 105, z: 62, yaw: 55, pitch: 7, type: 'INTERPOLATE' },
  { x: 44, y: 102, z: 67, yaw: 105, pitch: -5, type: 'INTERPOLATE' },
  { x: 36, y: 100, z: 58, yaw: 165, pitch: 0, type: 'STATIONARY' }
]
const EVENT_TITLES = POINTS.map((_, index) => `DISPLAY_FRAME_EVENT_${index}`)
const PLUGIN_LIST_MESSAGE = /ProtocolLib,\s*ServerTours/i
const QUIT_MARKER_MESSAGE = /Seed:/i

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function validateConfiguration() {
  assert.ok(Number.isInteger(PORT) && PORT > 0 && PORT <= 65535, `Invalid MC_PORT: ${process.env.MC_PORT}`)
  assert.match(
    ROUTE_NAME,
    /^[A-Za-z0-9_-]+$/,
    'ST_DISPLAY_ROUTE may contain only letters, digits, underscores and hyphens'
  )

  const data = minecraftData(VERSION)
  assert.ok(data, `minecraft-data does not support MC_VERSION=${VERSION}`)
  assert.ok(data.entitiesByName?.text_display, `${VERSION} does not expose the Java text_display entity`)
  return data.entitiesByName.text_display.id
}

function recordProtocol(bot, type, details = {}) {
  const event = {
    seq: bot.protocolEvents.length,
    at: Date.now(),
    type,
    ...details
  }
  bot.protocolEvents.push(event)
  return event
}

function createBot(textDisplayTypeId) {
  const bot = mineflayer.createBot({
    host: HOST,
    port: PORT,
    username: USERNAME,
    version: VERSION,
    auth: AUTH
  })

  bot.observedMessages = []
  bot.observedTitles = []
  bot.protocolEvents = []
  bot.knownTextDisplays = new Set()
  bot.activeProtocolCameraId = null
  bot.lastAbilities = null

  bot.on('messagestr', (message) => {
    bot.observedMessages.push(String(message))
  })
  bot.on('title', (title, type) => {
    bot.observedTitles.push({
      at: Date.now(),
      text: typeof title === 'string' ? title : JSON.stringify(title),
      type
    })
  })
  bot.on('error', (error) => {
    bot.lastError = error
  })
  bot.on('kicked', (reason) => {
    bot.lastKickReason = reason
  })

  bot._client.on('spawn_entity', (packet) => {
    if (packet.type !== textDisplayTypeId) return
    bot.knownTextDisplays.add(packet.entityId)
    recordProtocol(bot, 'display_spawn', {
      entityId: packet.entityId,
      x: packet.x,
      y: packet.y,
      z: packet.z,
      yaw: packet.yaw,
      pitch: packet.pitch
    })
  })
  bot._client.on('entity_metadata', (packet) => {
    if (bot.knownTextDisplays.has(packet.entityId)) {
      recordProtocol(bot, 'display_metadata', { entityId: packet.entityId })
    }
  })
  bot._client.on('camera', (packet) => {
    bot.activeProtocolCameraId = packet.cameraId
    recordProtocol(bot, 'camera', { entityId: packet.cameraId })
  })
  bot._client.on('entity_teleport', (packet) => {
    if (!bot.knownTextDisplays.has(packet.entityId)) return
    recordProtocol(bot, 'display_move', {
      entityId: packet.entityId,
      x: packet.x,
      y: packet.y,
      z: packet.z,
      yaw: packet.yaw,
      pitch: packet.pitch
    })
  })
  bot._client.on('entity_destroy', (packet) => {
    for (const entityId of packet.entityIds) {
      if (bot.knownTextDisplays.has(entityId)) {
        recordProtocol(bot, 'display_destroy', { entityId })
      }
    }
  })
  bot._client.on('abilities', (packet) => {
    bot.lastAbilities = {
      flags: packet.flags,
      flyingSpeed: packet.flyingSpeed,
      walkingSpeed: packet.walkingSpeed
    }
  })

  return bot
}

async function failFast(bot) {
  if (bot.lastError) throw bot.lastError
  if (bot.lastKickReason) throw new Error(`Bot kicked: ${bot.lastKickReason}`)
}

async function waitForReady(bot, maxMs = 30000) {
  const started = Date.now()
  while (Date.now() - started < maxMs) {
    await failFast(bot)
    if (bot.entity && bot.lastAbilities) return
    await wait(100)
  }
  throw new Error('Timed out waiting for the Java bot to spawn')
}

async function waitForCondition(bot, label, predicate, maxMs = 10000) {
  const started = Date.now()
  let lastError
  while (Date.now() - started < maxMs) {
    await failFast(bot)
    try {
      const result = predicate()
      if (result) return result
    } catch (error) {
      lastError = error
    }
    await wait(50)
  }
  const detail = lastError ? ` Last check: ${lastError.message}` : ''
  throw new Error(`Timed out waiting for ${label}.${detail}`)
}

async function waitForMessageSince(bot, matcher, startIndex, maxMs = 10000) {
  return waitForCondition(bot, `message ${matcher}`, () => {
    return bot.observedMessages.slice(startIndex).find((message) => matcher.test(message))
  }, maxMs)
}

async function sendCommand(bot, command, delayMs = 225) {
  bot.chat(command)
  await wait(delayMs)
  await failFast(bot)
}

function messagesSince(bot, startIndex) {
  return bot.observedMessages.slice(startIndex)
}

function titlesSince(bot, startIndex) {
  return bot.observedTitles.slice(startIndex)
}

function countMessages(bot, startIndex, matcher) {
  return messagesSince(bot, startIndex).filter((message) => matcher.test(message)).length
}

function countTitles(bot, startIndex, text) {
  return titlesSince(bot, startIndex)
    .filter((title) => title.type === 'title' && title.text.includes(text))
    .length
}

function normalizedInventory(bot) {
  return bot.inventory.slots
    .map((item, slot) => item && ({ slot, name: item.name, count: item.count }))
    .filter(Boolean)
}

function captureCoreState(bot) {
  return {
    gameMode: bot.game?.gameMode,
    quickBarSlot: bot.quickBarSlot,
    experience: {
      level: bot.experience?.level,
      points: bot.experience?.points,
      progress: bot.experience?.progress
    },
    health: bot.health,
    inventory: normalizedInventory(bot),
    abilities: { ...bot.lastAbilities }
  }
}

async function sampleServerPosition(bot) {
  const startIndex = bot.observedMessages.length
  bot.chat(`/data get entity ${bot.username} Pos`)
  const message = await waitForMessageSince(
    bot,
    /has the following entity data|\u62e5\u6709\u4ee5\u4e0b\u5b9e\u4f53\u6570\u636e|\u4ee5\u4e0b\u5b9e\u4f53\u6570\u636e/i,
    startIndex,
    6000
  )
  const coordinates = [...message.matchAll(/(-?\d+(?:\.\d+)?)d/g)].map((match) => Number(match[1]))
  if (coordinates.length < 3) {
    throw new Error(`Could not parse authoritative player position from: ${message}`)
  }
  return { x: coordinates[0], y: coordinates[1], z: coordinates[2] }
}

async function captureCoreStateWithPosition(bot) {
  return { ...captureCoreState(bot), position: await sampleServerPosition(bot) }
}

function positionDistance(a, b) {
  const dx = a.x - b.x
  const dy = a.y - b.y
  const dz = a.z - b.z
  return Math.sqrt(dx * dx + dy * dy + dz * dz)
}

function assertCoreState(actual, expected) {
  assert.equal(actual.gameMode, expected.gameMode, 'game mode should be restored')
  assert.equal(actual.quickBarSlot, expected.quickBarSlot, 'selected hotbar slot should be restored')
  assert.deepEqual(actual.inventory, expected.inventory, 'inventory, armor and offhand should be restored')
  assert.equal(actual.experience.level, expected.experience.level, 'experience level should be restored')
  assert.equal(actual.experience.points, expected.experience.points, 'total experience should be restored')
  assert.equal(actual.health, expected.health, 'health should be restored')
  assert.deepEqual(actual.abilities, expected.abilities, 'flight flags and movement speeds should be restored')
  assert.ok(
    Math.abs(actual.experience.progress - expected.experience.progress) < 0.0001,
    'experience progress should be restored'
  )
}

async function waitForCoreRestored(bot, expected, maxMs = 10000) {
  const actual = await waitForCondition(bot, 'player core state restoration', () => {
    const current = captureCoreState(bot)
    assertCoreState(current, expected)
    return current
  }, maxMs)
  actual.position = await sampleServerPosition(bot)
  const exitDistance = positionDistance(actual.position, expected.position)
  assert.ok(exitDistance <= 1.25, `exit position should be restored (distance ${exitDistance.toFixed(3)})`)
  return { state: actual, exitDistance }
}

async function prepareCoreState(bot) {
  await sendCommand(bot, `/tour stop ${bot.username}`, 200)
  await sendCommand(bot, '/gamemode survival @s', 250)
  // Keep preparation inside the always-loaded spawn region. Picking a new
  // far-away coordinate can synchronously generate chunks and time out the client.
  await sendCommand(bot, '/spreadplayers 0 0 1 16 false @s', 400)
  const isolationPosition = await sampleServerPosition(bot)
  assert.ok(
    Math.abs(isolationPosition.x) <= 24 && Math.abs(isolationPosition.z) <= 24,
    `isolation teleport did not take effect: ${JSON.stringify(isolationPosition)}`
  )
  await sendCommand(bot, '/kill @e[type=minecraft:experience_orb,distance=..64]', 200)
  await sendCommand(bot, '/effect give @s minecraft:instant_health 1 10 true', 200)
  await sendCommand(bot, '/clear @s', 200)
  await sendCommand(bot, '/give @s minecraft:emerald 5', 200)
  await sendCommand(bot, '/item replace entity @s weapon.offhand with minecraft:spyglass', 200)
  await sendCommand(bot, '/item replace entity @s armor.head with minecraft:golden_helmet', 200)
  await sendCommand(bot, '/experience set @s 7 levels', 200)
  await sendCommand(bot, '/experience set @s 4 points', 200)
  await sendCommand(bot, '/tp @s ~ ~ ~ 23 -6', 200)
  await waitForCondition(bot, 'prepared inventory synchronization', () => {
    const items = normalizedInventory(bot)
    assert.ok(items.some((item) => item.name === 'emerald' && item.count === 5), 'emeralds should be prepared')
    assert.ok(items.some((item) => item.name === 'spyglass'), 'offhand spyglass should be prepared')
    assert.ok(items.some((item) => item.name === 'golden_helmet'), 'helmet should be prepared')
    assert.equal(items.some((item) => item.name === 'paper'), false, 'edit-mode tools should be cleared')
    return true
  }, 10000)
  bot.setQuickBarSlot(5)
  await wait(400)
  return captureCoreStateWithPosition(bot)
}

async function resetRoute(bot) {
  await sendCommand(bot, '/tour exit', 200)
  await sendCommand(bot, `/tour remove ${ROUTE_NAME}`, 300)
  await sendCommand(bot, `/tour create ${ROUTE_NAME}`, 300)
  await sendCommand(bot, `/tour edit ${ROUTE_NAME}`, 500)
}

async function addPoint(bot, index, point) {
  await sendCommand(bot, `/tp @s ${point.x} ${point.y} ${point.z} ${point.yaw} ${point.pitch}`, 100)
  await sendCommand(bot, '/tour createpoint', 275)
  if (point.type !== 'STATIONARY') {
    await sendCommand(bot, `/tour pointsetting type ${point.type}`, 175)
  }
  await sendCommand(bot, `/tour pointsetting secondsVisible ${POINT_SECONDS}`, 150)
  await sendCommand(bot, `/tour pointsetting label display_camera_${index}`, 150)
  await sendCommand(bot, `/tour pointsetting title ${EVENT_TITLES[index]}`, 150)
  if (index === 0) {
    await sendCommand(bot, '/tour pointsetting description DISPLAY_CAMERA_ROUTE_BEGIN', 150)
  }
  await sendCommand(bot, '/tour pointcommand add plugins', 150)
  await sendCommand(bot, '/tour pointcommand setexecutor 0 PLAYER', 150)

  if (index === POINTS.length - 1) {
    await sendCommand(bot, '/tour pointcommand add seed', 150)
    await sendCommand(bot, '/tour pointcommand setexecutor 1 PLAYER', 150)
    await sendCommand(bot, '/tour pointcommand settrigger 1 QUIT', 150)
  }
}

async function createRoute(bot) {
  await resetRoute(bot)
  for (let index = 0; index < POINTS.length; index += 1) {
    await addPoint(bot, index, POINTS[index])
  }
  await sendCommand(bot, '/tour exit', 500)
}

async function setTickRate(bot, rate) {
  const startIndex = bot.observedMessages.length
  bot.chat(`/tick rate ${rate}`)
  await waitForMessageSince(bot, new RegExp(`tick rate.*${rate}(?:\\.0)?`, 'i'), startIndex, 6000)
  await wait(rate === 1 ? 1200 : 300)
}

function protocolSince(bot, mark) {
  return bot.protocolEvents.slice(mark)
}

function targetCameraEvents(bot, mark) {
  return protocolSince(bot, mark).filter((event) => {
    return event.type === 'camera' && bot.knownTextDisplays.has(event.entityId)
  })
}

async function waitForDisplayCameraStart(bot, mark, maxMs = 6000) {
  return waitForCondition(bot, 'Java TextDisplay camera target', () => {
    const target = targetCameraEvents(bot, mark)[0]
    if (target) return target
    return false
  }, maxMs).catch((error) => {
    throw new Error(
      `${error.message} Restart Paper with the current ServerTours build ` +
      'and connect with a Java Edition client.'
    )
  })
}

function summarizeDisplaySession(bot, mark) {
  const events = protocolSince(bot, mark)
  const cameraEvents = events.filter((event) => event.type === 'camera')
  const targets = cameraEvents.filter((event) => bot.knownTextDisplays.has(event.entityId))
  if (targets.length === 0 || bot.entity?.id == null) return null

  const lastTarget = targets[targets.length - 1]
  const restore = cameraEvents.find((event) => event.seq > lastTarget.seq && event.entityId === bot.entity.id)
  if (!restore) return null

  const targetIds = [...new Set(targets.map((event) => event.entityId))]
  const destroyedIds = targetIds.filter((entityId) => {
    return events.some((event) => event.type === 'display_destroy' && event.entityId === entityId)
  })
  if (destroyedIds.length !== targetIds.length) return null
  if (bot.activeProtocolCameraId !== bot.entity.id) return null

  return {
    events,
    targets,
    targetIds,
    restore,
    destroyedIds,
    displaySpawns: events.filter((event) => event.type === 'display_spawn'),
    displayMoves: events.filter((event) => event.type === 'display_move'),
    displayDestroys: events.filter((event) => event.type === 'display_destroy')
  }
}

async function waitForDisplayCleanup(bot, mark, maxMs = 12000) {
  return waitForCondition(bot, 'camera reset to player and TextDisplay destruction', () => {
    return summarizeDisplaySession(bot, mark)
  }, maxMs)
}

function assertTargetWasSpawnedAndConfigured(session) {
  for (const entityId of session.targetIds) {
    const spawn = session.displaySpawns.find((event) => event.entityId === entityId)
    const target = session.targets.find((event) => event.entityId === entityId)
    const metadata = session.events.find((event) => event.type === 'display_metadata' && event.entityId === entityId)
    assert.ok(spawn, `camera target ${entityId} should have a TextDisplay spawn packet`)
    assert.ok(metadata, `camera target ${entityId} should have display metadata`)
    assert.ok(spawn.seq < target.seq, `TextDisplay ${entityId} must spawn before becoming the camera target`)
  }
  assert.ok(
    session.restore.seq < session.displayDestroys[session.displayDestroys.length - 1].seq,
    'camera must reset to the player before the final display is destroyed'
  )
}

function analyzeContinuousMovement(session) {
  const candidates = session.targetIds.map((entityId) => {
    const moves = session.displayMoves.filter((event) => event.entityId === entityId)
    const uniquePositions = new Set(moves.map((event) => {
      return `${event.x.toFixed(4)},${event.y.toFixed(4)},${event.z.toFixed(4)}`
    }))
    let pathDistance = 0
    for (let index = 1; index < moves.length; index += 1) {
      pathDistance += positionDistance(moves[index - 1], moves[index])
    }
    return { entityId, moves, uniquePositions: uniquePositions.size, pathDistance }
  })
  candidates.sort((first, second) => second.moves.length - first.moves.length)
  const best = candidates[0]
  assert.ok(best, 'at least one display target should be observed')
  // Absolute scene time intentionally hard-rebases after a skipped server
  // frame, so normal scheduler jitter may split one segment across displays.
  assert.ok(best.moves.length >= 4, `expected at least 4 continuous display moves, saw ${best.moves.length}`)
  assert.ok(best.uniquePositions >= 4, `expected at least 4 distinct display positions, saw ${best.uniquePositions}`)
  assert.ok(best.pathDistance >= 2, `display path should cover at least 2 blocks, saw ${best.pathDistance.toFixed(3)}`)
  return {
    entityId: best.entityId,
    movePackets: best.moves.length,
    uniquePositions: best.uniquePositions,
    pathDistance: Number(best.pathDistance.toFixed(3))
  }
}

function analyzeHardRebases(session) {
  const transitions = []
  let previous
  for (const target of session.targets) {
    if (previous == null) {
      previous = target.entityId
      continue
    }
    if (target.entityId === previous) continue
    const spawn = session.displaySpawns.find((event) => event.entityId === target.entityId)
    const oldDestroy = session.displayDestroys.find((event) => {
      return event.entityId === previous && event.seq > target.seq
    })
    assert.ok(spawn && spawn.seq < target.seq, 'replacement display must spawn before the camera switches')
    assert.ok(oldDestroy, 'old display must be destroyed after the replacement camera switch')
    transitions.push({
      oldEntityId: previous,
      newEntityId: target.entityId,
      spawnSeq: spawn.seq,
      cameraSeq: target.seq,
      oldDestroySeq: oldDestroy.seq
    })
    previous = target.entityId
  }
  assert.ok(transitions.length >= 1, '1 TPS playback should hard-rebase onto at least one replacement display')
  return transitions
}

function assertRouteEventsExactlyOnce(bot, messageStart, titleStart) {
  assert.equal(
    countMessages(bot, messageStart, PLUGIN_LIST_MESSAGE),
    EVENT_TITLES.length,
    'each crossed command event must execute exactly once'
  )
  for (const title of EVENT_TITLES) {
    assert.equal(countTitles(bot, titleStart, title), 1, `${title} must be sent exactly once`)
  }
  assert.equal(countMessages(bot, messageStart, QUIT_MARKER_MESSAGE), 1, 'natural finish should execute QUIT once')
}

async function testNaturalPlayback(bot, expectedState) {
  await setTickRate(bot, 20)
  const mark = bot.protocolEvents.length
  const messageStart = bot.observedMessages.length
  const titleStart = bot.observedTitles.length
  bot.chat(`/tour play ${ROUTE_NAME}`)

  const firstTarget = await waitForDisplayCameraStart(bot, mark)
  const continuous = await waitForCondition(bot, 'continuous display camera movement', () => {
    const partial = {
      targetIds: [...new Set(targetCameraEvents(bot, mark).map((event) => event.entityId))],
      displayMoves: protocolSince(bot, mark).filter((event) => event.type === 'display_move')
    }
    try {
      return analyzeContinuousMovement(partial)
    } catch {
      return false
    }
  }, 5000)
  const session = await waitForDisplayCleanup(bot, mark, 8000)
  assertTargetWasSpawnedAndConfigured(session)
  assertRouteEventsExactlyOnce(bot, messageStart, titleStart)
  const restored = await waitForCoreRestored(bot, expectedState, 6000)
  return {
    firstDisplayEntityId: firstTarget.entityId,
    displayEntities: session.targetIds,
    continuous,
    exitDistance: Number(restored.exitDistance.toFixed(3))
  }
}

async function testLowTpsHardRebase(bot, expectedState) {
  await setTickRate(bot, 1)
  const mark = bot.protocolEvents.length
  const messageStart = bot.observedMessages.length
  const titleStart = bot.observedTitles.length
  bot.chat(`/tour play ${ROUTE_NAME}`)

  await waitForDisplayCameraStart(bot, mark, 7000)
  await waitForCondition(bot, 'all low-TPS command events', () => {
    return countMessages(bot, messageStart, PLUGIN_LIST_MESSAGE) === EVENT_TITLES.length
  }, 15000)
  await waitForCondition(bot, 'all low-TPS title events', () => {
    return EVENT_TITLES.every((title) => countTitles(bot, titleStart, title) === 1)
  }, 15000)
  const session = await waitForDisplayCleanup(bot, mark, 15000)
  const restored = await waitForCoreRestored(bot, expectedState, 10000)

  await wait(1250)
  assertTargetWasSpawnedAndConfigured(session)
  assertRouteEventsExactlyOnce(bot, messageStart, titleStart)
  const transitions = analyzeHardRebases(session)
  await setTickRate(bot, 20)
  return {
    displayEntities: session.targetIds,
    hardRebases: transitions,
    commandEvents: countMessages(bot, messageStart, PLUGIN_LIST_MESSAGE),
    titleEvents: EVENT_TITLES.length,
    exitDistance: Number(restored.exitDistance.toFixed(3))
  }
}

function sendSneak(bot, sneaking) {
  bot._client.write('entity_action', {
    entityId: bot.entity.id,
    actionId: bot.supportFeature('entityActionUsesStringMapper')
      ? (sneaking ? 'start_sneaking' : 'stop_sneaking')
      : (sneaking ? 0 : 1),
    jumpBoost: 0
  })
}

function sendForwardInput(bot, forward) {
  if (bot.supportFeature('newPlayerInputPacket')) {
    bot._client.write('player_input', { inputs: { forward } })
  } else {
    bot._client.write('steer_vehicle', {
      sideways: 0,
      forward: forward ? 1 : 0,
      jump: 0
    })
  }
}

async function testInputExit(bot, expectedState, inputName, sendInput) {
  const mark = bot.protocolEvents.length
  const messageStart = bot.observedMessages.length
  bot.chat(`/tour play ${ROUTE_NAME}`)
  await waitForMessageSince(bot, /DISPLAY_CAMERA_ROUTE_BEGIN/, messageStart, 5000)
  const target = await waitForDisplayCameraStart(bot, mark)

  sendInput(true)
  await wait(150)
  sendInput(false)

  const session = await waitForDisplayCleanup(bot, mark, 6000)
  assertTargetWasSpawnedAndConfigured(session)
  const restored = await waitForCoreRestored(bot, expectedState, 6000)
  assert.equal(
    countMessages(bot, messageStart, QUIT_MARKER_MESSAGE),
    0,
    `${inputName} should exit before the final point's QUIT command`
  )
  return {
    input: inputName,
    firstDisplayEntityId: target.entityId,
    destroyedDisplayEntities: session.destroyedIds,
    exitDistance: Number(restored.exitDistance.toFixed(3))
  }
}

async function bestEffortCleanup(bot) {
  if (!bot.entity) return
  try {
    bot.chat('/tick rate 20')
    await wait(1600)
    bot.chat(`/tour stop ${bot.username}`)
    await wait(300)
    bot.chat('/tour exit')
    await wait(200)
    if (!KEEP_ROUTE) {
      bot.chat(`/tour remove ${ROUTE_NAME}`)
      await wait(300)
    }
  } catch (error) {
    console.error(`Cleanup warning: ${error.message}`)
  }
}

async function main() {
  const textDisplayTypeId = validateConfiguration()
  const bot = createBot(textDisplayTypeId)
  const report = {
    status: 'running',
    scenario: 'Java Edition Display Camera packet lifecycle',
    connection: { host: HOST, port: PORT, version: VERSION },
    route: ROUTE_NAME,
    textDisplayTypeId,
    assumptions: [
      'The test account is a server operator.',
      'Paper was restarted with the current Display-only ServerTours build.',
      'ProtocolLib and ServerTours are enabled.',
      'editMode.enableHotbarAltCommands is true.'
    ]
  }

  try {
    await waitForReady(bot)
    await wait(750)
    await setTickRate(bot, 20)
    await createRoute(bot)
    const initialState = await prepareCoreState(bot)
    report.expectedState = initialState

    report.naturalPlayback = await testNaturalPlayback(bot, initialState)
    report.lowTpsHardRebase = await testLowTpsHardRebase(bot, await captureCoreStateWithPosition(bot))
    report.shiftExit = await testInputExit(
      bot,
      await captureCoreStateWithPosition(bot),
      'SHIFT',
      (pressed) => sendSneak(bot, pressed)
    )

    if (EXPECT_WASD_EXIT) {
      report.wasdExit = await testInputExit(
        bot,
        await captureCoreStateWithPosition(bot),
        'WASD/forward',
        (pressed) => sendForwardInput(bot, pressed)
      )
    } else {
      report.wasdExit = {
        status: 'skipped',
        reason: 'Set ST_DISPLAY_EXPECT_WASD_EXIT=true only when an external plugin/API enables TouringPlayer.setExitByMoving(true) for each session.'
      }
    }

    assert.ok(bot.entity, 'bot should remain connected after all Java Display Camera scenarios')
    report.status = 'passed'
    console.log(JSON.stringify(report, null, 2))
  } finally {
    await bestEffortCleanup(bot)
    bot.quit()
  }
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
