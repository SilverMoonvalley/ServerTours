const assert = require('node:assert/strict')
const { randomBytes } = require('node:crypto')
const mineflayer = require('mineflayer')

const HOST = process.env.MC_HOST || '127.0.0.1'
const PORT = Number(process.env.MC_PORT || 25566)
const USERNAME = process.env.MC_USERNAME || 'TestBot'
const VERSION = process.env.MC_VERSION || '1.21.5'

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function createRouteName() {
  const timestamp = Date.now().toString(36)
  const nonce = randomBytes(3).toString('hex')
  return `rec_e2e_${timestamp}_${nonce}`
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function createBot() {
  const bot = mineflayer.createBot({
    host: HOST,
    port: PORT,
    username: USERNAME,
    version: VERSION,
    auth: process.env.MC_AUTH || 'offline'
  })

  bot.observedMessages = []
  bot.on('messagestr', (message) => {
    bot.observedMessages.push(String(message))
  })
  bot.on('error', (error) => {
    bot.lastError = error
  })
  bot.on('kicked', (reason) => {
    bot.lastKickReason = reason
  })
  bot.on('end', (reason) => {
    bot.lastEndReason = reason || 'connection ended'
  })
  return bot
}

function failFast(bot) {
  if (bot.lastError) throw bot.lastError
  if (bot.lastKickReason) throw new Error(`Bot kicked: ${bot.lastKickReason}`)
  if (bot.lastEndReason) throw new Error(`Bot disconnected: ${bot.lastEndReason}`)
}

async function waitForReady(bot, maxMs = 30000) {
  const started = Date.now()
  while (Date.now() - started < maxMs) {
    failFast(bot)
    if (bot.entity) return
    await wait(100)
  }
  throw new Error('Timed out waiting for bot spawn')
}

async function waitForCondition(bot, label, predicate, maxMs = 10000) {
  const started = Date.now()
  let lastError
  while (Date.now() - started < maxMs) {
    failFast(bot)
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
  failFast(bot)
}

async function setTickRate(bot, rate) {
  const startIndex = bot.observedMessages.length
  bot.chat(`/tick rate ${rate}`)
  await waitForMessageSince(bot, new RegExp(`tick rate.*${rate}(?:\\.0)?`, 'i'), startIndex, 5000)
  await wait(300)
}

async function sampleServerPosition(bot) {
  const startIndex = bot.observedMessages.length
  bot.chat(`/data get entity ${bot.username} Pos`)
  const message = await waitForMessageSince(
    bot,
    /has the following entity data|\u62e5\u6709\u4ee5\u4e0b\u5b9e\u4f53\u6570\u636e|\u4ee5\u4e0b\u5b9e\u4f53\u6570\u636e/i,
    startIndex,
    5000
  )
  const coordinates = [...message.matchAll(/(-?\d+(?:\.\d+)?)d/g)].map((match) => Number(match[1]))
  if (coordinates.length < 3) {
    throw new Error(`Could not parse authoritative player position from: ${message}`)
  }
  return { x: coordinates[0], y: coordinates[1], z: coordinates[2] }
}

function distance(a, b) {
  const dx = a.x - b.x
  const dy = a.y - b.y
  const dz = a.z - b.z
  return Math.sqrt(dx * dx + dy * dy + dz * dz)
}

function degrees(value) {
  return value * Math.PI / 180
}

async function recordFlight(bot) {
  // Mineflayer does not apply spectator controls to its normal physics model.
  // creative.flyTo advances the client position in small 50ms steps, which are
  // sent as ordinary movement packets and accepted by the server in spectator.
  bot.creative.startFlying()
  try {
    await bot.look(degrees(155), degrees(-8), true)
    await bot.creative.flyTo(bot.entity.position.offset(3.0, 1.0, -2.0))
    await wait(450)

    // Cross the wrapped yaw boundary in the short direction. The recording
    // should retain this as a small continuous turn instead of a 358 degree spin.
    await bot.look(degrees(179), degrees(-12), true)
    await wait(350)
    await bot.look(degrees(-179), degrees(-10), true)
    await bot.creative.flyTo(bot.entity.position.offset(2.0, 0.5, 2.0))
    await wait(400)

    await wait(750)

    await bot.look(degrees(-145), degrees(7), true)
    await bot.creative.flyTo(bot.entity.position.offset(4.0, 1.5, 3.0))
    await wait(500)
  } finally {
    bot.creative.stopFlying()
  }
}

function parseSavedRecording(message, routeName) {
  assert.match(message, new RegExp(`(?:Created route\\s+|已创建路线\\s+)${escapeRegExp(routeName)}\\b`, 'i'))
  const counts = message.match(/from\s+(\d+)\s+raw samples\s*\/\s*(\d+)\s+keyframes/i) ||
    message.match(/(\d+)\s*个原始采样[，,]\s*(\d+)\s*个关键帧/)
  const duration = message.match(/\(([0-9]+(?:\.[0-9]+)?)s\)/i) ||
    message.match(/（([0-9]+(?:\.[0-9]+)?)\s*秒）/)
  if (!counts || !duration) {
    throw new Error(`Could not parse completed recording summary: ${message}`)
  }
  return {
    rawSamples: Number(counts[1]),
    keyframes: Number(counts[2]),
    durationSeconds: Number(duration[1])
  }
}

async function assertRestored(bot, expected, label, maxMs = 10000) {
  await waitForCondition(bot, `${label} game mode restoration`, () => {
    assert.equal(bot.game?.gameMode, expected.gameMode)
    return true
  }, maxMs)
  const position = await sampleServerPosition(bot)
  const restoredDistance = distance(position, expected.position)
  assert.ok(
    restoredDistance <= 1.25,
    `${label} should restore the player position (distance ${restoredDistance.toFixed(3)})`
  )
  assert.ok(bot.entity, `${label} should leave the bot connected`)
  return { position, restoredDistance }
}

async function bestEffortCleanup(bot, routeName) {
  if (!bot.entity || bot.lastEndReason || bot.lastKickReason) return
  bot.clearControlStates()
  const commands = [
    '/tour record cancel',
    `/tour stop ${bot.username}`,
    `/tour remove ${routeName}`,
    `/tour record discard ${routeName}`
  ]
  for (const command of commands) {
    try {
      await sendCommand(bot, command, 250)
    } catch (_) {
      return
    }
  }
}

async function main() {
  const routeName = createRouteName()
  const bot = createBot()
  let savedSummary

  try {
    await waitForReady(bot)
    await wait(750)

    // The packaged test server grants this offline bot operator permissions.
    // A deterministic non-playback mode makes both recording and playback
    // lifecycle transitions observable through normal client state packets.
    await setTickRate(bot, 20)
    await sendCommand(bot, '/gamemode survival @s', 300)
    await waitForCondition(bot, 'operator gamemode command', () => bot.game?.gameMode === 'survival', 5000)
    const before = {
      gameMode: bot.game.gameMode,
      position: await sampleServerPosition(bot)
    }

    const startMessageIndex = bot.observedMessages.length
    bot.chat(`/tour record start ${routeName}`)
    await waitForMessageSince(
      bot,
      new RegExp(`(?:Camera recording started for\\s+|已开始录制镜头\\s+)${escapeRegExp(routeName)}\\b`, 'i'),
      startMessageIndex,
      7000
    )
    await waitForCondition(bot, 'spectator recording mode', () => bot.game?.gameMode === 'spectator', 5000)

    await recordFlight(bot)
    const recordedEndPosition = await sampleServerPosition(bot)
    const recordedDisplacement = distance(recordedEndPosition, before.position)
    assert.ok(
      recordedDisplacement >= 0.75,
      `spectator controls should produce a real recorded path (displacement ${recordedDisplacement.toFixed(3)})`
    )

    const stopMessageIndex = bot.observedMessages.length
    bot.chat('/tour record stop')
    const processingMessage = await waitForMessageSince(
      bot,
      new RegExp(`(?:Recording\\s+${escapeRegExp(routeName)}\\s+is saved as a draft|` +
        `录制\\s+${escapeRegExp(routeName)}\\s+已保存为草稿)`, 'i'),
      stopMessageIndex,
      7000
    )
    const afterRecording = await assertRestored(bot, before, 'recording stop', 8000)

    const savedMessage = await waitForMessageSince(
      bot,
      new RegExp(`(?:Created route\\s+${escapeRegExp(routeName)}\\b.*raw samples|` +
        `已创建路线\\s+${escapeRegExp(routeName)}\\b.*原始采样)`, 'i'),
      stopMessageIndex,
      30000
    )
    savedSummary = parseSavedRecording(savedMessage, routeName)
    assert.ok(savedSummary.rawSamples >= 20, 'a multi-second recording should contain many real 20 Hz samples')
    assert.ok(savedSummary.keyframes >= 2, 'a moving recording should compile to at least two keyframes')
    assert.ok(savedSummary.keyframes <= savedSummary.rawSamples, 'compiled keyframes cannot exceed raw samples')
    assert.ok(
      savedSummary.durationSeconds >= 3 && savedSummary.durationSeconds <= 5,
      `recording duration should be approximately 3-5 seconds, got ${savedSummary.durationSeconds}s`
    )

    const playMessageIndex = bot.observedMessages.length
    const playCommandAt = Date.now()
    bot.chat(`/tour play ${routeName}`)
    await waitForCondition(bot, 'recorded route playback start', () => {
      assert.equal(
        bot.observedMessages.slice(playMessageIndex).some((message) => /route not found|no valid recorded camera/i.test(message)),
        false,
        'recorded route should be available for playback'
      )
      return bot.game?.gameMode !== before.gameMode
    }, 7000)
    const playbackStartedAt = Date.now()

    await waitForCondition(bot, 'recorded route natural completion', () => {
      return bot.game?.gameMode === before.gameMode
    }, Math.ceil(savedSummary.durationSeconds * 1000) + 7000)
    const playbackFinishedAt = Date.now()
    const afterPlayback = await assertRestored(bot, before, 'recorded route playback', 5000)

    const expectedPlaybackMs = savedSummary.durationSeconds * 1000
    const actualPlaybackMs = playbackFinishedAt - playbackStartedAt
    const playbackErrorMs = Math.abs(actualPlaybackMs - expectedPlaybackMs)
    const allowedPlaybackErrorMs = Math.max(350, expectedPlaybackMs * 0.15)
    assert.ok(
      playbackErrorMs <= allowedPlaybackErrorMs,
      `playback duration ${actualPlaybackMs}ms should stay near recorded duration ` +
        `${expectedPlaybackMs}ms (error ${playbackErrorMs}ms)`
    )
    assert.ok(bot.entity, 'bot should remain connected after natural playback completion')

    console.log(JSON.stringify({
      status: 'passed',
      scenario: 'ServerTours timestamped spectator recording and recorded-camera playback',
      server: `${HOST}:${PORT}`,
      routeName,
      recording: {
        ...savedSummary,
        displacement: recordedDisplacement,
        processingMessage
      },
      restoration: {
        gameMode: before.gameMode,
        afterRecordingDistance: afterRecording.restoredDistance,
        afterPlaybackDistance: afterPlayback.restoredDistance
      },
      playback: {
        commandToStartMs: playbackStartedAt - playCommandAt,
        expectedMs: expectedPlaybackMs,
        actualMs: actualPlaybackMs,
        errorMs: playbackErrorMs,
        allowedErrorMs: allowedPlaybackErrorMs
      }
    }, null, 2))
  } finally {
    await bestEffortCleanup(bot, routeName)
    if (bot.entity && !bot.lastEndReason) bot.quit()
  }
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
