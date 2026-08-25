const assert = require('node:assert/strict')
const mineflayer = require('mineflayer')
const minecraftData = require('minecraft-data')

const EVENT_ROUTE = process.env.ST_P0_EVENT_ROUTE || 'p0_kernel_events'
const CONFIRM_ROUTE = process.env.ST_P0_CONFIRM_ROUTE || 'p0_kernel_confirm'

const EVENT_TITLES = [0, 1, 2, 3].map((index) => `P0_FRAME_TITLE_${index}`)
// Paper 1.21.5 emits `/plugins` as separate header and plugin-list messages.
const PLUGIN_LIST_MESSAGE = /ProtocolLib,\s*ServerTours/i
const QUIT_MARKER_MESSAGE = /Seed:/i
const VERSION = process.env.MC_VERSION || '1.21.5'
const TEXT_DISPLAY_TYPE_ID = minecraftData(VERSION)?.entitiesByName?.text_display?.id

assert.ok(TEXT_DISPLAY_TYPE_ID != null, `${VERSION} does not expose the Java text_display entity`)

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function waitForEnd(bot, maxMs = 10000) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('Timed out waiting for bot disconnect')), maxMs)
    bot.once('end', (reason) => {
      clearTimeout(timeout)
      resolve(reason)
    })
  })
}

function createBot() {
  const bot = mineflayer.createBot({
    host: process.env.MC_HOST || '127.0.0.1',
    port: Number(process.env.MC_PORT || 25566),
    username: process.env.MC_USERNAME || 'TestBot',
    version: VERSION,
    auth: process.env.MC_AUTH || 'offline'
  })

  bot.observedMessages = []
  bot.observedTitles = []
  bot.knownTextDisplays = new Set()
  bot.activeProtocolCameraId = null
  bot.protocolDisplayTargets = []
  bot.protocolCameraRestores = []
  bot.protocolDisplayDestroys = []
  bot.on('messagestr', (message) => {
    bot.observedMessages.push(String(message))
  })
  bot.on('title', (title, type) => {
    bot.observedTitles.push({
      text: typeof title === 'string' ? title : JSON.stringify(title),
      type,
      at: Date.now()
    })
  })
  bot._client.on('spawn_entity', (packet) => {
    if (packet.type === TEXT_DISPLAY_TYPE_ID) {
      bot.knownTextDisplays.add(packet.entityId)
    }
  })
  bot._client.on('camera', (packet) => {
    const event = { entityId: packet.cameraId, at: Date.now() }
    bot.activeProtocolCameraId = packet.cameraId
    if (bot.knownTextDisplays.has(packet.cameraId)) {
      bot.protocolDisplayTargets.push(event)
    } else if (packet.cameraId === bot.entity?.id) {
      bot.protocolCameraRestores.push(event)
    }
  })
  bot._client.on('entity_destroy', (packet) => {
    for (const entityId of packet.entityIds) {
      if (bot.knownTextDisplays.has(entityId)) {
        bot.protocolDisplayDestroys.push({ entityId, at: Date.now() })
      }
    }
  })
  bot.on('error', (error) => {
    bot.lastError = error
  })
  bot.on('kicked', (reason) => {
    bot.lastKickReason = reason
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
    if (bot.entity) return
    await wait(100)
  }
  throw new Error('Timed out waiting for bot spawn')
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

async function resetRoute(bot, routeName) {
  await sendCommand(bot, '/tour exit', 175)
  await sendCommand(bot, `/tour remove ${routeName}`, 250)
  await sendCommand(bot, `/tour create ${routeName}`, 250)
  await sendCommand(bot, `/tour edit ${routeName}`, 500)
}

async function addPoint(bot, index, point) {
  const x = 0.5 + index * 0.25
  await sendCommand(bot, `/tp @s ${x} 84 0.5 ${index * 10} 0`, 100)
  await sendCommand(bot, '/tour createpoint', 275)

  if (point.type && point.type !== 'STATIONARY') {
    await sendCommand(bot, `/tour pointsetting type ${point.type}`, 175)
  }
  await sendCommand(bot, `/tour pointsetting secondsVisible ${point.seconds}`, 150)
  await sendCommand(bot, `/tour pointsetting label ${point.label}`, 150)
  await sendCommand(bot, `/tour pointsetting title ${point.title}`, 150)
  if (point.description) {
    await sendCommand(bot, `/tour pointsetting description ${point.description}`, 150)
  }
  if (point.confirm) {
    await sendCommand(bot, '/tour pointsetting confirmRequired true', 150)
    await sendCommand(bot, '/tour pointsetting confirmMode CHAT', 150)
  }
  if (point.pluginsCommand) {
    await sendCommand(bot, '/tour pointcommand add plugins', 150)
    await sendCommand(bot, '/tour pointcommand setexecutor 0 PLAYER', 150)
  }
  if (point.quitMarker) {
    const commandIndex = point.pluginsCommand ? 1 : 0
    await sendCommand(bot, '/tour pointcommand add seed', 150)
    await sendCommand(bot, `/tour pointcommand setexecutor ${commandIndex} PLAYER`, 150)
    await sendCommand(bot, `/tour pointcommand settrigger ${commandIndex} QUIT`, 150)
  }
}

async function createEventRoute(bot) {
  await resetRoute(bot, EVENT_ROUTE)
  for (let index = 0; index < EVENT_TITLES.length; index += 1) {
    await addPoint(bot, index, {
      seconds: 0.1,
      label: `p0_event_${index}`,
      title: EVENT_TITLES[index],
      description: index === 0 ? 'P0_EVENT_BEGIN' : undefined,
      pluginsCommand: true,
      quitMarker: index === EVENT_TITLES.length - 1
    })
  }
  await sendCommand(bot, '/tour exit', 500)
}

async function createConfirmationRoute(bot) {
  await resetRoute(bot, CONFIRM_ROUTE)
  await addPoint(bot, 0, {
    seconds: 0.25,
    label: 'p0_confirm_exit',
    title: 'P0_CONFIRM_EXIT_TITLE',
    description: 'P0_CONFIRM_EXIT_POINT',
    confirm: true
  })
  await addPoint(bot, 1, {
    type: 'INTERPOLATE',
    seconds: 0.25,
    label: 'p0_confirm_enter',
    title: 'P0_CONFIRM_ENTER_TITLE',
    description: 'P0_CONFIRM_ENTER_POINT',
    confirm: true
  })
  await addPoint(bot, 2, {
    seconds: 0.25,
    label: 'p0_confirm_done',
    title: 'P0_CONFIRM_DONE_TITLE',
    description: 'P0_CONFIRM_DONE_POINT',
    quitMarker: true
  })
  await sendCommand(bot, '/tour exit', 500)
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
    inventory: normalizedInventory(bot)
  }
}

async function sampleServerPosition(bot) {
  const startIndex = bot.observedMessages.length
  bot.chat(`/data get entity ${bot.username} Pos`)
  const message = await waitForMessageSince(
    bot,
    /has the following entity data|拥有以下实体数据|以下实体数据/i,
    startIndex,
    5000
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
  assert.ok(
    Math.abs(actual.experience.progress - expected.experience.progress) < 0.0001,
    'experience progress should be restored'
  )
}

async function waitForCoreRestored(bot, expected, maxMs = 10000) {
  const actual = await waitForCondition(bot, 'player core state restoration', () => {
    assert.equal(bot.activeProtocolCameraId, bot.entity.id, 'camera should be reset to the player on the client')
    const actual = captureCoreState(bot)
    assertCoreState(actual, expected)
    return actual
  }, maxMs)
  actual.position = await sampleServerPosition(bot)
  const exitDistance = positionDistance(actual.position, expected.position)
  assert.ok(exitDistance <= 1.25, `exit position should be restored (distance ${exitDistance.toFixed(3)})`)
  return actual
}

async function prepareCoreState(bot) {
  await sendCommand(bot, `/tour stop ${bot.username}`, 200)
  await sendCommand(bot, '/gamemode survival @s', 250)
  // Use an isolated area and clear ambient XP so the baseline cannot change
  // between capture and playback because of old test-world experience orbs.
  await sendCommand(bot, '/spreadplayers 0 0 1 16 false @s', 400)
  const isolationPosition = await sampleServerPosition(bot)
  assert.ok(
    Math.abs(isolationPosition.x) <= 24 && Math.abs(isolationPosition.z) <= 24,
    `isolation teleport did not take effect: ${JSON.stringify(isolationPosition)}`
  )
  await sendCommand(bot, '/kill @e[type=minecraft:experience_orb,distance=..64]', 200)
  await sendCommand(bot, '/effect give @s minecraft:instant_health 1 10 true', 200)
  await sendCommand(bot, '/clear @s', 200)
  await sendCommand(bot, '/give @s minecraft:diamond 3', 200)
  await sendCommand(bot, '/item replace entity @s weapon.offhand with minecraft:torch 7', 200)
  await sendCommand(bot, '/item replace entity @s armor.head with minecraft:iron_helmet', 200)
  await sendCommand(bot, '/experience set @s 9 levels', 200)
  await sendCommand(bot, '/experience set @s 3 points', 200)
  await sendCommand(bot, '/tp @s ~ ~ ~ 17 -4', 200)
  await waitForCondition(bot, 'prepared inventory synchronization', () => {
    const items = normalizedInventory(bot)
    assert.ok(items.some((item) => item.name === 'diamond' && item.count === 3), 'diamonds should be prepared')
    assert.ok(items.some((item) => item.name === 'torch' && item.count === 7), 'offhand torches should be prepared')
    assert.ok(items.some((item) => item.name === 'iron_helmet'), 'helmet should be prepared')
    assert.equal(items.some((item) => item.name === 'paper'), false, 'edit-mode tools should be cleared')
    return true
  }, 5000)
  bot.setQuickBarSlot(4)
  await wait(400)
  await failFast(bot)
  return captureCoreStateWithPosition(bot)
}

async function setTickRate(bot, rate) {
  const startIndex = bot.observedMessages.length
  bot.chat(`/tick rate ${rate}`)
  await waitForMessageSince(bot, new RegExp(`tick rate.*${rate}(?:\\.0)?`, 'i'), startIndex, 5000)
  await wait(rate === 1 ? 1200 : 300)
}

async function testNormalDuration(bot, expectedState) {
  await setTickRate(bot, 20)
  const messageStart = bot.observedMessages.length
  const titleStart = bot.observedTitles.length
  const targetStart = bot.protocolDisplayTargets.length
  const restoreStart = bot.protocolCameraRestores.length
  bot.chat(`/tour play ${EVENT_ROUTE}`)
  const target = await waitForCondition(bot, 'normal-rate TextDisplay camera target', () => {
    return bot.protocolDisplayTargets.slice(targetStart)[0]
  }, 5000)
  const restore = await waitForCondition(bot, 'normal-rate camera restoration', () => {
    return bot.protocolCameraRestores.slice(restoreStart).find((entry) => entry.at >= target.at)
  }, 5000)
  const firstFrame = await waitForCondition(bot, 'normal-rate first-frame title', () => {
    return bot.observedTitles.slice(titleStart)
      .find((entry) => entry.type === 'title' && entry.text.includes(EVENT_TITLES[0]))
  }, 5000)
  await waitForCondition(bot, 'normal-rate TextDisplay destruction', () => {
    // A scheduler skip may hard-rebase the camera before natural completion,
    // so the first target can be destroyed before the final player-camera
    // restore. The dedicated display test validates the final restore-before-
    // destroy ordering; here we only need this session's target to be cleaned.
    return bot.protocolDisplayDestroys.find((entry) => entry.entityId === target.entityId && entry.at >= target.at)
  }, 5000)
  await waitForCoreRestored(bot, expectedState, 5000)
  assert.equal(countMessages(bot, messageStart, QUIT_MARKER_MESSAGE), 1, 'natural finish should execute QUIT once')

  const expectedMs = EVENT_TITLES.length * 100
  // Camera creation happens during the startup transaction, before the scene
  // clock is anchored. Frame-zero dispatch is the observable playback start.
  const actualMs = restore.at - firstFrame.at
  const errorMs = Math.abs(actualMs - expectedMs)
  assert.ok(
    errorMs <= 50,
    `normal playback duration error ${errorMs}ms must not exceed one 20 TPS scheduling period`
  )
  return { expectedMs, actualMs, errorMs, schedulerPeriodMs: 50 }
}

async function testLowTpsCatchUp(bot, expectedState) {
  await setTickRate(bot, 1)
  const messageStart = bot.observedMessages.length
  const titleStart = bot.observedTitles.length
  bot.chat(`/tour play ${EVENT_ROUTE}`)

  await waitForCondition(bot, 'all low-TPS crossed command events', () => {
    return countMessages(bot, messageStart, PLUGIN_LIST_MESSAGE) === EVENT_TITLES.length
  }, 12000)
  await waitForCondition(bot, 'all low-TPS crossed titles', () => {
    return EVENT_TITLES.every((title) => countTitles(bot, titleStart, title) === 1)
  }, 12000).catch((error) => {
    throw new Error(`${error.message} Seen titles: ${JSON.stringify(titlesSince(bot, titleStart))}`)
  })
  await waitForCoreRestored(bot, expectedState, 12000)

  // Wait through another 1 TPS scheduler interval to catch accidental replay.
  await wait(1250)
  assert.equal(
    countMessages(bot, messageStart, PLUGIN_LIST_MESSAGE),
    EVENT_TITLES.length,
    'crossed command events must each execute exactly once'
  )
  for (const title of EVENT_TITLES) {
    assert.equal(countTitles(bot, titleStart, title), 1, `${title} must be sent exactly once`)
  }
  assert.equal(countMessages(bot, messageStart, QUIT_MARKER_MESSAGE), 1, 'low-TPS finish should execute QUIT once')

  await setTickRate(bot, 20)
  return {
    commandMessages: countMessages(bot, messageStart, PLUGIN_LIST_MESSAGE),
    titles: EVENT_TITLES.map((title) => ({ title, count: countTitles(bot, titleStart, title) }))
  }
}

async function testConfirmationBarriers(bot, expectedState) {
  const startIndex = bot.observedMessages.length
  bot.chat(`/tour play ${CONFIRM_ROUTE}`)
  await waitForMessageSince(bot, /P0_CONFIRM_EXIT_POINT/, startIndex, 5000)

  await wait(700)
  assert.equal(
    messagesSince(bot, startIndex).some((message) => message.includes('P0_CONFIRM_ENTER_POINT')),
    false,
    'stationary point must pause before leaving'
  )
  assert.notDeepEqual(normalizedInventory(bot), expectedState.inventory, 'tour should own inventory while paused')

  bot.chat('/tour continue')
  await waitForMessageSince(bot, /P0_CONFIRM_ENTER_POINT/, startIndex, 5000)
  await wait(700)
  assert.equal(
    messagesSince(bot, startIndex).some((message) => message.includes('P0_CONFIRM_DONE_POINT')),
    false,
    'interpolate point must pause immediately upon entering'
  )

  bot.chat('/tour continue')
  await waitForMessageSince(bot, /P0_CONFIRM_DONE_POINT/, startIndex, 5000)
  await waitForCoreRestored(bot, expectedState, 8000)
  assert.equal(countMessages(bot, startIndex, QUIT_MARKER_MESSAGE), 1, 'confirmed finish should execute QUIT once')

  assert.equal(
    messagesSince(bot, startIndex).filter((message) => message.includes('P0_CONFIRM_ENTER_POINT')).length,
    1,
    'same-frame continuation must not duplicate the entered point'
  )
}

async function testCommandStopRestores(bot, expectedState) {
  const startIndex = bot.observedMessages.length
  bot.chat(`/tour play ${CONFIRM_ROUTE}`)
  await waitForMessageSince(bot, /P0_CONFIRM_EXIT_POINT/, startIndex, 5000)
  bot.chat(`/tour stop ${bot.username}`)
  await waitForCoreRestored(bot, expectedState, 8000)
  assert.ok(bot.entity, 'bot should remain connected after /tour stop')
}

async function testMovementExitRestores(bot, expectedState) {
  const startIndex = bot.observedMessages.length
  bot.chat(`/tour play ${CONFIRM_ROUTE}`)
  await waitForMessageSince(bot, /P0_CONFIRM_EXIT_POINT/, startIndex, 5000)
  bot.setControlState('sneak', true)
  bot._client.write('entity_action', {
    entityId: bot.entity.id,
    actionId: bot.supportFeature('entityActionUsesStringMapper') ? 'start_sneaking' : 0,
    jumpBoost: 0
  })
  await wait(250)
  bot._client.write('entity_action', {
    entityId: bot.entity.id,
    actionId: bot.supportFeature('entityActionUsesStringMapper') ? 'stop_sneaking' : 1,
    jumpBoost: 0
  })
  bot.setControlState('sneak', false)
  await waitForCoreRestored(bot, expectedState, 8000)
  assert.equal(
    messagesSince(bot, startIndex).some((message) => message.includes('P0_CONFIRM_ENTER_POINT')),
    false,
    'movement exit must not emit later point events'
  )
}

async function testRapidStopThenPlay(bot, expectedState) {
  const oldStart = bot.observedMessages.length
  bot.chat(`/tour play ${CONFIRM_ROUTE}`)
  await waitForMessageSince(bot, /P0_CONFIRM_EXIT_POINT/, oldStart, 5000)

  const newMessageStart = bot.observedMessages.length
  const newTitleStart = bot.observedTitles.length
  bot.chat(`/tour stop ${bot.username}`)
  bot.chat(`/tour play ${EVENT_ROUTE}`)

  await waitForCondition(bot, 'replacement route command events', () => {
    return countMessages(bot, newMessageStart, PLUGIN_LIST_MESSAGE) === EVENT_TITLES.length
  }, 8000)
  await waitForCoreRestored(bot, expectedState, 8000)
  await wait(500)

  assert.equal(
    messagesSince(bot, newMessageStart).some((message) => message.includes('P0_CONFIRM_ENTER_POINT')),
    false,
    'stopped session must not emit later point events into replacement playback'
  )
  assert.equal(
    countMessages(bot, newMessageStart, PLUGIN_LIST_MESSAGE),
    EVENT_TITLES.length,
    'replacement playback command events must not be duplicated or lost'
  )
  for (const title of EVENT_TITLES) {
    assert.equal(countTitles(bot, newTitleStart, title), 1, `replacement ${title} must be isolated`)
  }
}

async function testDisconnectRejoinAndImmediatePlay(bot, expectedState) {
  await setTickRate(bot, 5)
  const oldStart = bot.observedMessages.length
  bot.chat(`/tour play ${CONFIRM_ROUTE}`)
  await waitForMessageSince(bot, /P0_CONFIRM_EXIT_POINT/, oldStart, 5000)
  await waitForCondition(bot, 'TextDisplay camera target before disconnect', () => {
    return bot.knownTextDisplays.has(bot.activeProtocolCameraId)
  }, 5000)

  const ended = waitForEnd(bot)
  bot.quit()
  await ended

  const replacement = createBot()
  try {
    await waitForReady(replacement)
    const messageStart = replacement.observedMessages.length
    const titleStart = replacement.observedTitles.length
    // At 5 TPS the old visibility lease is still pending for five ticks;
    // starting immediately exercises UUID-equal/entity-id-different replacement.
    replacement.chat(`/tour play ${EVENT_ROUTE}`)
    await waitForCondition(replacement, 'rejoin replacement command events', () => {
      return countMessages(replacement, messageStart, PLUGIN_LIST_MESSAGE) === EVENT_TITLES.length
    }, 8000)
    await waitForCondition(replacement, 'rejoin replacement titles', () => {
      return EVENT_TITLES.every((title) => countTitles(replacement, titleStart, title) === 1)
    }, 8000)
    await setTickRate(replacement, 20)
    await waitForCoreRestored(replacement, expectedState, 8000)
    assert.equal(countMessages(replacement, messageStart, QUIT_MARKER_MESSAGE), 1, 'rejoin playback QUIT should run once')
    return replacement
  } catch (error) {
    await bestEffortCleanup(replacement)
    replacement.quit()
    throw error
  }
}

async function bestEffortCleanup(bot) {
  if (!bot.entity) return
  try {
    bot.chat('/tick rate 20')
    // The command may need one final slow tick when a failure occurred at 1 TPS.
    await wait(1600)
    bot.chat(`/tour stop ${bot.username}`)
    await wait(300)
    bot.chat('/tour exit')
    await wait(200)
    bot.chat(`/tour remove ${EVENT_ROUTE}`)
    await wait(250)
    bot.chat(`/tour remove ${CONFIRM_ROUTE}`)
    await wait(250)
  } catch (error) {
    console.error(`Cleanup warning: ${error.message}`)
  }
}

async function main() {
  let bot = createBot()
  const report = {
    status: 'running',
    scenario: 'P0 absolute-time multi-track playback kernel',
    routes: { event: EVENT_ROUTE, confirmation: CONFIRM_ROUTE }
  }

  try {
    await waitForReady(bot)
    await wait(750)
    await setTickRate(bot, 20)
    await createEventRoute(bot)
    await createConfirmationRoute(bot)
    const expectedState = await prepareCoreState(bot)

    report.expectedState = expectedState
    report.normalDuration = await testNormalDuration(bot, expectedState)
    report.lowTpsCatchUp = await testLowTpsCatchUp(bot, await captureCoreStateWithPosition(bot))
    await testConfirmationBarriers(bot, await captureCoreStateWithPosition(bot))
    report.confirmationBarriers = 'passed'
    await testCommandStopRestores(bot, await captureCoreStateWithPosition(bot))
    report.commandStopRestore = 'passed'
    await testMovementExitRestores(bot, await captureCoreStateWithPosition(bot))
    report.movementExitRestore = 'passed'
    await testRapidStopThenPlay(bot, await captureCoreStateWithPosition(bot))
    report.rapidStopThenPlay = 'passed'
    const disconnectBaseline = await captureCoreStateWithPosition(bot)
    bot = await testDisconnectRejoinAndImmediatePlay(bot, disconnectBaseline)
    report.disconnectRejoinRestore = 'passed'
    report.status = 'passed'

    assert.ok(bot.entity, 'bot should remain connected after all playback lifecycle scenarios')
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
